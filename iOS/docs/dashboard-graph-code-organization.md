# Dashboard Graph & Snapshot Code — Organization Plan

**Status:** ✅ **Executed** 2026-07-20 on `MOB-1591-…` (commit `24a97c063`) — 39 pure `git mv` renames, Dev build green, 0 `.pbxproj` edits. Decisions locked as recommended (A1/B1/C1/D1/E1). Doc path references updated in the same follow-up.
**Scope:** iOS · `meApp/Features/Dashboard`
**Base:** `develop` (follow-on to MOB-518 + MOB-1516 — the graph is now *one* engine + *three* product recipes; the folder layout hasn't caught up)
**Author note:** this is a **pure move/rename refactor** — no behaviour change, no logic edits.

---

## 0. TL;DR

Two problems, one root cause:

- **The graph engine is scattered** across ~8 directories (`Managers/Graph/`, `Models/`, `Views/Components/`, `Modifiers/`, …).
- **The snapshot cards feel spread out** — but really only **weight** is: BPM and Baby *already* own their
  snapshot card + trend view inside `BPM/` and `Baby/`; **weight has no product folder**, so `WeightSnapshotCard`,
  `WeightTrendView`, etc. are stranded in the shared layer folders.

**Root cause:** the feature mixes two organizing principles — *by layer* at the top (`Managers/`, `Models/`,
`Views/`, `ViewModels/`…) **and** *by product* (`BPM/`, `Baby/`) — and **weight is the homeless default** with
no folder of its own. Even BPM/Baby are only *partially* self-contained (their view-models and reading-arrival
CTAs still live in the shared folders).

**The fix — commit to product symmetry:**

1. **`Weight/`** — a new product folder that mirrors `BPM/` and `Baby/` (the explicit ask).
2. **Finish the pattern** — pull each product's stragglers (view-models, reading CTAs) into its own folder so
   `Weight/` · `BPM/` · `Baby/` are three clean peers, each owning *its* snapshot card + trend view + recipe.
3. **`Chart/`** — one home for the shared, product-agnostic engine (fixes the "graph is scattered" half).

**Why it's cheap and safe:** the `meApp` target is a `PBXFileSystemSynchronizedRootGroup` (verified in
`project.pbxproj`) → files sync from disk → **zero `.pbxproj` edits**. Swift has **no per-file imports** →
moving a file *within the target* cannot break a reference. So this is ~all `git mv` (blame-preserving) + doc
updates, verified by one build + `swiftlint --strict`.

> **Terminology trap.** "Snapshot" is overloaded here. This plan means the **dashboard snapshot *cards*** (the
> per-product summary card in the home carousel). It is **not** about the value-type data snapshots
> (`AccountSnapshot` / `DeviceSnapshot` / `EntrySnapshot` / `AccountSettingsSnapshot`) — those stay put.

---

## 1. The problem — the asymmetry today

**BPM and Baby are (partly) product folders; weight isn't a folder at all:**

| Product | Snapshot card | Trend view | Reading CTA | View-model |
|---|---|---|---|---|
| **BPM** | `BPM/Views/Components/BpmSnapshotCard` ✅ | `BPM/Views/Screens/BpmTrendView` ✅ | `Views/Components/BpmReadingArrivalCTAView` ❌ shared | — (uses store) |
| **Baby** | `Baby/Views/Components/BabySnapshotCard` ✅ | `Baby/Views/Screens/BabyTrendView` ✅ | `Views/Components/BabyReading*CTAView` ❌ shared | `ViewModels/BabySnapshotCardViewModel` ❌ shared |
| **Weight** | `Views/Components/WeightSnapshotCard` ❌ **homeless** | `Views/Screens/WeightTrendView` ❌ **homeless** | `Views/Components/WeightScaleReadingArrivalCTAView` ❌ shared | `ViewModels/WeightSnapshotCardViewModel` ❌ shared |

**And the shared graph engine is spread across 8 places:**

| Current location | Graph files | What it is |
|---|---|---|
| `Managers/Graph/` | `ChartPrep`, `ChartDecimator`, `ChartDomainSanitizer`, `ChartRebuildSignature`, `GraphDataPreparer`, `GraphRenderingConfiguration`, `GraphAnimationManager`, `GraphCalloutSupport`, `GraphInteractionHandler`, `GraphSelectionPresentationResolver` | compute core (10) |
| `Models/` | `ChartModel`, `DashboardChartLayout`, `DashboardChartRules`, `GraphSeries`, `YAxisCalculator`, `SelectionData` | engine data types, mixed with unrelated dashboard models |
| `Views/Components/` | `GraphView`, `TrendChartHost`, `TrendChartView`, `GraphCalloutViews`, `GraphScrollHintModalView`, `GraphSkeletonView` | renderer + seam, mixed with ~20 unrelated components |
| `Modifiers/` | `ScrollDetectionModifier` | chart-scroll only (sole consumer: `TrendChartHost`) |
| `Managers/` (top) | `DashboardChartManager`, `DashboardGraphManager` | host-facing coordinators |

