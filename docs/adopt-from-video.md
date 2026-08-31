# What to adopt from the multi-agent PR-reviewer video

Companion to [video-notes-ai-pr-review-agent.md](video-notes-ai-pr-review-agent.md). This one asks a single question: **of everything that video prescribes, what is genuinely worth adding to *this* repo — and what should be ignored?**

---

## 0. Framing: two different machines

The video builds a **runtime service** — GitHub webhook → HMAC verify → Redis queue → LangGraph worker → four LLM agents → Postgres → posts back. It owns infrastructure, runs unattended, and pays for its own tokens.

`pr-review-skills` is a **prompt-orchestrated skill** — a human runs `/review-pr 1234` in Claude Code, `gh` provides the auth and transport, Claude provides the reasoning, GitHub stores the state. There is no server, no queue, no database.

So roughly **half the video is structurally inapplicable** (§5 below). What transfers is not the stack — it's the **decision architecture**: how a finding is shaped, when a finding is allowed to be posted, and how the reviewer proves it was right.

The honest headline: this repo already implements more of the video's *rule* layer than the video does. What it doesn't yet have is the video's **gate, proof and learning** layers.

---

## 1. Scorecard — video prescription vs. what's already here

| Video prescribes | Status here | Where |
|---|---|---|
| Fan-out to specialists by concern, not one prompt | ✅ **Exceeds it.** 4 specialists in the video; this repo has security, privacy, platform (SwiftUI/Compose/Appium/SDK), asset-references, and a language-idiom lens — ~40 rule files across 5 tracks | [review-pr.md](../.claude/commands/review-pr.md) § 4a.0–4a.7 |
| Aggregator merges + dedupes | ✅ Present, though scattered | § 4a.4 + per-track "SDK wins" / "supersedes" clauses |
| Severity + category on every finding | ✅ `P0` / `P1` / `P2` / `Nit`, with per-rule prescribed severity | § Priorities |
| Location + evidence on every finding | ✅ Inline comments anchored to `path:line`; rules carry Sniff + Fix | § 4a.5 |
| **Rationale** on every finding | ✅ **Implemented** — the comment body is three clauses, `<issue> · <evidence> · <fix>` | § 4a.5 |
| **Confidence** on every finding | ✅ **Implemented** — `high`/`medium`/`low`, assigned at creation | § 4a, § Confidence |
| Confidence gate → post vs. escalate | ✅ **Implemented** — `high` posts · `medium` posts only at P0/P1 · `low` → *Worth a look* | § 4a.5 |
| Skepticism / verify before trusting | ✅ **Strong.** Re-review reads the code, never the author's claim; tickets are verified for existence *and* relevance | § 4b.1, § Ticket verification |
| Grounding in the repo, not just the diff | ✅ In spirit — rules mandate pulling whole files from the branch | § 4a.6, § 4a.7 |
| Semantic memory (codebase) | ✅ On-demand file reads. A vector store would be overkill | — |
| Procedural memory (team conventions) | ✅ `references/**` + deference to repo `CLAUDE.md` | § Guardrails |
| **Episodic memory (past reviews / disputes)** | ✅ **Implemented** — [references/disputes.md](../references/disputes.md) + the run ledger | § 4b.1 step 5 |
| Event spine / trace / audit | ✅ **Implemented** — one JSON line per run to `~/.claude-review/runs.jsonl`, every candidate recorded | § Step 5.5 |
| Cost ledger | ⚠️ Partial — the ledger records rule hits and outcomes, not tokens (no metered API to price) | § Step 5.5 |
| Idempotency / dedup before posting | ✅ **Implemented** — fuzzy ±5-line substance match **plus** an exact content-hash check against the ledger | § 4a.4 |
| Autonomy gate tied to maturity | ✅ Approve only when genuinely clean **and** nothing withheld; `--request-changes` rollout-gated | § 5 |
| Feedback loop with minimum-evidence threshold | ✅ **Implemented** — ≥3 disputes across ≥2 authors/repos before a rule is even reviewed | § 4b.1 step 5 |
| Golden dataset + regression gate before shipping a change | ⚠️ Fixtures exist (`test-fixtures/`), no expected-findings manifest, no runner | → § 3.1 |
| Independent verifier with fresh context | ✅ **Implemented** — every P0/P1 goes to an agent that sees the code and the claim but not the rule | § 4a.4.5 |
| Locked spec the agent may not redefine | ❌ Missing. The orchestrator grows every sprint | → § 3.2 |
| Model routing per specialist | ❌ Single session, one model for all lenses | → § 3.4 |
| Fan-out / parallelism | ✅ **Implemented at the PR level** — one agent per PR, 4 concurrent, plus a concurrent verifier batch. Per-*lens* fan-out is still § 3.4 | § Step 0.5 |
| Untrusted-input / prompt-injection handling | ✅ Explicit | § Guardrails |
| Public-repo security escalation off-thread | ❌ Missing (low relevance — MOB repos are private) | → § 4 |

