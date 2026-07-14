# MOB-518 — Weight-graph rebuild docs

Reference set for the **weight-graph chart-engine rebuild** (MOB-518, Task 2 of epic MOB-516).
The whole rebuild ships as **one working PR** on branch `MOB-518-chart-engine-scroll-hitch-multi-series`.

## Docs in this folder

| Doc | What it is |
|-----|-----------|
| [MOB-518-weight-graph-how-it-works.md](MOB-518-weight-graph-how-it-works.md) | **★ START HERE to UNDERSTAND it.** A guided tour of how the finished v2 weight graph actually works — the whole flow, legacy-vs-v2 + why it's faster, and each feature (scroll, y-axis, section switch, selection, header average, goal chip, weightless, metric tiles, month ticks, points-on-line) explained with analogies + ASCII flow diagrams. Read this to learn the engine; read the design/spec docs to change it. |
| [MOB-518-weight-graph-v2-engine-design.md](MOB-518-weight-graph-v2-engine-design.md) | **★ CURRENT PLAN.** The greenfield "strangler" rebuild — a new weight-chart engine built alongside the old graph, reusing the domain math, flipped over at parity. Read this first. §8 = architectural backlog (V-A1…V-A6), §9 = the V-A4 decision (Option A locked), **§10 = the single canonical remaining-work roadmap** (wins when the V-/A-/Phase- numbering schemes disagree). |
| [MOB-518-weight-graph-feature-spec.md](MOB-518-weight-graph-feature-spec.md) | **★ PARITY CHECKLIST.** Every behaviour the weight graph must reproduce (periods, aggregation, y-axis, scroll, selection, header, goal, weightless, metrics…) with a ✅/◑/✗ v2-coverage marker each. |
| [MOB-518-weight-graph-known-issues.md](MOB-518-weight-graph-known-issues.md) | **★ DEVICE PUNCH-LIST.** Running log of issues seen on device, each mapped to the step that fixes it. Swept + verified at the end before sign-off. |
| [MOB-518-chart-engine-rearchitecture.md](MOB-518-chart-engine-rearchitecture.md) | **The diagnosis + target architecture.** Root-cause inventory (S1–S10) and the engine design the v2 doc delivers. Still valid; the *delivery mechanism* moved to v2. |
| [MOB-518-weight-graph-implementation-guide.md](MOB-518-weight-graph-implementation-guide.md) | **The (superseded) in-place how-to.** Phase 0 + Phase 1 were executed from this; Phases 2–5 are replaced by the v2 build order. Kept for the S1/animation analysis. |
| [MOB-518-weight-graph-focus.md](MOB-518-weight-graph-focus.md) | **The baseline.** W1 (de-`@Published` scrollPosition) + W2 (`ChartDomainSanitizer`) root-cause + shipped fixes that carry into v2. |

## Locked decisions

- **Approach (2026-07-09):** **greenfield strangler rebuild** — a NEW weight-chart engine (`ChartModel` +
  off-main `ChartPrep` + stable `WeightChartView`) built *alongside* the old graph, reusing the domain math,
  flipped over only at device parity. Not an in-place morph. Executed: Phase 0 (dead code) + Phase 1 (`.id`
  removal) shipped; those learnings carry into v2. See the v2 design doc.
- **Delivery:** single working PR on this branch; steps = ordered commits (no per-phase PRs).
- **Y-axis:** stays **adaptive** (Y-B) — same behaviour as today, just one clean settle event.
- **Tests:** none written until the whole weight graph is signed off (Phase T); verify on device + temp `#if DEBUG` probes until then.
- **Baby/BPM:** not touched until weight graph is approved; shared-code edits carry `// MULTI-SERIES:` notes.

## Companion docs (kept in `../` — they cover the whole MOB-516 epic, not just the weight graph)

- [../MOB-516-implementation-plan.md](../MOB-516-implementation-plan.md) — epic execution log (Tasks 1–4)
- [../performance-analysis-5.1.0.md](../performance-analysis-5.1.0.md) — Instruments evidence
- [../performance-remediation-plan.md](../performance-remediation-plan.md) — epic technical plan
- [../performance-issues-overview.md](../performance-issues-overview.md) — plain-English summary
- `../../meApp/Features/Dashboard/GraphViewFlow.md` — current chart architecture reference (in the source tree)