*(Baby's chart recipe — `BabyDashboardChartSupport`, `BabyPercentileGrowthReference`, etc. — is already inside
`Baby/` and **stays there**; see Decision A.)*

---

## 2. The target — three product peers + one shared engine

```
Features/Dashboard/
│
├── Chart/                          ★ NEW — the shared, product-AGNOSTIC engine (fixes "graph is scattered")
│   ├── Model/                      ChartModel, GraphSeries, DashboardChartLayout,
│   │                               DashboardChartRules (per-product colour/scale dispatch), YAxisCalculator
│   ├── Engine/                     ChartPrep (buildWeight/buildBpm/buildBaby), ChartDecimator,
│   │                               ChartDomainSanitizer, ChartRebuildSignature, GraphDataPreparer,
│   │                               GraphRenderingConfiguration, GraphAnimationManager,
│   │                               GraphCalloutSupport, GraphInteractionHandler,
│   │                               GraphSelectionPresentationResolver
│   ├── Managers/                   DashboardChartManager, DashboardGraphManager
│   └── Views/                      GraphView (seam), TrendChartHost, TrendChartView, GraphCalloutViews,
│                                   GraphScrollHintModalView, GraphSkeletonView, ScrollDetectionModifier
│
├── Weight/                         ★ NEW — mirrors BPM/ & Baby/  (the explicit ask)
│   ├── ViewModels/                 WeightSnapshotCardViewModel
│   └── Views/
│       ├── Components/             WeightSnapshotCard, WeightScaleReadingArrivalCTAView
│       └── Screens/                WeightTrendView
│
├── BPM/                            (completed — gains its stray reading CTA)
│   ├── Enums/ Models/ Strings/
│   └── Views/{Components,Screens}  … + BpmReadingArrivalCTAView moves in
│
├── Baby/                           (completed — gains its stray VMs + reading CTAs; keeps its chart recipe)
│   ├── Enums/ Environment/ Models/ Strings/ Utils/  (chart recipe: BabyDashboardChartSupport, percentile math…)
│   ├── ViewModels/                 BabySnapshotCardViewModel, BabyTrendViewModel   ← moved in
│   └── Views/{Components,Screens}  … + BabyReading*CTAView, AssignBabyModalView move in
│
├── Shared/  (or leave at root)     cross-product: MultiDeviceSnapshotView + MultiDeviceSnapshotViewModel
│                                   (the carousel host), SnapshotSkeletonCardView, DashboardTrendView
│
├── Managers/  Models/  Views/  Stores/  Strings/  Protocols/  Utils/  Enums/   (everything non-graph stays)
```

**The payoff:** the folder layout now *teaches the architecture the docs already describe* — *"add a product =
add a `ChartPrep.buildX` + a `DashboardChartRules` branch + a `<Product>/` folder"*. Each product is a peer;
the engine is one place.

---

## 3. Move tables

### 3.1 ★ Create `Weight/` (the explicit ask)

| From | To |
|---|---|
| `Views/Screens/WeightTrendView.swift` | `Weight/Views/Screens/` |
| `Views/Components/WeightSnapshotCard.swift` | `Weight/Views/Components/` |
| `Views/Components/WeightScaleReadingArrivalCTAView.swift` | `Weight/Views/Components/` |
| `ViewModels/WeightSnapshotCardViewModel.swift` | `Weight/ViewModels/` |

> **Weight has no product-specific `Strings/`** today (it uses the shared `DashboardStrings`), unlike BPM
> (`BpmDashboardStrings`) / Baby (`BabyDashboardStrings`). Fine to leave; optionally split a `WeightDashboardStrings`
> later — not required for this refactor.

### 3.2 Finish BPM/ and Baby/ symmetry (recommended — Decision C)

| From | To | Product |
|---|---|---|
| `ViewModels/BabySnapshotCardViewModel.swift` | `Baby/ViewModels/` | Baby |
| `ViewModels/BabyTrendViewModel.swift` | `Baby/ViewModels/` | Baby |
| `Views/Components/BabyReadingArrivalCTAView.swift` | `Baby/Views/Components/` | Baby |
| `Views/Components/BabyReadingAssignedToastView.swift` | `Baby/Views/Components/` | Baby |
| `Views/Components/BabyReadingNoProfileCTAView.swift` | `Baby/Views/Components/` | Baby |
| `Views/Components/AssignBabyModalView.swift` | `Baby/Views/Components/` | Baby |
| `Views/Components/BpmReadingArrivalCTAView.swift` | `BPM/Views/Components/` | BPM |