**Pattern in the remaining gaps:** what's left is the *maintenance* layer — a regression gate for rule edits, a locked purpose spec, and per-lens model routing. The memory and self-check gaps that dominated this table are closed.

---

## 2. Adopt now — ✅ **shipped**

All four landed on 2026-08-17, plus parallel multi-PR review (§ 2.5). The subsections below keep the *reasoning* — why each mechanism exists — because that's what a future maintainer needs when deciding whether to weaken one. For how they actually work, see [HOW-IT-WORKS.md](../HOW-IT-WORKS.md) and the orchestrators.

| | Mechanism | Lives in |
|---|---|---|
| 2.1 | Confidence + evidence, and a gate that withholds | `review-pr.md` § 4a, § 4a.5, § Confidence · `review.md` § 4, § 4.5 |
| 2.2 | Independent fresh-context verifier on P0/P1 | § 4a.4.5 · § 4.4.5 |
| 2.3 | Run ledger + exact-hash idempotency | § Step 5.5 · § 4a.4 |
| 2.4 | Dispute ledger with a ≥3 threshold | § 4b.1 step 5 → [references/disputes.md](../references/disputes.md) |
| 2.5 | Parallel per-PR review agents | § Step 0.5 |

### 2.1 Confidence on every finding, and a gate that uses it ✅

**The video's argument:** the point isn't coverage, it's selectivity. A finding you're 55% sure about, posted with the same weight as one you're certain of, is how a reviewer loses the author's trust. Confidence is what lets the system *defer* instead of *dumping*.

**The gap here:** every candidate finding that survives de-dup gets posted, at every priority, with no expressed certainty. The current implicit optimum is coverage — read ~40 rule files, apply all of them. That is precisely the thing the video argues against, and it's the single biggest philosophical difference between the two systems.

**Concrete change** — in § 4a.5, require each finding to carry an internal confidence before posting, and route on it:

| Confidence | Meaning | Action |
|---|---|---|
| **high** | The rule's Sniff pattern matched *and* the surrounding code was read and confirms it | Post inline as today |
| **medium** | Pattern matched but context is ambiguous (couldn't verify the asset exists, couldn't confirm the helper, judgment-based rule) | Post **only** if `P0`/`P1`; otherwise roll into the Step 5 summary as "worth a look" |
| **low** | Inferred from the diff alone, couldn't verify | Never post inline. One summary line, or drop |

Several rules already imply this — `ios/asset-references.md` mandates verifying an asset with `find` rather than guessing, and `appium/locators.md` says to actually check for an id before choosing P1 vs P2. Those are confidence checks in disguise. **Making the field explicit turns a per-rule habit into a system-wide gate.**

Also worth adding to the comment format itself: the video's insistence that *"this looks off" is not a review*. Extend the mandated shape from `P1 — <issue> · <fix>` to `P1 — <issue> · <evidence> · <fix>` where the evidence is the observed fact (the nil path, the missing `.imageset`, the unawaited call), not a restatement of the rule name. Keep the `P<n> — ` prefix exactly as-is — Step 3's mode detection depends on it.

### 2.2 An independent verifier before posting ✅

**The video's argument:** the builder grading its own work only writes the exam questions it already knows the answers to. In the demo, a fresh-context verifier — given only the goal, the success criteria and the invariants, explicitly *not* the builder's reasoning — immediately found a real defect the builder had declared clean.

**Why it matters more here than anywhere else in this list:** a wrong `P1` on a teammate's PR is more expensive than a missed one. A missed bug is a bug; a wrong blocker is a credibility hit that makes the next twenty real findings easier to ignore. The "almost right" failure mode — 90% correct, subtly misattributed — is exactly the shape LLM review findings take.

