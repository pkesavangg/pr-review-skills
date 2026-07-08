# MOB-518 — Weight-graph rebuild docs

Reference set for the **weight-graph chart-engine rebuild** (MOB-518, Task 2 of epic MOB-516).
The whole rebuild ships as **one working PR** on branch `MOB-518-chart-engine-scroll-hitch-multi-series`.

## Docs in this folder

| Doc | What it is |
|-----|-----------|
| [MOB-518-chart-engine-rearchitecture.md](MOB-518-chart-engine-rearchitecture.md) | **The plan.** Target architecture, root-cause inventory (S1–S10), phased migration, decisions. Read this first. |
| [MOB-518-weight-graph-implementation-guide.md](MOB-518-weight-graph-implementation-guide.md) | **The how-to.** File-by-file, phase-by-phase changes + per-phase device QA + touch map. Execute against this. |
| [MOB-518-weight-graph-focus.md](MOB-518-weight-graph-focus.md) | **The baseline.** W1 (de-`@Published` scrollPosition) + W2 (`ChartDomainSanitizer`) root-cause + shipped fixes that Phase 0 starts from. |

## Locked decisions

- **Delivery:** single working PR on this branch; phases = ordered commits (no per-phase PRs).
- **Y-axis:** stays **adaptive** (Y-B) — same behaviour as today, just one clean settle event.
- **Tests:** none written until the whole weight graph is signed off (Phase T); verify on device + temp `#if DEBUG` probes until then.
- **Baby/BPM:** not touched until weight graph is approved; shared-code edits carry `// MULTI-SERIES:` notes.

## Companion docs (kept in `../` — they cover the whole MOB-516 epic, not just the weight graph)

- [../MOB-516-implementation-plan.md](../MOB-516-implementation-plan.md) — epic execution log (Tasks 1–4)
- [../performance-analysis-5.1.0.md](../performance-analysis-5.1.0.md) — Instruments evidence
- [../performance-remediation-plan.md](../performance-remediation-plan.md) — epic technical plan
- [../performance-issues-overview.md](../performance-issues-overview.md) — plain-English summary
- `../../meApp/Features/Dashboard/GraphViewFlow.md` — current chart architecture reference (in the source tree)