### 3.3 Consolidate the shared engine into `Chart/` (24 files)

| From | To |
|---|---|
| `Managers/Graph/*.swift` (10 files) | `Chart/Engine/` (rename the confusing `Managers/Graph` → `Chart/Engine`) |
| `Models/ChartModel.swift`, `DashboardChartLayout.swift`, `DashboardChartRules.swift`, `GraphSeries.swift`, `YAxisCalculator.swift` | `Chart/Model/` |
| `Views/Components/GraphView.swift`, `TrendChartHost.swift`, `TrendChartView.swift`, `GraphCalloutViews.swift`, `GraphScrollHintModalView.swift`, `GraphSkeletonView.swift` | `Chart/Views/` |
| `Modifiers/ScrollDetectionModifier.swift` | `Chart/Views/` (chart-only — verified sole consumer) |
| `Managers/DashboardChartManager.swift`, `DashboardGraphManager.swift` | `Chart/Managers/` |

### 3.4 Cross-product / shared — stay at dashboard level (Decision E)

`Views/Screens/MultiDeviceSnapshotView.swift` (+ `ViewModels/MultiDeviceSnapshotViewModel.swift`) — the
carousel that hosts *all* product cards · `Views/Components/SkeletonLoader/SnapshotSkeletonCardView.swift` ·
`Views/Screens/DashboardTrendView.swift` (routes to the product trend views). Not owned by any one product.

### 3.5 Borderline & orphans — handle explicitly, do NOT blind-move by name

| File | Finding | Recommendation |
|---|---|---|
| `Views/Components/WeightDisplayView.swift` | **Used by `BpmDisplayView` too** — it's a shared numeric display, not weight-only | **Leave shared** (don't move into `Weight/`); consider renaming later |
| `Managers/WeightlessDisplayRounding.swift` | Consumed by `DashboardMetricsCalculator` + the chart's `GraphDataPreparer` | **Leave shared** — moving it into `Weight/` would make the engine depend on a product folder |
| `Models/SelectionData.swift` | **Zero consumers** (documented as graph-tap data) | **Delete** after `grep -rn SelectionData` confirms |
| `Views/Components/ReadingArrivalViewCTAView.swift` | No external consumers found | Verify (preview-only? dead?) → delete or keep as shared base |
| `Views/Components/MultipleReadingsToastView.swift` | No external consumers found | Verify → delete or keep shared |
| `BPM/Enums/BpmConstants.swift` | Used by metrics + calculator + chart | **Stays in `BPM/`** — BPM domain constant, not chart-owned |

---

## 4. Decisions to lock (edit the **Pick** line)

**Decision A — where does product-specific chart *recipe* code live?**
- `A1 — With the product (recommended, product-first):` baby percentile math stays in `Baby/`; `Chart/` holds
  only the product-agnostic engine. Consistent with the three-peers model. *(Assumed by §2.)*
- `A2 — All under Chart/Products/<Product>/:` pull baby math into `Chart/`. Puts 100% of graph code in one tree
  but breaks product symmetry.
- **Pick:** `A1`

**Decision B — folder name for the shared engine.**
- `B1 — Chart/ (recommended):` the v2 vocabulary is `Chart*` (`ChartModel`, `ChartPrep`, `TrendChartView`).
- `B2 — Graph/:` matches `GraphView` / `DashboardGraphManager` / the `iOS:graph` skill.
- **Pick:** `B1`

**Decision C — how far to normalize?**
- `C1 — All three products (recommended):` do §3.1 **and** §3.2 so Weight/BPM/Baby are true peers.
- `C2 — Weight only:` just §3.1; leave BPM/Baby stragglers where they are (weight gets a home, asymmetry remains).
- **Pick:** `C1`

**Decision D — per-product `ViewModels/`?**
- `D1 — Yes (recommended):` each product folder gets a `ViewModels/` subfolder; move the product VMs out of the
  shared `ViewModels/`.
- `D2 — Keep VMs centralized:` product VMs stay in the shared `ViewModels/` (strict mirror of BPM/Baby today).
- **Pick:** `D1`

**Decision E — a `Shared/` folder for cross-product views?**
- `E1 — Create Dashboard/Shared/ (recommended):` move the carousel host + skeleton + `DashboardTrendView` there.
- `E2 — Leave at root:` keep them in `Views/Screens` / `Views/Components`.
- **Pick:** `E1`