**Concrete change** — a new step between § 4a.4 (de-dup) and § 4a.5 (post): for every candidate `P0` and `P1`, spawn a subagent with **fresh context** that receives only the diff hunk, the surrounding file, and the claim — *not* the rule file and not the reasoning that produced it — and is told to **refute** it. Default to refuted when uncertain. A refuted `P0`/`P1` is demoted or dropped, and the summary reports `Verified:N Refuted:M`.

Scope it to P0/P1 and cap the count so the cost stays bounded. This is also the cleanest possible use of the existing `pr-review-toolkit` subagents.

### 2.3 A run ledger — the event spine, scaled down ✅

**The video's argument:** without a record of why a finding was raised, what context was used, and what it cost, the system can't be defended, debugged, or improved. *If it isn't measured, it can't be improved.*

**The gap here:** there is currently no way to answer the most useful maintenance question in this repo — **which rules actually produce findings that people act on?** With ~40 rule files, some are certainly dead weight and some are certainly noise, and today there's no evidence either way.

**Concrete change** — append one JSON line per run to `.claude-review/runs.jsonl` (gitignored, or a shared file if you want team-wide data):

```json
{"ts":"2026-08-16T14:22:03Z","repo":"gg/SageApp","pr":1954,"mode":"first-review",
 "track":"compose","rule_files":34,"candidates":18,"dropped_dedup":3,"dropped_confidence":2,
 "refuted":1,"posted":{"P0":0,"P1":3,"P2":4,"Nit":0},"verdict":"COMMENT",
 "findings":[{"rule":"compose/asset-references#getIdentifier","file":"KettleCard.kt","line":88,
              "priority":"P1","confidence":"high","hash":"a91f…"}]}
```

Three things this unlocks immediately:

1. **Rule pruning.** Cross-reference against which comments got resolved vs. ignored. A rule that fires often and is never acted on is noise; delete it.
2. **True idempotency.** The `hash` per posted finding is the video's idempotency key. Re-running `/review-pr` on the same PR, or the cloud routine racing a manual run, currently relies on substance-matching de-dup; a hash makes it exact.
3. **Episodic memory** (§2.4 builds on this).

Costs almost nothing — it's an append at the end of Step 5.

### 2.4 A dispute ledger with a minimum-evidence threshold ✅

**The video's argument:** record disputes so the system learns — but *not every feedback is good feedback*. A junior's bad pushback shouldn't retrain the reviewer, so require a **minimum evidence threshold** (N independent disputes) before acting, and decay stale feedback.

**The gap here:** the re-review pipeline already classifies outcomes beautifully (`✅ Resolved` / `✅ Accepted` / `⚠️ Partially` / `🎫 Awaiting ticket` / `❌ Still open`) — and then throws that signal away when the run ends. That's the **episodic memory** hole in the scorecard, and the data is already being computed.

**Concrete change** — when § 4b.1 records `✅ Accepted` on the grounds of *"intentional choice with a technical reason"*, append the rule id + repo + reason to `references/disputes.md`. Then a rule that accumulates **≥3 independent accepted-disputes across different PRs and authors** gets reviewed for amendment or a documented carve-out — never on the first dispute. Same shape as the video's threshold, and it makes the rule files evidence-driven instead of intuition-driven.

One deliberate exclusion: a deferral closed by a verified ticket (`✅ Accepted — tracking in MOB-1234`) is **not** a dispute. The finding was right and is being scheduled; logging it would teach the reviewer to stop reporting things people intend to fix.

### 2.5 Parallel review agents, one per PR ✅

**Not from the video** — this is the video's fan-out/fan-in pattern applied at the level where it actually pays here. The video fans out four *specialists* over one PR; this repo already has far more than four lenses, but it was processing multiple *PRs* strictly one at a time. Reviewing a sprint's worth of PRs was a serial wait for no reason: the PRs are independent, often in different repos, with separate diffs and separate comment threads. Nothing needed serializing.

**Concrete change** — § Step 0.5: two or more targets fans out one general-purpose agent per PR, 4 concurrent, each executing Steps 1–5 for its own PR and posting its own comments. `--sequential` opts out.

