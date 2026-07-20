# Adopting Karpathy-style working principles into meApp's CLAUDE.md

**Status:** proposal for review · **Source compared:** [multica-ai/andrej-karpathy-skills `CLAUDE.md`](https://github.com/multica-ai/andrej-karpathy-skills/blob/main/CLAUDE.md) vs. meApp's `/CLAUDE.md`, `iOS/CLAUDE.md`, `Android/CLAUDE.md`

---

## TL;DR

The Karpathy file and our files are **complementary, not competing**:

| | Karpathy `CLAUDE.md` | meApp `CLAUDE.md` (×3) |
|---|---|---|
| Answers | *How should the agent behave?* | *What is this project & its conventions?* |
| Content | 4 behavioral principles | Build commands, paths, architecture, gotchas |
| Length | ~1 screen | Long reference docs |
| Weakness | No project facts | **Almost no default "working posture"** |

**The one gap worth filling:** our files tell Claude *what the code is* but not *how to approach changing it* unless the task happens to route through a skill (`self-review`, `post-change-guard`, `verify-tests`, …). A short **"Working Principles"** block at the top of the root `/CLAUDE.md` makes the good posture the default even for one-off edits.

**Do not adopt it verbatim** — two of its rules contradict your own documented preferences. Reconciled version is in [§4](#4-recommended-drop-in-block).

---

## 1. What the Karpathy file does well

Four principles, each a short "core idea + concrete rules":

1. **Think before coding** — state assumptions explicitly; surface uncertainty; present multiple interpretations when ambiguous; propose simpler approaches; push back on flawed requests.
2. **Simplicity first** — write the minimum; no scope creep, no premature abstraction, no unrequested configurability, no defensive handling of impossible cases; "would a senior engineer call this overcomplicated?" self-test.
3. **Surgical changes** — change only what the request demands; don't refactor working/adjacent code; match existing style; *flag* pre-existing issues instead of fixing them unprompted; clean up only the debris your own change created.
4. **Goal-driven execution** — turn vague tasks into verifiable criteria; reproduce a bug with a failing test before fixing; keep tests green across refactors; plan multi-step work as `[action] → verify: [checkable outcome]`; loop until verified rather than assuming done.

Success indicators it names: fewer unnecessary diff lines, fewer overengineering rewrites, clarifying questions *before* mistakes.

**Why it's good:** it's model-facing behavior guidance, short, and framed around *diffs and verification* — exactly the failure modes an agent falls into on a large codebase.

---

## 2. What we already have (so we don't duplicate)

A lot of principle #4 and parts of #2/#3 are **already operationalized as skills/hooks** — that's a strength, keep leaning on it:

- **Simplicity / surgical** → `code-simplifier` agent, `/simplify`, `review-code-standards`.
- **Goal-driven / verify** → `verify-tests`, `run-tests`, `analyze-coverage`, JaCoCo 80% gate (Android), coverage minimums (iOS), `self-review`, `post-change-guard`.
- **Don't-break-adjacent** → `review-regression`, `silent-failure-hunter`, snapshot-boundary audit + SwiftLint custom rules (iOS).
- **Docs stay surgical & current** → `docs-freshness-check.sh` PostToolUse hook + `/update-architecture` + `/update-confluence`.

So the *missing* piece is not the machinery — it's the **plain-language default posture** stated where every session reads it. That's the only thing worth importing.

---

## 3. Reconciling with your established preferences ⚠️

Two Karpathy rules **conflict** with how you've told me you want to work. Adopt the reconciled form, not the original:

| Karpathy says | Your documented preference | Reconciled rule to adopt |
|---|---|---|
| "If confused, **stop and ask** rather than guess." | Global CLAUDE.md: *"Prefer acting on a sensible default over asking; state the assumption you made."* | **State the assumption and proceed.** Only stop for genuinely blocking or hard-to-reverse ambiguity. |
| "Don't improve adjacent code / leave working code alone." | Memory *systematic-over-reactive*: *when patching N sites, surface the architectural fix that prevents site N+1.* | **Keep the diff surgical, but *surface* the systemic fix** as a note — don't silently expand scope, don't stay silent either. |

Two more that need a meApp-specific caveat so they aren't misread:

- **"No defensive error handling."** Correct *only* for genuinely impossible cases. This is a HIPAA health app with a `silent-failure-hunter` agent — real error paths still must be handled. Frame as "don't guard the impossible," not "skip error handling."
- **"Match existing style even if you'd prefer different."** True, but our style is *enforced* (SwiftLint `--strict`, detekt no-`!!`, theme-token rules, snapshot boundary). So "match existing style" here means "obey the lint/architecture rules," which is stronger than a preference.

Everything else in the Karpathy file transfers cleanly.

---

## 4. Recommended drop-in block

Add this as a new section near the **top of the root `/CLAUDE.md`** (right after *Project Overview*), so it applies to both platforms. It's the Karpathy 4, reconciled with §3 and pointed at our existing skills:

```markdown
## Working Principles (how to approach any change)

These govern *how* to work; the sections below govern *what* the project is.

1. **Think before coding.** State the assumptions you're making and proceed on a
   sensible default — don't stop to ask unless the ambiguity is blocking or hard to
   reverse. If a simpler approach exists, say so before building the complex one.
2. **Simplicity first.** Write the minimum the request needs. No speculative
   features, premature abstraction, or unrequested config. Don't add defensive
   handling for *impossible* cases (real error paths still matter — this is a HIPAA
   app). If a change balloons, stop and simplify; run `/simplify` when in doubt.
3. **Surgical changes.** Touch only what the request demands. Don't refactor working
   or adjacent code, and match the surrounding style — which here means obeying the
   enforced rules (SwiftLint `--strict`, detekt no-`!!`, theme tokens, the iOS
   snapshot boundary). **When you patch several sites of the same bug, surface the
   one architectural fix that would prevent the next one** — as a recommendation,
   without silently expanding the diff.
4. **Goal-driven execution.** Turn a vague task into a checkable outcome before
   coding. Reproduce a bug with a failing test, then fix it; keep tests green across
   refactors. Before committing, run `/self-review` (and `/post-change-guard` on
   iOS). Don't declare done until the criterion is verified.

Good session = small diffs, no overengineering rewrites, assumptions stated up front.
```

**Placement rationale:** root file = cross-platform, so one block covers iOS + Android. The platform files already point to the root for the shared overview, so no duplication needed there.

---

## 5. Optional, lower-priority ideas

- **Android `analytics` naming** and **iOS `accessibility` DoD** are already great examples of "what good looks like" tables. Karpathy's "success indicators" idea could be echoed as a one-line *Definition of Done* at the top of each platform file, but this is largely covered by `self-review`. Skip unless you want it explicit.
- If you like the block in §4, consider mirroring the exact same wording into `~/.claude/CLAUDE.md` under a "How to work with me" note so it also applies to SageApp — but SageApp has its own conventions, so keep it generic there.

---

## 6. Recommendation

**Adopt:** the §4 drop-in block into root `/CLAUDE.md`. It's the one genuine gap, it's short, and it's reconciled with your existing preferences and skills.

**Don't adopt:** the raw "stop and ask" and "never touch adjacent code" rules — they fight your documented working style.

**No change needed to:** the reference content (paths, build, architecture, gotchas) — those are already stronger than Karpathy's file, which has none.