---

## 5. Phased execution (each phase = build + `swiftlint --strict` clean; one PR, ordered commits)

Moves can't break Swift references within a target, so phases are for reviewability + clean git history, not
safety gates.

1. **`Weight/`** — create it, `git mv` the 4 files (§3.1). Smallest, most self-evident win; proves the
   synchronized-group flow (no `.pbxproj` edits).
2. **Complete BPM/ & Baby/** — `git mv` the 7 stragglers (§3.2). *(skip if Decision C = C2)*
3. **`Chart/`** — `git mv` the 24 engine files (§3.3); rename `Managers/Graph/` → `Chart/Engine/`.
4. **`Shared/` + cleanup** — move cross-product views (§3.4, if E1); delete confirmed orphans (§3.5); remove
   now-empty dirs (`Managers/Graph/`, `Modifiers/`, drained `ViewModels/` if fully moved).
5. **Docs** — update every doc that hard-codes old paths (§6). Enforced by the docs-freshness hook.

Verify after each move phase:
```bash
cd iOS && xcodebuild build -project meApp.xcodeproj -scheme meApp \
  -destination 'generic/platform=iOS' -configuration Dev -derivedDataPath /tmp/reorg-dd \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
cd iOS && swiftlint --strict
```

---

## 6. Docs & references to update (Phase 5)

- `iOS/docs/MOB-1516-baby-bpm-graph-migration/MOB-1516-unified-graph-how-it-works.md` — **§11 "Where each thing
  lives"** cheat-sheet + the file links throughout (point at `Models/ChartModel.swift`,
  `Managers/Graph/ChartPrep.swift`, `Views/Components/TrendChartView.swift`, `Baby/…`, `BPM/…`).
- `iOS/docs/MOB-1516-baby-bpm-graph-migration/MOB-1516-implementation-guide.md` — the `../../meApp/Features/…`
  links (§1/§3/§4/§7).
- `iOS/architecture.md` — Dashboard feature layout.
- `iOS/.claude/skills/graph/SKILL.md` — the `iOS:graph` skill's file map.
- `iOS/docs/babyapp-growth-graph-reference.md` — any baby-chart paths.
- `scripts/docs-freshness-check.sh` **+** the `update-architecture` skill map — add the new `Chart/`,
  `Weight/`, `Shared/` paths (keep the two identical, per the repo rule).

---

## 7. Prevent re-scatter (the systematic half)

1. **Convention note** — 6 lines in `iOS/CLAUDE.md` (Dashboard section) + a `Chart/README.md`: *shared chart
   code → `Chart/{Model,Engine,Views,Managers}`; a product's card / trend / recipe → `<Product>/`; a new
   product = new `<Product>/` folder + `ChartPrep.buildX` + a `DashboardChartRules` branch.*
2. **PR-review line** — "new `*SnapshotCard*` / `*TrendView*` under its `<Product>/`? new `*Chart*`/`*Graph*`
   under `Chart/`?" Added to the self-review notes.
3. **Optional lightweight guard** — a `grep` check (CI or PostToolUse hook) that flags a new `*Chart*` file
   created outside `Chart/`, or a `Weight*/Bpm*/Baby*` card/trend file created outside its product folder.
   Catches drift at authoring time.

---

## 8. Risk & non-goals

**Risk: essentially none at the code level.** No logic changes; Swift has no path imports; the target
auto-syncs from disk. Real failure modes: (a) stale doc paths (Phase 5), (b) a missed `git mv` leaving an empty
dir (Phase 4), (c) an "orphan" turning out to be referenced — re-grep before deleting. Use `git mv` (not
delete+add) so `git blame` follows the files.

**Known coupling this surfaces (pre-existing, acceptable — same target):** `Chart/Engine/ChartPrep.buildBaby`
calls `Baby/BabyDashboardChartSupport`; `GraphDataPreparer` uses `WeightlessDisplayRounding`. The engine
depending on product support already exists; product-first just makes it visible. Not worth breaking apart in a
move-only refactor.

**Non-goals:**
- Any behaviour, rendering, or performance change to the graph.
- Touching the value-type data snapshots (`AccountSnapshot`/`DeviceSnapshot`/`EntrySnapshot`).
- Refactoring `DashboardStore` / manager *internals*, or renaming the misnamed-but-shared `WeightDisplayView`
  (flag it; do it separately).

---

*Once §4 Decisions are locked, execution is mechanical: `git mv` per §3, build + lint per §5, docs per §6.
Estimated ~35 files moved, ~1–3 deleted, 0 `.pbxproj` edits.*