Two things had to be designed around, both worth remembering before extending this:

1. **Agents share one working tree.** § 4b.3's `git fetch origin pull/N/head` mutates shared refs, so parallel agents are banned from any git write and read PR content over the API instead (`gh api .../contents?ref=<sha>`, `gh api .../compare/<a>...<b>`). This turned out to be *more* correct even sequentially — the local checkout is usually on an unrelated branch, so a file read off disk may not be the code under review at all.
2. **One writer for the ledger.** Concurrent appends can interleave a half-written line. Agents return their ledger object; the parent writes all of them.

Failure is isolated per PR — an agent that dies prints `PR #N — ERROR · …` and the rest carry on.

---

## 3. Adopt next

### 3.1 Golden fixtures + a regression gate for rule edits

The video: a new prompt or model must beat the deployed one on a golden dataset before it ships. Here the equivalent risk is real — every rule-file edit changes ~40 files' worth of behaviour with **no test**, and the `references/vendored/` quarterly sync can silently change SwiftUI/Compose behaviour wholesale.

`test-fixtures/` already exists with seven files but no expected output. Complete it: add `test-fixtures/expected/<fixture>.json` listing the findings a correct run must produce (rule id, line, priority), plus a `/review-eval` command that runs `/review` against the fixture tree and diffs actual vs expected. Then wire it into [CLAUDE.md](../CLAUDE.md) as a required step before any rule-file change — including vendored syncs.

Highest-leverage fixtures to add first: one per track that currently has none for its *hardest* judgment call — a scope-creep PR, a description-vs-diff mismatch, and an asset name that exists vs. one that doesn't.

### 3.2 A locked purpose spec (`done.md`) for the reviewer itself

The video's `done.html` exists because a coding agent will quietly redefine the goal to something it can reach. The same drift applies to a 520-line orchestrator that grows a new track every quarter: Appium, then SDK, then code-standards, then asset-references. Each addition was justified; the *aggregate* has never been re-tested against a stated purpose.

Write a short, deliberately stable `docs/done.md`: what this reviewer is for, what it must never do, the invariants (`P<n> — ` prefix is structural; never `--request-changes`; never mutate git; verify before trusting; repo conventions win), and the noise budget (§3.3). Then make [CLAUDE.md](../CLAUDE.md) require that any new rule justify itself against it. The video's framing is exactly right: the point of the locked spec is that **the thing doing the work isn't allowed to move the goalposts.**

### 3.3 A findings budget per PR

Direct application of L0 — *selectivity, not coverage*. Today a large PR run through five tracks can produce 20+ comments, and 20 comments is functionally zero comments.

Add to § 4a.5: post at most **N inline comments per PR** (10 is a sane start), ordered `P0 → P1 → P2 → Nit`; everything beyond the cap is rolled into the Step 5 summary as grouped counts by rule. And per the video's own no-silent-caps discipline, **say what was truncated** — `8 further P2/Nit findings not posted inline (asset naming ×5, magic numbers ×3)` — so the author can still ask for them.

### 3.4 Fan-out to parallel subagents with model routing

The video's fan-out/fan-in is currently collapsed here into a single sequential session reading ~40 rule files. Running the tracks as parallel subagents — cheap model for the mechanical lenses (asset references, naming, metadata, magic numbers), the strongest model for security, correctness and the judgment-based cross-cutting checks — matches both the video's pattern and its cost discipline.

**Real trade-off, worth stating:** the current single-context design is *why* the de-dup and "SDK wins over generic" precedence rules work — every finding is visible to one reasoner at once. Fan-out means the aggregator (§ 4a.4) has to become a genuine merge step over structured findings rather than an implicit one. That's a real refactor, which is why it sits here rather than in §2 — but it's also the thing that makes §2.1's confidence field and §2.2's verifier cheap to run.

---

## 4. Consider — conditional or low priority

