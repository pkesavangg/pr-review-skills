# Notes — "System Design for AI Agents: Building a Multi-Agent PR Reviewer"

**Source:** [youtube.com/watch?v=iqRcGCah0Kw](https://www.youtube.com/watch?v=iqRcGCah0Kw) · freeCodeCamp.org · published 2026-08-14 · 3h 10m
**Presenter:** credited in the intro as "IU Singh" — almost certainly **Ayush Singh** (Antern.co), whose "Genesis kit" harness the second half is built around.
**Notes basis:** auto-generated captions, cleaned and summarised. The ASR mangles proper nouns; where a term is reconstructed it's marked *(ASR)*.

**What it is:** a system-design lecture, not a coding tutorial. Roughly 1h50m of design reasoning, then ~1h of driving a coding agent to build the first two milestones. It deliberately does *not* finish the project — it hands you a baseline and a method.

---

## 0. The thesis

The video opens by rejecting the standard tutorial shape — *take the diff → stuff it in a prompt → ask an LLM what's wrong → maybe bolt on RAG → call it production-ready.* Its claim is that this is a demo, not a system, because it has no mechanism behind it.

The reframe:

> The problem is not "automate PR review." The problem is **selectivity** — which findings the agent handles, and which ones a human's judgment actually gets spent on.

Everything else in the architecture is derived from that one sentence.

**Level-0 principle (stated as the thing to carry away if you carry nothing else):**

> The system optimises for surfacing findings worth a senior's attention and deferring the rest — **not for maximal output. Selectivity, not coverage.**

---

## 1. The design lens — five moves, run once per component

The presenter's reusable template. It's generic; PR review is just the worked example.

| Move | What you do |
|---|---|
| **1. Map the mess** | Delete the AI from the picture. Document what happens *today*, in minute detail — every micro-decision a human makes, including the unconscious ones. Note where a human is genuinely judging vs. doing mechanical work, and where the human process breaks. |
| **2. Name the trigger and the output** | Both must be **precise**. Not "a claim comes in" but "an email to *this* address containing *this* keyword." Not "produce a review" but a **typed object** with a defined shape. |
| **3. Assign each step a component** | Mechanical → deterministic code. Unstructured reading/writing → LLM. Scoring → deterministic ML. Past knowledge → retrieval. Safety/finance/legal → human checkpoint. |
| **4. Decide the autonomy level** | Not a default. Driven by *consequence of error*, *reversibility*, and *system maturity*. |
| **5. Assume everything breaks** | Walk each component and ask "what can go wrong?" from **two directions** — engineering failures and LLM failures — then sort them into a 2×2 of *what you know you don't know* vs. *what you'd only recognise once you saw it*. The component's design then largely writes itself. |

The claim: run this loop five times over five components (ingress, orchestrator, retriever, event spine, gates) and you end up with a fault-tolerant architecture *before* writing any code.

### The autonomy ladder (move 4)

From most to least automated:

1. **Full automation** — routine, reversible, low-stakes, periodic human spot-check.
2. **Human reviews output** — system drafts, human approves before it goes out (reputational stakes).
3. **Human handles exceptions** — system does the easy cases, human sees anomalies and low-confidence cases. ← *what this system uses*
4. **System prepares, human decides** — system gathers all context and scores; human makes the call (typical for lending).
5. **Full human, AI assists.**

Golden rule stated: anything touching **financial, legal, or health** decisions cannot be fully autonomous. And: *start with more human involvement than you think you need, then reduce it as the system earns trust* — the reverse (walking autonomy back after an expensive mistake) is much worse.

The supporting war story: an outbound LinkedIn agent at a previous company autonomously scheduled a dinner with a major client's prospect. The team's failure wasn't the agent — it was an **assumption** ("even if it books something, we'll have 2–3 days to catch it"). It happened in under 9 hours. The lesson framed as: *the system failed because an assumption failed*, so assumptions belong in the design as first-class, registered artefacts.

---

## 2. Failure-mode catalogue

Enumerated up front, before any architecture, each paired with the defence it forces into the design:

| Failure mode | Defence it forces |
|---|---|
| **Hallucination** — confidently wrong where it matters | Citation requirement, grounding via retrieval, a confidence score, human gate below threshold |
| **Model / prompt drift** — good on your eval set, degrades in the wild | Monitoring dashboard, alert thresholds, periodic prompt & model refresh, rule fallbacks |
| **Tool / API timeout** | Timeouts + retry with backoff, graceful degradation on partial data, circuit breaker on a dead dependency |
| **Feedback-loop poisoning** — one junior's bad review teaches the agent a wrong preference | **Minimum evidence threshold** before acting on feedback; decay stale feedback |
| **Orchestration deadlock** — the aggregator waits forever on a hung specialist | Per-node timeouts; the merge step must tolerate a missing input |
| **Human bottleneck** — the escalation queue grows faster than humans drain it | Escalation-rate monitoring, capacity planning, queue prioritisation by business value |
| **The "almost right" problem** — 90% correct, 10% subtly wrong | Flag low confidence, random audits, rotate models, adversarial/novel inputs |
| **Duplicate delivery** — the webhook retries and you post the review twice | Idempotency key, dedup before posting |

Note the sequencing: *the problems are enumerated first, and words like "observability," "reliability," and "retrieval" only appear afterwards as the answers.* The video is explicit that this is the point — you should feel the need for observability before anyone says the word.

---

## 3. Part 1 — First principles

### Why the system exists at all

Remove the automated reviewer and you get: every PR waits on scarce senior attention; PRs queue for hours or days; different reviewers flag different things; and **the 10th review of the day is not the 1st** — fatigue makes the same human inconsistent.

So the purpose statement is **not** "review PRs automatically." It's:

> **Reclaim senior-engineer attention by automating the mechanical part of the review, so human judgment is spent only where it's genuinely required.**

### How a senior actually reviews — four native behaviours

Observed from the human process, then translated to engineering:

| Human behaviour | Engineering consequence |
|---|---|
| Brings **codebase context** — recalls what contradicts past decisions, ADRs, architectural style | The agent needs **retrieval**, not the whole repo in a prompt |
| Reasons across **separate concerns**, each from a different mindset | **Multi-agent**, not one prompt wearing four hats |
| Stays **skeptical** — doesn't assume the diff is correct | Every finding carries a **rationale** |
| **Cites evidence** — "line 40 can be null here," not "this looks off" | Every finding carries **location + confidence**; "looks off" isn't actionable or disputable |

The four specialists fall out of the four mindsets, not out of a decision to have four agents:

- **Security** — could this be exploited?
- **Quality / correctness** — is the logic right, does it match our architecture and standards?
- **Testing** — missing edge cases, untested paths, brittle assertions, coverage gaps.
- **Docs / readability** — can the next person read this?

### The trigger, the output, and the object that travels

- **Trigger:** GitHub emits a `pull_request` webhook when a PR opens. Precise, not "someone mentions a PR."
- **Output:** a single **structured** review posted back, with findings attached to specific files and lines.

"Structured" is doing real work here. The unit that flows through every component is a **Finding**:

| Field | Why it exists |
|---|---|
| `agent_type` | Which concern raised it → traceability |
| `severity` + `category` | How bad, and of what kind |
| `file` + `line` | Exact location → this *is* the evidence |
| `rationale` | Why — makes the finding auditable and disputable, and lets you debug a wrong finding after the fact |
| `confidence` | Drives the human gate: post automatically, or route to a human |

Both `rationale` and `confidence` are needed: confidence decides the routing, and rationale is how you diagnose the case where the confidence itself was wrong.

### The four rungs of review automation

Where the industry sits, and why each rung is insufficient:

1. **Linters** — pattern/style matching. Cannot reason about intent or logic.
2. **Static analysis** — dataflow and type analysis, finds real bugs. High false-positive rate, no codebase-wide judgment, can't read docs.
3. **Single-LLM review** — one prompt judges the whole diff. One mindset for four concerns, no grounding in the repo, hallucinates confidently, nothing auditable.
4. **Agentic fan-out / fan-in** ← the chosen pattern. Parallel specialists, then an aggregator that merges, dedupes, scores, and routes.

### The grounding problem

A senior reviewer doesn't have this problem — they already know the repo. The agent is a stranger. You can't paste the repo into the prompt (context blows up and quality collapses), so: **for each diff, retrieve only the most relevant slices of the codebase.** Retrieval is what turns the stranger into a colleague. Notably, the requirement isn't just "code similar to the diff" but "code the diff would *impact*."

### Three shapes of memory a reviewer needs

| Memory | Content | Natural storage shape |
|---|---|---|
| **Semantic** | The codebase itself — functions, classes, conventions, ADRs | Vector embeddings + similarity search |
| **Episodic** | Past reviews: what was flagged before, what was disputed, when | Time-ordered relational rows (expirable) |
| **Procedural** | How *this team* likes things done | Small, structured facts and rules |

### Trust needs a third thing: proof

Scenario: the agent posts "this endpoint is vulnerable to SQL injection, confidence 60%," and the developer disputes it. If there's no record of *why* it was raised, what context was retrieved, which prompt version ran, which model answered, and what it cost — the finding can't be defended, debugged, or improved.

So beyond reasoning and grounding, every action needs an **event spine**: every span, LLM call, tool call and decision written to a durable time-ordered log. One event stream powers three things — a **trace viewer** (reconstruct any review end-to-end), an **audit trail** (defend or retract any finding), and **economics** (what does a review cost).

### The human-in-the-loop gate

Concrete policy:

- Confidence below threshold (starts around **0.6**, tightens as the system matures) → human approval queue.
- Any **critical** finding → escalate regardless of confidence.
- Developer disputes a posted finding → record the feedback (reversibility + learning loop).
- **Maturity owns the autonomy** — the threshold is not a constant.

One notable side rule: on a **public** repo, a security finding should never be posted openly on the PR. It goes to a private channel. Publishing an exploitable vulnerability in a public thread is itself the harm.

---

## 4. Part 2 — Data engineering

Three data shapes, named after what they're *for*:

| Shape | What it holds |
|---|---|
| **Memory** | Code chunks + embeddings, past reviews, conventions — everything that helps the agent understand a new diff |
| **Truth** | What actually happened: findings, GitHub review IDs, HITL decisions, human overrides |
| **Time** | Observability: spans, LLM calls, tool calls, cost, latency, decisions |

The obvious move is three stores (vector DB + Postgres + a time-series DB). The video argues against it: answering one ordinary question — *for this PR, what code did we retrieve, what review did we produce, and which model calls were expensive?* — now means three queries stitched together in Python, three connection pools, three backup stories, three sets of failure modes. **You pay maintenance and reliability cost for a shape convenience.**

The chosen answer: keep the three *shapes*, collapse to **one durable store** — a managed Postgres (Tiger Cloud / TimescaleDB *(ASR)*) with extensions:

- **pgvector** — embeddings in a column alongside their metadata.
- **pgvectorscale + DiskANN** — index on SSD rather than RAM, so millions of code chunks still return nearest neighbours fast.
- **Hypertables** — time-partitioned chunks for the agent-event stream; a "last hour" dashboard query touches one chunk, not the whole history.
- **Continuous aggregates** — a maintained rollup (cost/minute, p95 latency, token totals) so the cost dashboard never scans 10M raw rows. The budget guard reads the rollup and can **block further LLM calls once the day's spend crosses the limit**.

The stated reason for a Postgres-shaped store over a pure vector DB: a PR review isn't only a similarity question. It's similarity **plus** repo filters, freshness, exact identifier matching, review records, cost records and audit history — so the retrieval result should live next to the metadata and the review trail.

Redis stays, for one job only: the job queue (Redis + ARQ *(ASR)*).

---

## 5. Part 3 — Architecture assembly

### Ingress: why a queue at all

GitHub expects a fast acknowledgement of a webhook (order of ~10s). An LLM review takes 30–90s. So the ingress cannot be the reviewer.

Flow: webhook arrives → **verify the HMAC signature on the payload** (reject forgeries *before any processing*) → **check the idempotency key** (a retried delivery is acknowledged and dropped) → enqueue the job → return 200 immediately. A separate worker pool picks the job up.

The receptionist/chef analogy: one person taking orders and cooking will drop both. Split them.

**Where it breaks at scale:** queue depth outgrows worker drain rate; a single worker becomes the bottleneck. The modular-monolith answer is to extract the webhook receiver as a stateless ingress service and run the orchestrator as a separate worker pool — same codebase, separate scaling.

### Orchestration: LangGraph vs Temporal

The four specialists have no dependency on each other, so the pattern is **fan-out → aggregate → gate**. The engine choice is argued rather than assumed:

| | LangGraph | Temporal |
|---|---|---|
| Infrastructure | Runs in the Python process | Separate server to operate |
| Parallel fan-out | First-class (Send API) | Supported, heavier to express |
| Checkpointing | To the Redis you already run | Stronger built-in durability guarantees |
| LLM integration | Purpose-built | Generic workflow engine |
| Maturity | Newer | Enterprise-proven at scale |
| Operational cost | ~none beyond the app | You run the server |

Verdict: **LangGraph for the MVP** — but that's a *reversible* decision only if you make it reversible. Hence the strongest engineering point in this section:

> Don't build the system around the framework. Define one abstract `WorkflowEngine` interface — `run`, `resume`, `get_state` — and implement it per engine. Swapping to Temporal later becomes a new implementation class, not a rewrite.

### The assembled MVP

```
GitHub PR opened
   └─ webhook → FastAPI ingress
        ├─ verify HMAC signature        (reject forgery before any work)
        ├─ check idempotency key        (drop retried deliveries)
        └─ enqueue to Redis/ARQ · return 200 immediately
             └─ worker picks up one job
                  └─ LangGraph orchestrator
                       ├─ retrieval: embed the diff → hybrid search
                       │   (vector + full-text) over code chunks → top-k slices
                       ├─ FAN OUT (parallel, each grounded by retrieval):
                       │     security · quality · testing · docs
                       ├─ AGGREGATOR: merge · dedupe · score overall confidence
                       └─ CONFIDENCE GATE
                            ├─ confident + nothing critical → post review to GitHub
                            └─ otherwise → human approval queue (dashboard)
   Every step above writes to the event spine (agent_events hypertable)
   → trace viewer · audit trail · cost ledger · budget guard
```

Other decisions listed for the build-out phases: **model routing per agent** (a cheap model for the docs agent, the strongest model for security), a **prompt registry** (versioned, never overwritten, so a bad review can be traced to the prompt that produced it), a **golden dataset + regression gate** (a new prompt or model must beat the deployed one before it ships), OpenTelemetry into the event hypertable, RBAC with an immutable audit trail, and the reliability layer (retries, circuit breakers, idempotency) verified under **fault injection**.

---

## 6. Part 4 — Implementation: the "Genesis kit" harness

The last hour is about *how to drive a coding agent*, and the framing is blunt: **this is not vibe coding.** Most of the time went into design; the agent is directed against that design, not asked to invent it.

The harness (the presenter's own open-source kit) produces a set of state files:

| Artefact | Role |
|---|---|
| **`done.html`** — the locked spec | What "done" means: the cognitive job, inputs, autonomy level, failure tolerance. **The agent is not allowed to edit it** — because a coding agent will otherwise quietly redefine the goal to something it can reach. |
| **`implementation-notes`** — live state | What's actually built right now, the active loop, the current milestone, known gaps, blockers. Consulted before creating anything, so the agent doesn't rebuild what exists. |
| **Context graph** — invariants | The non-negotiables, e.g. *every webhook payload passes signature verification*, *every delivery is deduped by its idempotency key*, *every finding carries confidence and rationale*. Plus a graph of the code so a change in one place surfaces what else must be re-tested. |
| **`index.md`** | Sources, concepts, how it works — the project's documentation spine (credited to Karpathy's approach *(ASR)*). |
| **`plan.md`** — milestones | Per milestone: outcome, phase, files/bounds it may touch, **demo command**, success criteria, which loops run, which skills load, external dependencies, and a **token budget**. |
| **`current.md`** — checkpoints | Written at every gate, so a fresh session resumes exactly where the last one stopped. |

### The loop and its gates

Before any work, **G0** runs: search for the thing you're about to build, read the implementation notes, confirm it doesn't already exist, write a checkpoint, and emit a verdict — *built* or *unbuilt*. Only "unbuilt" proceeds.

Then the build loop (`while milestone not done and iterations < 10`): produce a micro-plan → load the relevant skills → edit → run tests → if it fails, enter the debug loop; if it needs knowledge, enter the research loop → checkpoint each iteration.

The five gates check: were the right skills loaded; did this iteration **measurably** move the milestone; was it within budget; was quality verified; did an **independent** checker confirm it against the demo command.

### The two ideas most worth stealing from this section

**1. The independent verifier.** When the builder says it's done, a **separate agent with fresh context** is spawned. It is given only the milestone goal, the success criteria and the invariants — deliberately **not** the builder's reasoning trail — and told: *you did not write this code; do not assume the builder's claims are true.* In the demo it immediately caught a real defect the builder had declared clean (a validly-signed but malformed payload producing a raw 500 instead of a 400). The debug loop then fixed it and added a regression test.

The analogy given: an agent writing and then grading its own test paper will only write the questions it already knows the answers to. The teacher doesn't know your reasoning — only the input and the expected output.

**2. Quiz-me.** Instead of accepting "looks good," the harness interrogates *you* on the code that was just written — a design question, an edge-case question, an impact question, each multiple-choice with a "not sure / skip" option. In the demo this surfaced two things a skim would have missed: that `TRUNCATE` doesn't fire row-level `DELETE` triggers (so the append-only invariant needed a separate `BEFORE TRUNCATE` trigger), and an embedding-dimension mismatch between the schema (`vector(1536)`) and the `.env` (`256`) that would have failed at insert time several milestones later. Its purpose is stated plainly: so you can't later claim the decision wasn't yours.

### Cost discipline

Threaded throughout: use a strong model as **orchestrator/planner only** and spawn cheaper models for the mechanical work; give every milestone a token budget; and the presenter's own rule — *if you generated a token, justify its output.*

---

## 7. The principles, condensed

1. Selectivity, not coverage. Surface what's worth senior attention; defer the rest.
2. Find the human system that already solved it, and copy its structure — not its steps.
3. Precise trigger, precise output, and a typed object that travels between every component.
4. Separate concerns → separate agents. One prompt wearing four hats performs like none of them.
5. Every finding carries location, rationale, and confidence — or it isn't reviewable, disputable, or debuggable.
6. Ground with retrieval; a model reasoning over a diff it has no repo context for is guessing.
7. Confidence drives autonomy. Autonomy is earned with maturity, not assumed at launch.
8. Anything not recorded cannot be defended, debugged, or improved. Build the event spine early.
9. Enumerate failure modes per component *before* designing it; the defences write the design.
10. Don't build around a framework — put one abstract interface between you and it.
11. Fewer datastores beats a perfect shape per feature; you pay for every store in reliability, not just money.
12. Not every feedback is good feedback — require a minimum evidence threshold before learning from it.
13. Never let the builder grade its own work. Verify with fresh context and the success criteria only.
14. A system fails when an assumption fails. Register the assumptions.

---

## 8. Caveats and gaps

- **It's an MVP baseline, by design.** The presenter says explicitly that the *internal* design of each specialist agent — the security agent's actual reasoning logic, state handling, disambiguation, latency, output ethics — is not covered here and is deferred to future videos. What you get is the skeleton.
- **Only ~2 of ~9 milestones are built on camera** (webhook ingress, database infrastructure). The rest is left as an exercise.
- **A vendor thread runs through it.** Tiger Cloud is presented with a referral link and free credits. The technical argument for a single Postgres-shaped store is sound and stands on its own; the specific vendor choice is sponsored, so treat it as one option, not a conclusion.
- **The evaluation layer is named but not built.** Golden dataset, regression gate, eval-driven prompt updates are listed as phases, not demonstrated.
- **Caption quality is poor** — heavy accent + auto-ASR. Technical terms are frequently mangled (Anthropic → "entropic", Redis → "radius", idempotency → "item potency", Qdrant → "cuteant", DiskANN → "disc NN"). Treat any exact name here as reconstructed unless you verify it against the linked repo.
