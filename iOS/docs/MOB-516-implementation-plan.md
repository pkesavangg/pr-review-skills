# MOB-516 — Dashboard performance: remaining-work implementation plan

> **What this doc is.** The single, actionable execution guide for the work still open on epic
> **[MOB-516](https://greatergoods.atlassian.net/browse/MOB-516)** ("[meApp][iOS] 5.1.0 Performance Hardening")
> **after MOB-1433 shipped.** It says, per task: *what it's for, where to start, how to fix, in what order,
> how to prove it.* All file:line references were verified against the working tree on **2026-07-08**.
>
> **Read the companions for depth, this doc to execute:**
> - `performance-analysis-5.1.0.md` — full investigation + Instruments evidence (the "why").
> - `performance-remediation-plan.md` — task definitions + acceptance criteria (the "what").
> - `performance-issues-overview.md` — plain-English summary for non-engineers.
> - `MOB-1433-implementation-plan.md` — the entry fetch/sync pipeline that already shipped (the "already done").

**Platform:** iOS only (`meApp/iOS/meApp`) · **Base branch:** `develop` · **Commit/branch prefix:** `MOB-XXXX` per task.

---

## 1. Where we are (start here)

MOB-1433 delivered the **entry fetch/sync off-main pipeline**, which cleared the epic's worst symptom: the
**login → dashboard loading-screen freeze is fixed.** It also shipped, as a side effect, a large slice of the
original Task 1, Task 3, and Task 4. So the remaining MOB-516 work is **narrower and different in priority**
than the companion docs (written 2026-06-05) first framed it.

| # | Task | Status | Fixes the user-visible… | Blocks 5.1.0? |
|---|------|--------|--------------------------|:---:|
| **2** | **Chart engine** | 🔴 **Not started — DO FIRST** | graph **stutter/hang while scrolling** | **Yes** |
| **1** | Data load — incremental aggregation | 🟡 residual (off-main move done) | **slow screen-open on 10k accounts** | Yes (residual) |
| **3** | Disk writes & logging | 🟡 residual (merge batching done) | battery / disk churn during sync | No |
| **4** | Memory/CPU + MetricKit | 🟡 residual (HealthKit settings done) | memory / background kills / field data | No |

### The order, and why

```
Task 2  →  Task 1-residual  →  ( Task 3  ∥  Task 4 )
(chart)     (aggregation)        (disk)   (memory + MetricKit)
```

1. **Task 2 first.** It is entirely untouched, it is the actual "graph loading/hanging" complaint, and it is a
   **hard prerequisite for the 5.1.0 BPM and baby-growth graphs** — the baby percentile graph (5–10 series)
   lands on the one chart path that was never windowed, so shipping it on today's engine multiplies the worst
   hot path in front of brand-new users.
2. **Task 1-residual next.** The durable "stop recomputing the whole history" fix that MOB-1433 explicitly
   deferred (its §5c). Biggest remaining lever for large-account smoothness once the graph itself is fast.
3. **Task 3 ∥ Task 4 last, in parallel.** Independent, lower-risk cleanups.
4. **Wire MetricKit (part of Task 4) early if you can** — production hang/CPU stacks let you rank the
   *remaining* hangs with evidence instead of this doc's ordering.

### Ticket & PR strategy

Each numbered task becomes **its own MOB task under MOB-516**, its own branch (`MOB-XXXX-…` off `develop`),
its own PR. (Unlike MOB-1433, these are cleanly separable — don't bundle them.) Sizing / files per task are in
each section's header and in `performance-remediation-plan.md` §"Rough sizing".

---

## 2. TASK 2 — Chart engine: kill scroll stutter + make it multi-series-ready 🔴 START HERE

**What it's for.** Two hot paths run per-frame during a scroll gesture instead of once per settle. On the
weight chart it's a hitch; on the 5.1.0 baby percentile graph (5–10 lines) it multiplies into a hang. Fixing it
removes work, not features — the spec is already event-driven ("average updates when scroll *ends*").

**Where to start (files + verified lines):**
- [`Features/Dashboard/Views/Components/BaseGraphChartContent.swift`](../meApp/Features/Dashboard/Views/Components/BaseGraphChartContent.swift) — `ChartSeriesContent.visiblePoints` (`:53-65`), `percentileBoundaryExtendedPoints` (`:67-90`), `interpolatedBoundaryPoint` (`:92-133`).
- [`Features/Dashboard/ViewModels/BaseSectionViewModel.swift`](../meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift) — `visibleChartSeriesData` (`:199-231`), `isScrolling` (`:23`), y-axis compute `updateChartData` (`:393-399`), axis cache (`:733-749`).
- [`Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift`](../meApp/Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift) — `pointsToRender` (`:70-104`, the ≤200 cap + sampled buffers), `sampledBufferPoints` (`:225-252`).
- [`Features/Dashboard/Managers/Graph/GraphDataPreparer.swift`](../meApp/Features/Dashboard/Managers/Graph/GraphDataPreparer.swift) — `binarySearchFirst` (`:469`), `binarySearchLast` (`:482`) — **reuse these** (see the ⚠️ in step 2a).

### How to fix — in order

**Step 2a — Window + binary-search the percentile path (this is H1, the big one).**
Today `visiblePoints` (`:53-65`) computes `pointsToRender` for *every* series and then **throws it away** for
percentile series (`:60-64`), returning `percentileBoundaryExtendedPoints`, which does:
- an O(n) `.filter` over the whole series for the visible grid range (`:71`), rendering **all** in-range points
  with **no 200-cap sampling**;
- O(n) `last { $0.xDate < boundary }` / `first { $0.xDate > boundary }` neighbor scans (`:100-101`).

Fix:
1. Route percentile series through `pointsToRender` too, so a baby graph never renders more than the
   visible + sampled buffer set (the ≤200 discipline the weight chart already has).
2. Replace the `.filter` (`:71`) with a **binary-search slice** over the sorted points, and the
   `last/first { … }` neighbor scans (`:100-101`) with binary search. Points arrive **sorted by date** —
   `makeCacheUpdate` sorts each series (`BaseGraphViewCacheSupport.swift:38-40`) and preserves that order into
   `plottedPoints` (`:53-57`) — so binary search is valid.
3. Keep the boundary-interpolation behavior (`interpolatedBoundaryPoint`, `:92-133`) so percentile lines still
   draw cleanly to the visible grid edges — just feed it neighbors found by binary search over the windowed set.

> ⚠️ **`binarySearchFirst`/`binarySearchLast` are typed to `[BathScaleWeightSummary]`** (`GraphDataPreparer.swift:469-493`),
> not generic — they **cannot** be used on `[PlottedGraphSeries]` (which keys on `.xDate`, not `.date`) as-is.
> Either (a) generalize them to `func binarySearch…<T>(in: [T], where: (T) -> Bool) -> Int?` and update the
> existing call sites (`:404-405,420-421,437-459`), or (b) add a small parallel variant over `[PlottedGraphSeries]`.
> Prefer (a) — one generic helper, no duplication.

**Step 2b — Re-key the visible-window cache so it survives a scroll (this is H3).**
`visibleChartSeriesData` (`:199-231`) caches by raw `scrollPosition` (`:211`). `scrollPosition` changes every
frame, so the key never matches mid-scroll and the `.filter` at `:222` re-runs **every frame**.
Fix: key on a **quantized scroll bucket + dataHash** instead of the raw position — e.g.
`floor(scrollPosition.timeIntervalSinceReferenceDate / bucketWidth)`, with `bucketWidth` a small fraction of
`visibleDomainLength`. Store `lastVisibleBucket` alongside `lastVisibleDataHash`; recompute only when the bucket
or data hash changes. Result is identical at rest; it just stops recomputing 60×/s while the window sits in the
same bucket.
*(Note: the store-level fetch is already scroll-guarded — `chartSeriesData` `:158-166` reuses `cachedChartSeriesData`
during scroll when the metric is unchanged. This step is only about the `visibleChartSeriesData` filter.)*

**Step 2c — Confirm window-average / y-axis recompute only on scroll-END (this is the spec, H4-spec).**
The y-axis domain/ticks are computed in `updateChartData` (`:393-399`) with an axis cache (`:733-749`); the
`isScrolling` flag exists (`:23`) and flips false at `:461`. **Verify** the window-average and dynamic y-axis
derive from the settled window (recompute gated on `isScrolling == false`), not from `scrollPosition` per frame.
If any per-frame recompute is found on the average/axis path, gate it behind `!isScrolling`. This is a
verify-and-tighten step, not a rebuild — likely small.

### Tests (add to `meAppTests/Features/Dashboard/…`)
- **Point-set parity:** for a known weight dataset **and** a known baby percentile dataset, the produced
  visible/render point set matches current output (downsampling preserves shape).
- **Boundary correctness:** binary-search neighbors == linear-scan neighbors for random boundaries; interpolation
  value unchanged.
- **Cache-hit ratio:** during a scripted scroll across one bucket, `visibleChartSeriesData` recomputes ≤1×
  (assert a computed-count counter), and re-filters when the bucket changes.
- **Scroll-end:** average/y-axis settle exactly once when `isScrolling` goes false.

### Regression guard (behavior parity — non-negotiable)
Downsampling must preserve line **shape** *and* keep enough data that the **crosshair still snaps to a real
entry**. QA script: scroll all periods (week/month/year/total) on weight + baby + BPM; confirm the average
settles on finger-lift, the y-axis matches the visible window, and the crosshair selects a real point.

### Done when
Animation Hitches trace (Profile build, physical device) on a **5k-entry weight** account **and** a **baby
percentile** account shows steady-state scroll **< ~5 ms/s hitch, no frame > 16.7 ms.**

### 2.1 Execution log & open decision (MOB-518, branch `MOB-518-chart-engine-scroll-hitch-multi-series`) — 2026-07-08

**Step 2a (H1) — DONE.**
- Added a generic O(log n) `SortedArrayIndex.first/last(in:where:)` helper (`GraphDataPreparer.swift`, top-level);
  the existing `[BathScaleWeightSummary]` `binarySearch*` methods now delegate to it (one algorithm, no duplication).
- Extracted the percentile windowing out of `ChartSeriesContent` into a pure, unit-testable
  `enum PercentileChartWindowing` (`BaseGraphChartContent.swift`) and replaced the O(n) `.filter` slice +
  `last/first(where:)` neighbour scans with binary search. **Output is byte-identical** (parity pinned by
  `PercentileChartWindowingTests` against a verbatim copy of the old logic).
- **Deviation from the written plan (justified):** the plan said to *also* route percentile series through
  `pointsToRender`'s ≤200-point cap. Verified in the tree that percentile curves are **already downsampled to
  ~150 points at generation** (`BabyPercentileGrowthReference.swift:135-137`; `heightPercentileSeries` step-to-150),
  so that cap can never fire — and percentile lines render across the wider `visibleGridRange`, not the scroll
  window, so forcing them through `pointsToRender` would *regress* the edge-to-edge curve. The real per-frame cost
  was the linear scans, which binary search removes. No cap added (documented inline).

**Step 2c (H4-spec) — VERIFIED, no code change.** The window average / y-axis / metrics recompute is already
scroll-end-gated: `updateYAxisConfiguration()` hard-guards `!isScrolling` (`BaseSectionViewModel.swift:355`),
`handleScrollPositionChange` only buffers the position during a gesture, and `handleScrollEndOptimized`
(`DashboardChartManager.swift:227-264`) + `handleScrollPhaseChange(.idle)` drive the settle-time recompute.

**Step 2d (W1 + W2, runtime warnings) — DONE, compiles; awaiting device confirm.** Full write-up in
[`MOB-518-weight-graph-focus.md`](MOB-518-weight-graph/MOB-518-weight-graph-focus.md) §3.5 + §4.3.
- **W1 (scroll update storm)** — `Publishing changes from within view updates` + `onChange(of:
  ChartScrollPositionConfiguration) … multiple times per frame`. Root: `scrollPosition` was `@Published`, and the
  `.chartScrollPosition` binding writes it *on the view-update pass* → re-entrant re-render every frame. Fix:
  **de-`@Published` `scrollPosition`** (`BaseSectionViewModel.swift`) + a `scrollAdoptToken` (`@State` in
  `BaseGraphView`) for programmatic-scroll adoption re-renders + deleted both `+0.001` nudge hacks. During a drag
  the body no longer re-runs per frame (Charts scrolls natively) — the 10k-smoothness win. (An earlier Option B
  attempt kept the `@Published` write and failed on device — reverted.)
- **W2 (`Invalid frame dimension` flood)** — degenerate/non-finite domains reaching `.chartYScale`/`.chartXScale`
  (partly downstream of the W1 thrash). Fix: new pure `ChartDomainSanitizer` (`Managers/Graph/`) routes every
  Charts scale/visible-domain input through finite + positive-width guards; `safeYAxisDomain` feeds both the scale
  and the series clamp. Unit-tested (`ChartDomainSanitizerTests`).
- **Build:** app + `build-for-testing` both green (Dev, `generic/platform=iOS`). Device verification checklist:
  `MOB-518-weight-graph-focus.md` §3.3.

**Step 2b (H3) — OPEN DECISION for Kesavan.** The target property `visibleChartSeriesData`
(`BaseSectionViewModel.swift:201`, its cache fields `:195-197`, and the `SectionViewModelProtocol` requirement
`:45`) is **dead for rendering** — grep across the whole iOS tree shows the *only* readers are unit tests
(`BaseSectionViewModelTests`, `TotalSectionViewModelTests`). The render path migrated to
`cachedPlottedPoints`/`makeCacheUpdate`, orphaning it. So "re-key its cache on a quantized bucket" (the plan's
Step 2b) would optimise a property that never runs during a real scroll. Pick one (edit inline):

- [ ] **A — Remove the dead property (recommended).** Delete `visibleChartSeriesData` + its cache fields
  (`cachedVisibleSeriesData`/`lastVisibleScrollPosition`/`lastVisibleDataHash`) + the protocol requirement +
  the ~4 tests that exercise it. Removes the maintenance trap that made this analysis waste effort on a "hot
  path" that isn't hot. Prevents the next reader from re-chasing it (the systematic fix).
- [ ] **B — Re-key it anyway** on a quantized scroll bucket + dataHash (as originally written), in case it is
  re-wired into the render path later. (Optimises code nothing currently calls.)
- [ ] **C — Leave as-is**, just document that it is orphaned.

*Default if unanswered: I hold at C (no deletion of a tested protocol API without your call) — 2a already delivers
the live win; 2b is cleanup either way.*

---

## 3. TASK 1 (residual) — Data load: stop re-reading the whole history 🟡

**What it's for.** On a 10k-entry account, opening History / re-appearing on Dashboard is still slow because the
app re-fetches + re-maps the *entire* create history every load, and streak/progress re-scan the whole table —
even when nothing changed. MOB-1433 moved this **off the main actor** (so it no longer freezes the loading
screen) but did **not** stop it from running; it now shows up as worker↔main-context store contention
(≈4.9 s of `loadDashboardData` worker time on 10k, per MOB-1433 §5c). This is the durable fix MOB-1433 deferred.

**Where to start:**
- [`Data/Services/EntryService.swift`](../meApp/Data/Services/EntryService.swift) — `performDashboardDataLoad` (`:2137-2191`), `getAllEntriesAsDTO` (`:347-353`), `getStreak` (`:732-765`), `getProgress` (`:472-558`), `handleEntryAdded` (`:2194-2240`), `updateDailySummary`/`updateMonthlySummary` (`:2401-2432`), in-memory summary arrays (`:51-62`), signature caches (`:83-86`), `fetchBabyEntrySnapshots` (`:1870-1886`, **still main-actor**).
- [`Core/Services/SwiftDataWorker.swift`](../meApp/Core/Services/SwiftDataWorker.swift) / [`Data/Storage/DB/EntryRepository.swift`](../meApp/Data/Storage/DB/EntryRepository.swift) — cheap `fetchEntryCount` + `fetchLatestEntry` for the signature.

### How to fix — in order (lowest-risk first)
1. **Cheap signature gate *before* the fetch.** Today the dataset signature is computed **after** the full
   fetch+map (`:2152-2185`), so the map always runs. Compute a signature from **cheap indexed queries** —
   `fetchEntryCount(forUserId:)` + latest-entry timestamp (`fetchLatestEntry`) + a metric-selection token —
   **before** calling `getAllEntriesAsDTO`. On a hit, return the cached in-memory summaries and skip the fetch
   entirely. No schema change. Biggest bang for the buck.
2. **Move the baby load off the main actor.** `fetchBabyEntrySnapshots` (`:1870-1871`) still uses the
   main-actor `localRepo.fetchEntries(forUserId:operationType:)` and filters by babyId in memory — route it
   through `SwiftDataWorker` like the weight/BPM paths. (Directly relevant to the 5.1.0 baby graph.)
3. **Incremental streak/progress (the big one).** `getStreak` (`:733`) and `getProgress` (`:476`) each do their
   own full-history fetch on a cache miss. Maintain running totals in `handleEntryAdded` / the delete path so a
   miss doesn't re-scan all 10k. If this needs a **persisted** summary to also cover the cold/first load, that's
   a new `@Model` + lightweight migration — the larger, higher-risk sub-task; scope it separately.

> **Note:** `DailyWeightSummary` (listed in `CLAUDE.md`) **does not exist in code** — daily/monthly summaries
> are in-memory `@Published` arrays only (`EntryService.swift:51-62`). Any "read the summary table" instinct is
> wrong; either cache in memory (step 1) or introduce the persisted model deliberately (step 3).

### Tests / Done when
Summaries via the gated/incremental path are **byte-identical** to the full-map path for a fixed dataset;
add/delete/edit/unit/weightless/account-switch all still update correctly. **Done when:** a redundant
`loadDashboardData` on an unchanged 10k dataset does **no** full-history fetch, and History/Dashboard open on
10k no longer stalls behind a full worker read.

### Constraint
Must keep returning **DTOs/snapshots, never `@Model`,** across the actor boundary (SwiftLint
`no_published_swiftdata_model` + `check-snapshot-boundary.sh`).

---

## 4. TASK 3 — Disk writes & logging 🟡

**What it's for.** ~8× disk-write regression + spiky sync hangs. The remote-merge batching already landed in
MOB-1433; what remains is the **log-write storm** and one local bulk path.

**Where to start:**
- [`Core/Services/LoggerService.swift`](../meApp/Core/Services/LoggerService.swift) — persist logic (`:60-61, 77, 87`).
- [`Data/Storage/DB/LoggerRepository.swift`](../meApp/Data/Storage/DB/LoggerRepository.swift) — `saveLogEntry` per-row insert+save (`:57-58`).
- [`Data/Services/EntryService.swift`](../meApp/Data/Services/EntryService.swift) — `saveNewEntries` per-row loop (`:227-237`).

### How to fix
1. **Persistence log-level floor + batching.** Logging persists everything except `.debug` (`:60-61`) with one
   `ctx.insert` + `ctx.save()` per line (`LoggerRepository.swift:57-58`). Add `persistenceMinimumLogLevel`
   (default `.warning`) in `LoggerService` so `.info`/`.success` don't hit disk, and buffer rows + flush in
   batches (N rows / on interval) instead of one save per line. (MOB-1433 moved the save to a background
   context — it's off-main now, but still one transaction per line.)
2. **Batch `saveNewEntries` (optional).** Still per-row (`:227-237`); route through the worker's batched
   `insertEntries` if a disk trace flags bulk local imports. Low priority.

### Done when
Organizer **Disk Writes trend toward < 5 MB/day**; warnings/errors still persisted.

---

## 5. TASK 4 — Memory/CPU residuals + MetricKit 🟡

**What it's for.** Trim the remaining peak-memory / background-termination pressure and, crucially, get
**production hang stacks** so the *remaining* hangs are ranked by evidence. HealthKit's settings-read-once +
forward-marker already landed in MOB-1433.

**Where to start:**
- [`Data/Services/HealthKitService.swift`](../meApp/Data/Services/HealthKitService.swift) — `syncAllData` unbounded fetch (`:225-228`).
- [`Data/Services/AccountService.swift`](../meApp/Data/Services/AccountService.swift) — `makeOtherAccountsInactive` (`:1522-1528`, N-1 sequential writes `:1524-1526`), `$activeAccount` sink (`:56-62`).
- New `Core/Services/MetricKitService.swift` + `ServiceRegistry`/AppDelegate registration.

### How to fix
1. **HealthKit sync bound.** The `FetchDescriptor<Entry>` in `syncAllData` (`:225-228`) is still unbounded —
   add `fetchLimit` + paging + `autoreleasepool` per page + **delta sync** keyed on a stored last-sync
   timestamp. (Writes are already committed in 1000-entry chunks off-main.)
2. **Account-switch fan-out.** `makeOtherAccountsInactive` does N-1 sequential `updateAccountClearingTokens`
   writes (`:1524-1526`) — batch into one transaction. Add `.removeDuplicates()` to the `$activeAccount` sink
   (`:56-62`) so a switch doesn't re-register ~8–12 session services twice. Verify the product-types migration
   has a run-once guard.
3. **MetricKit.** Add an `MXMetricManagerSubscriber` service (hang + CPU-exception diagnostics), wire it in
   `ServiceRegistry`/AppDelegate, log/upload payloads.

### Done when
Allocations across a sync show bounded retention; Peak Memory trends **< ~160 MB**; MetricKit payloads arriving.

---

## 6. Build, test, verify

```bash
# Build — Dev config ONLY (no "Debug" config exists; -configuration Debug silently builds Production)
cd iOS && xcodebuild build -project meApp.xcodeproj -scheme meApp \
  -destination 'generic/platform=iOS' -configuration Dev \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO

# Unit tests — MUST run on a connected physical device, never a simulator (project rule)
xcodebuild -project meApp.xcodeproj -scheme meAppTests -showdestinations 2>&1 | grep "platform:iOS," | grep -v Simulator
cd iOS && xcodebuild test -project meApp.xcodeproj -scheme meAppTests -configuration Dev -destination 'id={DEVICE_ID}'
```

**Epic-level gate (MOB-516):** on a ≥4,000-entry account (Profile build, physical device) — **no main-thread
block > 250 ms** on load, refresh, account switch, or scroll; steady-state scroll hitch < ~5 ms/s. Capture
before/after Instruments **Animation Hitches + Time Profiler** `.trace` per task (keep traces short/targeted;
never commit `.trace` bundles).

**Coverage gates:** Data/Services 80% (85% auth/account/sync), ViewModels 80%, Forms 85%. Test files are
mandatory, not optional.

---

## 7. Quick reference — every hotspot, current line, status

| Tag | Hotspot | Current location | Status |
|-----|---------|------------------|--------|
| H1 | Percentile path not windowed | `BaseGraphChartContent.swift:60-64, 71, 100-101` | 🔴 Task 2 |
| H3 | Visible cache keyed on raw scrollPosition | `BaseSectionViewModel.swift:211, 222` | 🔴 Task 2 |
| — | Binary-search helper (reuse; generalize) | `GraphDataPreparer.swift:469, 482` | reuse |
| — | `pointsToRender` (≤200 cap, keep) | `BaseGraphViewCacheSupport.swift:70` | ✅ leave |
| H2 | Full-history fetch+map every load | `EntryService.swift:2137-2191, :347-353` | 🟡 off-main done; gate-before-fetch open (Task 1) |
| — | Streak/progress full-scan on miss | `EntryService.swift:733, :476` | 🟡 Task 1 |
| — | Baby load on main actor | `EntryService.swift:1870-1871` | 🟡 Task 1 |
| — | `#Index<Entry>` (keep) | `Entry.swift:23-28` | ✅ shipped MOB-1433 |
| — | Off-main DTO map (keep) | `SwiftDataWorker.swift:229-242` | ✅ shipped MOB-1433 |
| H4a | Per-line log insert+save, no floor | `LoggerRepository.swift:57-58`, `LoggerService.swift:60-61` | 🔴 Task 3 |
| H4b | Merge batching | `SwiftDataWorker+EntryMerge.applyRemoteOperations` | ✅ shipped MOB-1433 |
| — | `saveNewEntries` per-row | `EntryService.swift:227-237` | 🟠 Task 3 (opt) |
| H5 | HealthKit unbounded fetch | `HealthKitService.swift:225-228` | 🟡 Task 4 (settings/marker done) |
| H6 | Account-switch N-1 writes + re-register | `AccountService.swift:1524-1526, :56-62` | 🟡 Task 4 |
| — | MetricKit | absent | 🔴 Task 4 |

*Line numbers verified 2026-07-08 on `develop`. Re-grep the symbol if a number drifts after later commits.*