- **Security findings escalated off-thread.** The video's rule (never post an exploitable vulnerability publicly on an open-source PR) is correct but mostly moot here — MOB repos are private. Worth one conditional line in § 4a.0: *if the repo is public and the finding is a `P0` security issue, post a neutral placeholder inline and route the detail privately.* Cheap insurance if the BLE SDK or a sample app is ever open-sourced.
- **A failure-mode table for the reviewer itself.** Running the video's 2×2 exercise on this system surfaces: hallucinated line numbers, vendored-rules drift after an upstream sync, `gh` 403 on forks (handled), Atlassian MCP absent (handled), double-posting on concurrent runs (§2.3 fixes), prompt injection in the diff (handled), and a rule whose Fix references a project helper that no longer exists. Documenting the unhandled ones with their fallback is a half-hour of work that pays off the first time one fires.
- **Assumption register.** The video's strongest story is that the system failed because an *assumption* failed. This repo's live assumptions — "the team uses `P<n> — ` only via this skill", "MOB sprint field is `customfield_10020`", "`references/vendored/` matches upstream" — are exactly the kind that break silently. A short list in `done.md` costs nothing.
- **Quiz-me.** Genuinely valuable for the *author* workflow (`/review` could ask "why is this the right fix?" before applying it), but it changes `/review` from a tool into a tutor. Only worth it if you want the skill to teach the team, not just correct them.

---

## 5. Don't adopt — and why

| Video component | Verdict |
|---|---|
| **Webhook ingress, HMAC verification, idempotency keys, Redis/ARQ queue, fast-ack** | Not applicable. There is no service to trigger. `gh` handles transport and auth, and a human triggers the run. *(If you ever want the reviewer to fire automatically on PR open, the answer is a GitHub Action calling `claude --print '/review-pr …'` — not a webhook service.)* |
| **LangGraph / Temporal orchestration + the `WorkflowEngine` abstraction** | Not applicable. The orchestrator is a Markdown prompt; Claude Code is the runtime. The *lesson* still lands though — don't couple to a framework — and this repo already honours it by keeping rules as plain Markdown rather than baking them into skill code. |
| **Tiger Cloud / pgvector / pgvectorscale / DiskANN / hypertables / continuous aggregates** | Not applicable. No persistent state to store. §2.3's JSONL is the right-sized version of the same idea. |
| **RAG over an embedded codebase** | Overkill. The video needs embeddings because its agent is a stranger to the repo with no filesystem. This reviewer has the branch checked out and can `Read`/`Grep` directly — which is *better* grounding, not worse. The rules already mandate it. |
| **The Genesis kit wholesale** | It's a harness for building greenfield software with a coding agent. This repo isn't being built that way. Take the three ideas that generalise — locked spec (§3.2), independent verifier (§2.2), demo-command-as-success-criterion (→ §3.1 fixtures) — and leave the rest. |
| **Prompt registry / model routing per prompt version** | Premature. Revisit if §3.4's fan-out lands, at which point per-lens model choice becomes a real knob. |
| **Token/cost budget guard that blocks LLM calls** | Not applicable — the budget here is a Claude subscription, not a metered API. The *measurement* half is still worth having (§2.3). |

---

## 6. Order — done and remaining

**Shipped 2026-08-17** (§2 in full, plus §2.5): run ledger · confidence + evidence · independent verifier · dispute ledger · parallel per-PR agents. README.md, HOW-IT-WORKS.md and CLAUDE.md were updated alongside, per the repo convention.

**Remaining, in the order I'd take them:**

1. **§3.3 findings budget** — pairs naturally with the confidence gate that just landed, and is a small edit to § 4a.5.
2. **§3.1 golden fixtures** — do this *before* the next `references/vendored/` sync, not after. It's also now cheap to validate against: the ledger gives you a baseline of what currently fires.
3. **§3.2 `done.md`** — write it now that §2 has settled, so it locks the intended system rather than an interim one.
4. **§3.4 per-lens fan-out** — the largest refactor. Note that §2.5 already delivered PR-level parallelism, so the remaining win here is per-lens *model routing* and context relief, not wall-clock on a multi-PR run. Only worth it if a single large PR becomes slow or context-pressured.

**First maintenance action, once the ledger has a few weeks of data:**

```bash
jq -r '.findings[] | "\(.action)\t\(.rule)"' ~/.claude-review/runs.jsonl | sort | uniq -c | sort -rn
```

A rule that fires constantly and is almost always `withheld` or `refuted` is a rule whose Sniff pattern is too broad. A rule that never fires at all across every repo is dead weight. Both are deletions you can now justify with evidence rather than intuition — which was the whole point of §2.3.
