# 5.1.0 Performance Analysis & Hang-Reduction Playbook (iOS)

**Author:** Engineering (perf investigation)
**Date:** 2026-06-04
**Scope:** iOS only (`meApp/iOS/meApp`). Metrics source is **Xcode Organizer**, which is iOS-only.
**Status:** Investigation (2026-06-04) complete. **Reconciled 2026-07-08 after MOB-1433 shipped** (see banner). Epic = **[MOB-516](https://greatergoods.atlassian.net/browse/MOB-516)**.

> **Every file:line below was read in the working tree and confirmed.** Where an earlier pass mis-attributed a hotspot, this version corrects it (see [§4](#4-what-is-already-optimized-dont-redo-this)) — fixing already-fast code wastes effort and risks regressions.

> ⚠️ **Reconciliation note (2026-07-08).** This analysis was captured *before* [MOB-1433](https://greatergoods.atlassian.net/browse/MOB-1433) (the entry fetch/sync off-main pipeline) shipped. What that changed for this doc:
> - **H2 (the proven #1 hang) is no longer a main-thread hang.** `Entry.toOperationDTO()` now runs on a `@ModelActor` worker off the main actor, `Entry` has `#Index`, relationships are prefetched, and a signature cache short-circuits the *aggregation*. The **loading-screen freeze is fixed.** The 2.2 s cost did **not** disappear — it moved off-main and now shows up as worker↔main-context store contention on 10k accounts (MOB-1433 §5c). See H2 for the corrected framing.
> - **H1 (percentile chart path) is now FIXED — shipped in MOB-518 (PR #2237), after this reconciliation was written.** The percentile branch routes through `PercentileChartWindowing.boundaryExtendedPoints(...)` using binary search (`SortedArrayIndex`, commit `813f0f98a`), replacing the O(n) `.filter` + `last/first(where:)` scans; a v2 weight-graph engine also landed. **H3 (visible-window cache keyed on raw `scrollPosition`) is still valid and untouched** — MOB-518 did not re-key `visibleChartSeriesData` (`BaseSectionViewModel.swift`), so that remains the one open chart-engine item. Current line refs: H1 `BaseGraphChartContent.swift:60-64,71,100-101` (now superseded by `PercentileChartWindowing`); H3 `BaseSectionViewModel.swift:209,219`.
> - **H4 merge-batching, HealthKit settings/marker: done in MOB-1433.** H4 logging, H5 HealthKit fetch-bound, H6 account switch: still open. See each section for the ✅/🔴 marker.
> - Numbers in §1 are the 5.0.x Organizer capture; they have **not** been re-measured post-MOB-1433 (§9 still applies).

---

## 0. TL;DR

- The 5.0.x metrics are real and corroborated by user reviews (graph "lag", broken scroll). See [§2](#2-evidence).
- **PROVEN — one root cause behind three regressions (Instruments, 4k account):** `Entry.toOperationDTO()` mapping all entries on every dashboard load is the same code that causes (1) the **load hang** (2.2 s of SwiftData per-property overhead, ×3 across product types), (2) the **memory churn** (56M transient allocations / 8.75 GiB churned, climbing resident memory), and (3) the **#1 "Processing" battery** drain. Fix this one thing → fix all three. See [§5 H2](#5-verified-remaining-hotspots-the-real-targets).
- **Most of the regression is fixable without losing any functionality.** The functionality you need (scrollable graph, window-average on scroll-end, dynamic y-axis, live update on add/delete/unit/weightless) is *event-driven and cheap*. The current code is *frame-driven and expensive*. Fixing it means doing **less, at the right moment** — which is also exactly the behavior spec'd. See [§3](#3-is-this-inherent-or-fixable-the-direct-answer).
- The team has **already shipped meaningful optimizations** (scroll throttle, axis cache, point windowing, off-main aggregation, incremental entry updates). The remaining hangs come from a **small, specific set** of hot paths, not a "slow by design" rewrite. See [§4](#4-what-is-already-optimized-dont-redo-this) and [§5](#5-verified-remaining-hotspots-the-real-targets).
- **5.1.0 risk:** the new BPM (2-series) and especially baby percentile (5–10 series) graphs run on the *one* chart path that was **never** windowed. Shipping them on today's code multiplies the worst hot path. Fix the chart engine before/with the product-type graphs. See [§6](#6-why-510-makes-it-worse).

---

## 1. The numbers

| Metric (90th pct, iPhone All) | 4.x baseline\* | 5.0.x latest | Change | Verdict |
|---|---|---|---|---|
| Scroll Hitch Rate | ~3–5 ms/s | **32.6 ms/s** | ~8–10× | 🔴 Critical |
| Hang Rate | ~30 s/hr | **51.4 s/hr** | +22–30% | 🔴 Critical |
| Peak Memory | ~135 MB | **238.8 MB** | +57% | 🔴 Critical |
| Memory at Suspension | ~55 MB | **97.6 MB** | bucket +10–24% | 🟠 High |
| Disk Writes | ~3 MB/day | **26.1 MB/day** (peaked 90 in 5.0.0) | ~8× | 🟠 High |
| Battery — "Processing" | minor | **#1 category, 0.35%/day** | +46% regression | 🔴 Critical |
| Background Terminations | <1/day | **3.24/day, 99% system pressure** | regression | 🟠 High (memory-linked) |
| Launch Time | ~550 ms | **450 ms** | improved | 🟢 OK |

\* 4.x baselines are read off the Organizer bar charts (pixel-level), so treat the % as directional, not exact. The 5.0.0 step-change is unmistakable in every chart.

---

## 2. Evidence

**App Store aggregate (App ID 526207936, fetched 2026-06-04):** Weight Gurus, v5.0.2, **4.46★ / 1,010 ratings.** The aggregate is healthy only because it's diluted by years of pre-5.0 ratings — recent written reviews are uniformly negative on the redesign:

- ★☆ (May 6): *"The lag between clicking and info display is unacceptable"*; can't scroll along timelines.
- ★★ (Apr 30): *"you can no longer drag the line cursor across the graph to scroll data points"*; graphs "harder to read."
- ★★ (May 15): graphs "harder to read, less intuitive."

"Lag between clicking and info display" and "can't drag the cursor across the graph" are lay descriptions of the **32.6 ms/s hitch** and **51.4 s/hr hang** — telemetry and humans point at the *same feature*.

> ⚠️ **Reviews limitation (honest):** Apple's public customer-reviews RSS now returns an **empty entry set** for this app (verified across US/GB/CA/AU) — effectively deprecated. The three reviews above are scraped from the public storefront and are **not** a representative sample. A real pull needs the **App Store Connect API** (`customerReviews`, JWT). The aggregate (4.46★/1,010) is reliable.

> ⚠️ **Metrics lag (honest):** 5.0.2 shipped ~10 days before capture and several optimizations (§4) landed across 5.0.1/5.0.2. Organizer percentiles trail adoption, so part of the 51 s/hr may reflect 5.0.0 users who haven't updated. Don't assume "current `main` still hangs this much" without a fresh capture — see [§9](#9-what-we-still-need).

---

## 3. Is this inherent, or fixable? — the direct answer

**Fixable. Almost none of it is inherent to the functionality.** Look at the spec you actually need:

| Required behavior | Cadence it *needs* | Cadence the code *uses today* | Right cost @ 10k entries |
|---|---|---|---|
| Scrollable graph | continuous, but **render-only** | recomputes data per frame | ~0 if nothing computes during the gesture |
| Window average updates **on scroll end** | **event** (scroll end) | n/a (already deferrable) | O(1) with prefix sums, or O(visible) once |
| Y-axis from window values | **event** (window change) | recomputed on scroll | O(visible) once per settle |
| Title / unit / weightless change | **event** | event ✓ | O(visible) once |
| New entry / deletion → update | **event** | event ✓ (already incremental — §4) | O(day+month), tiny |
| 10k entries | only matters on **load / total view** | full-table fetch on main; non-windowed percentile path | bounded with paging + windowing + binary search |

The key insight: **your own spec is event-driven** ("update when scroll *ends*", "update on add/delete"). Anything still doing per-frame work is doing *more* than the spec asks. Fixing it removes work, not features.

**Is it "bad Swift concurrency" or "bad code"?** Mostly architecture/algorithm, with a concurrency tail. In order of blame:
1. **Cadence** — some work runs per-frame instead of per-event. (architecture)
2. **Algorithm** — O(n) linear scans on already-sorted data; non-windowed percentile path. (algorithm)
3. **Main-actor fetches** — full-table reads materialized on `@MainActor` before any work can offload. (concurrency)
4. **Disk** — per-row `save()` instead of batched transactions. (mechanical)

**Regression risk lives in execution, not in feature loss.** The two places to be careful: (a) downsampling must preserve line shape *and* keep full data for crosshair selection so taps still snap to a real entry; (b) cadence changes can cause stale-window / one-frame-flicker bugs. Both are test-covered, not feature trade-offs.

---

## 4. What is already optimized (don't redo this)

> **➕ Shipped in MOB-1433 (2026-07-08) — do not redo:** entry fetch/DTO+snapshot map moved **off the main actor** onto the `@ModelActor` `SwiftDataWorker` (`fetchEntriesAsDTO`/`fetchAllEntryData`, `SwiftDataWorker.swift:229-261`) with **relationship prefetch**; **`#Index<Entry>`** added (`Entry.swift:23-28`); **dataset-signature caches** over aggregation/streak/progress (`EntryService.swift:2152-2185`, `:734-756`, `:478-484`); the remote merge is **batched off-main** (`SwiftDataWorker+EntryMerge.applyRemoteOperations`, chunked 500/save) and the **push is chunked ≤100**; the **loading screen is un-gated** from the sync; HealthKit **reads settings once/sync + gates historical backfill by a marker**. See `MOB-1433-implementation-plan.md`. The items below were already present *before* MOB-1433.

Reading the actual code, the team has shipped real perf work. **These are NOT problems — leave them alone:**

- **Scroll throttling** — `BaseSectionViewModel.swift:57-59` throttles scroll-position updates to ~16 ms (60 fps).
- **X-axis tick caching** — `BaseSectionViewModel.swift:250-289` caches axis ticks with a 1 s scroll tolerance; invalidated on period/data change.
- **Weight-chart point windowing** — `BaseGraphViewCacheSupport.pointsToRender` (`:70`) returns all points only when ≤200; above that it partitions into visible + *sampled* left/right buffers, so the weight line renders a bounded point count. (It's a single O(n) pass, not multi-scan.)
- **Pre-plotted points** — `BaseGraphView.cachedPlottedPoints` (`:31`) precomputes `plotXDate` per point so it isn't recomputed per frame.
- **Off-main dashboard aggregation** — `EntryService.performDashboardDataLoad` (`:2024`) runs grouping/aggregation in `Task.detached(.userInitiated)` (`:2041`) with a **dataset signature cache** that skips re-aggregation when nothing changed (`:2044-2046`).
- **Incremental entry updates** — `EntryService.handleEntryAdded` (`:2081`) fetches **only the affected day + month** (`getEntries(forDay:)`/`(forMonth:)`, which use scoped repo queries — `:344-348`, `:338-342`) and aggregates just those. *"New entry should update"* is already cheap. ✅
- **Task de-duplication** — `activeDashboardLoadTasks` / `activeBabyDashboardLoadTasks` collapse concurrent loads (`:2006-2020`).
- **Subscription teardown** — `EntryService` (`deinit :2472-2475`) and `AccountService` (`deinit :1495-1497`) cancel Combine subscriptions. The earlier "subscriptions never cancelled" claim was **wrong**; corrected here.
- **Snapshot boundary** — `@Model` never crosses the actor boundary (DTOs/snapshots do), which is why off-main aggregation is safe.

> The takeaway: this is **not** "the rewrite is slow by design." It's a handful of specific hot paths that escaped the optimization sweep — most of them on the chart's selection/percentile/total paths and the data-load fetch.

---

## 5. Verified remaining hotspots (the real targets)

### H1 ✅ Baby/percentile chart path — FIXED in MOB-518
**File:** `Features/Dashboard/Views/Components/BaseGraphChartContent.swift` · **Status: ✅ shipped in MOB-518 (PR #2237, commit `813f0f98a`).** The percentile branch now delegates to `PercentileChartWindowing.boundaryExtendedPoints(...)`, which uses binary search over the sorted points via the generic `SortedArrayIndex.first/last(in:where:)` helper — replacing the old O(n) `.filter` slice + `last(where:)`/`first(where:)` neighbour scans described below. (Per the MOB-518 execution log, percentile curves are already downsampled to ~150 points at generation, so no additional `pointsToRender` cap was added; output is parity-pinned by `PercentileChartWindowingTests`.) The description below is the original pre-fix analysis, kept for the trail.
The percentile branch bypasses `pointsToRender` and instead:
```swift
let pointsInGridRange = points.filter { $0.xDate >= ... && $0.xDate <= ... }   // :71  O(n)
...
let previousPoint = points.last { $0.xDate < boundary }                        // :100 O(n)
let nextPoint     = points.first { $0.xDate > boundary }                       // :101 O(n)
```
Runs **once per percentile series** (5–10 for baby) **per render**, and renders *all* in-range points (no 200-cap sampling). `points` is sorted by date → these should be binary search + slice. **This is the path 5.1.0's baby graph lands on.** Highest priority.

### H2 🟡 The dashboard load maps every entry to a DTO — was the proven #1 hang; MOB-1433 moved it off-main
**Status (2026-07-08):** 🟡 **Partly addressed by MOB-1433.** The map now runs on the `@ModelActor` worker (`getAllEntriesAsDTO` → `SwiftDataWorker.fetchEntriesAsDTO`, `SwiftDataWorker.swift:229-242`), `Entry` has `#Index` (`Entry.swift:23-28`), relationships are prefetched (`:238`), and a signature cache short-circuits the *aggregation* (`EntryService.swift:2152-2185`). **The loading-screen main-thread hang is gone.** What remains: the full-history fetch+map still runs on *every* load (the signature gates aggregation, **not** the fetch), so on a 10k account it reappears as worker↔main-context **store contention** (~4.9 s of `loadDashboardData` worker time, MOB-1433 §5c) rather than a main-thread block — this is the deferred incremental-aggregation work (Task 1 residual). The Instruments evidence below is the *original pre-MOB-1433* capture; kept for the trail.
**File (original, pre-MOB-1433):** `EntryService.swift:2028` (`getAllEntriesAsDTO`) → `EntryRepository.fetchEntriesAsDTO` → `Entry.toOperationDTO()`. **Current:** `EntryService.getAllEntriesAsDTO` `:347-353` → `worker.fetchEntriesAsDTO` (off main).

> **Confirmed by Instruments (2026-06-04, Animation Hitches/Responsiveness, physical device, 4,000-entry account).** Recording the loading screen showed **near-continuous "Severe Hang" across 53 s**, including individual hangs of **3.82 s, 3.33 s, 3.32 s, 1.72 s**. Drilling the 00:29–00:33 hang window (Time Profiler call tree, inverted, system libs hidden) gave a definitive stack:
> ```
> loadDashboardData → performDashboardDataLoad
>   → EntryRepository.fetchEntriesAsDTO  (performBackgroundTask)   2.71s / 93%
>     → Collection.map                                            2.21s / 76%
>       → Entry.toOperationDTO()                                  2.20s / 76%
>         → ObservationRegistrar.access / PersistentModel.getValue(forKey:) / _swift_getKeyPath
> ```

**Corrected diagnosis (an earlier draft was wrong):** the fetch+map runs **off the main actor** inside `performBackgroundTask` — the team did that correctly. The problem is threefold:

1. **`toOperationDTO()` is expensive per row.** Every property read on a SwiftData `@Model` goes through `ObservationRegistrar.access` + `PersistentModel.getValue(forKey:)` + keyPath resolution (`_swift_getKeyPath`). ~4,000 entries × ~20 fields (+ `scaleEntry`/metrics relationships) = hundreds of thousands of dynamic accesses → **2.2 s**. The main thread **hangs because it blocks waiting** on this background task before the dashboard can render.
2. **The signature cache runs *after* the map** (`:2044`), so the expensive conversion happens **every time** before the cache can short-circuit it.
3. **It repeats per `entryType`.** `getAllEntriesAsDTO` fetches+maps *all* entries then filters by type, so loading scale + BPM + baby = **3× this map**. For 5.1.0's three product types, 2.7 s → ~8 s. This is the concrete mechanism behind [§6](#6-why-510-makes-it-worse).

`getMonthsAll`/`getMonthYear` (used by `getProgress`, `:416`) share the same raw-entry materialization pattern (`:354`, `:382`).

**This is the proven #1 hang.** A 4,000-entry account (not even 10k) freezes for multiple seconds on load.

> **Same root cause also drives memory + battery (Allocations trace, 2026-06-04, 4k account, 3:44 session).** The summary showed **~56.6M transient allocations**, **~8.75 GiB total churned** (vs. only ~42 MiB persistent), and a **steadily climbing resident-memory line** that never came back down. That churn is the per-property `getValue`/keyPath/Observation machinery firing hundreds of thousands of times per load — the same `toOperationDTO` path. It explains the **+57% Peak Memory / OOM terminations** and the **#1 "Processing" battery** category simultaneously. **One fix (P1.1) addresses the hang, the memory churn, and the battery drain together.**
>
> *(The full allocation call tree could not be extracted: the 3:44 recording produced a 5.9 GB un-indexed event stream that Instruments never finalized — see [§9](#9-what-we-still-need) for the recording-methodology lesson. The summary numbers above are sufficient; the call tree would only re-confirm `toOperationDTO`.)*

### H3 🟠 `visibleChartSeriesData` cache is keyed on exact `scrollPosition` (dead during scroll)
**Status: 🟠 still valid — untouched by MOB-1433 AND by MOB-518. This is the one remaining chart-engine (Task 2) item.** MOB-518 shipped H1 + the v2 weight-graph engine but did **not** re-key `visibleChartSeriesData`; note the MOB-518 log also flags this property as effectively dead for rendering (only unit tests read it), so re-keying vs. removing is an open call — see `MOB-516-implementation-plan.md` §2.1 Step 2b.
**File:** `BaseSectionViewModel.swift:199-231` (key check `:211`, per-frame `.filter` `:222`)
```swift
// "Result is cached by (scrollPosition, dataHash)"   ← comment claims an optimization the code doesn't deliver
if scrollPosition == lastVisibleScrollPosition && lastCacheUpdateHash == lastVisibleDataHash && ... { return cached }
...
let filtered = data.filter { ... }                    // :222  O(n) every scroll frame
```
`scrollPosition` changes every frame, so the key never matches mid-scroll → the `.filter` re-runs every frame. Confirm it's on the hot path (it feeds window-derived values like the average/y-axis); if so, re-key on a **quantized window bucket + dataHash**, not raw position.

### H4 🟠 Per-row `save()` storms (disk writes + main-thread churn)
- **Logging:** 🔴 *still open.* `LoggerRepository.saveLogEntry` (`:32-63`) still does `ctx.insert; try ctx.save()` **per log line** (`:57-58`); `LoggerService` (`:60-61, 77, 87`) persists everything except `.debug` (no persistence-level floor). Sync paths emit many `.info` logs → thousands of fsync-bearing transactions/session. *(MOB-1433 only moved the save to a background context — still one transaction per line.)*
- **Merge:** ✅ *fixed in MOB-1433.* `mergeRemoteOperations` moved off-main to `SwiftDataWorker+EntryMerge.applyRemoteOperations` with one up-front identity fetch and **chunked saves (500/chunk)**; the push is now chunked ≤100 (`EntryService.swift:1171,1254-1271`).
- **Bulk create:** 🟠 *still open.* `EntryService.saveNewEntries` (`:224-248`) still loops `saveEntry`/`handleEntryAdded` per entry (`:227-237`). Local manual/bulk path only — route through the worker's batched `insertEntries` if a disk trace flags it.

### H5 🟠 HealthKit full sync — unbounded fetch + no delta
**Status: 🟡 partly addressed.** The `FetchDescriptor<Entry>` is **still unbounded** (no `fetchLimit`, no delta) — but MOB-1433/MA-3941 moved the read off-main and now commits writes in 1000-entry chunks, and integration settings are read once per sync with a per-account forward marker (`IntegrationsService.swift:248,257,276`).
**File:** `HealthKitService.swift:208-309` (`syncAllData`)
`FetchDescriptor<Entry>` with **no `fetchLimit`** (`:225-227`); fetches the entire create history (`:228`) and rebuilds the export payload — even if one entry changed. Drives peak memory and "Processing" CPU. **Still open:** add `fetchLimit` + paging + `autoreleasepool` + delta sync.

### H6 🟡 Account switch fan-out (hang on switch, not steady-state)
**Status: 🟡 still valid, untouched by MOB-1433.**
**File:** `AccountService.swift:332-370` (`switchAccount`)
`switchAccount` → `setActiveAccount` → `makeOtherAccountsInactive` (`:1522-1528`) does **N-1 sequential** `updateAccountClearingTokens` writes (`:1524-1526`); and the `$activeAccount` sink (`:56-62`) calls `ServiceRegistry.registerSessionServices()` (`:62`), re-registering ~8–12 services on every switch (no `.removeDuplicates()`). Multi-account users feel this as a switch-time stall, not a scroll hang. (Lower priority unless analytics show heavy multi-account use.)

---

## 6. Why 5.1.0 makes it worse

5.1.0 (`docs/guides/PRODUCT_TYPES_CURRENT_STATE.md`) adds `myBloodPressure` and `baby` product contexts, each with its own dashboard graph, on the engine above:

| Graph | Series | Lands on which path | Per-frame cost |
|---|---|---|---|
| Weight (today) | 1 | windowed (`pointsToRender`, ≤200 cap) | bounded ✅ |
| **BPM (new)** | 2 | weight path (windowed) + reference lines | ~2× bounded |
| **Baby percentile (new)** | **5–10** | **H1 non-windowed path** | **5–10× O(n) filter+scan, no cap** 🔴 |

The baby percentile graph is the single highest-risk surface: it multiplies the *one* hot path that was never windowed, and ships it to brand-new users whose first impression is that graph. **Sequence H1 (and ideally H2) before the baby graph reaches production.**

---

## 7. The remediation playbook (prioritized, with regression-safety)

Each item: the fix, the file, *how to keep functionality identical*, and *how we prove it*.

### P0 — Chart engine (blocks 5.1.0 graph expansion; biggest hang/hitch lever)

**P0.1 — Window + decimate the percentile path (H1).**
In `BaseGraphChartContent.percentileBoundaryExtendedPoints`/`interpolatedBoundaryPoint`:
- Replace `last(where:)`/`first(where:)` (`:101-102`) with **binary search** over the sorted `points` (a helper already exists in `GraphDataPreparer.swift`). O(n)→O(log n).
- Route percentile series through the same **`pointsToRender` windowing** the weight chart uses, so a baby graph never renders more than the visible+sampled set.
- *Functionality kept:* percentile curves look identical (decimation preserves shape via min/max sampling; boundary interpolation still draws to screen edges). *Proof:* snapshot-test the rendered point set for a known baby dataset before/after; Instruments hitch trace on a baby account.

**P0.2 — Re-key the visible-window cache (H3).** `BaseSectionViewModel.swift:211` — key on `(floor(scrollPosition / bucket), lastVisibleDataHash)` so the cache hits across the frames within a bucket. *Functionality kept:* window contents identical at rest; only recomputed when the window actually moves a bucket. *Proof:* assert cache-hit ratio >90% during a scripted scroll.

**P0.3 — Confirm window-derived values (average, y-axis) recompute on scroll-END only.** Spec says average updates on scroll end; ensure the y-axis/average path subscribes to `isScrolling == false` (there's already an `isScrolling` flag, `:23`) rather than to `scrollPosition`. *Functionality kept:* this is literally the spec. *Proof:* y-axis/average visibly settle once when the finger lifts; no mid-scroll recompute in Time Profiler.

**P0 gate:** capture **Animation Hitches + Time Profiler** `.trace` while scrolling (a) a 5k-entry weight account and (b) a baby percentile account, before/after. Target: steady-state scroll has **no main-thread frame > 16.7 ms**; hitch rate < ~5 ms/s.

### P1 — Kill the per-load DTO map (PROVEN #1 hang — H2)

The fetch is already off-main; the fix is to **stop mapping 4,000 raw `@Model` entries on every load.** In priority order:

**P1.1 — Stop re-reading raw entries on every load.** ⚠️ *Correction (2026-07-08): `DailyWeightSummary` does NOT exist in code* — `CLAUDE.md` lists it but there are zero references; daily/monthly summaries live only as in-memory `@Published` arrays on `EntryService` (`:51-62`), maintained incrementally by `handleEntryAdded`/`updateDailySummary`. So there is no "pre-aggregated table" to read. Two routes: **(a) cheap** — add a signature gate that runs *before* `getAllEntriesAsDTO`, keyed on `fetchEntryCount` + latest-entry timestamp, and return the cached in-memory summaries on a hit (no schema change); **(b) durable** — introduce a persisted `@Model` summary + lightweight migration so even the first/cold load skips the full map (larger, higher-risk). MOB-1433 added the off-main move + a signature cache over the *aggregation* but not route (a)'s gate-before-fetch, so the full map still runs each load.
**P1.2 — If a raw map is still needed:** (a) compute the dataset **signature from something cheap** (entry count + max timestamp) *before* the map, so an unchanged dataset short-circuits (`:2044` runs too late today); (b) **map once per load cycle and filter by type in memory** instead of re-fetching for scale/BPM/baby (today it's ×3); (c) use `FetchDescriptor.propertiesToFetch` and avoid faulting unused relationships so each row read skips the `getValue`/keyPath overhead.
*Functionality kept:* identical summaries on screen, just not recomputed from scratch. *Proof:* re-run the **same load trace on the same 4k account** — the `toOperationDTO` frame should drop from ~2.2 s to near-zero; Hangs track clears.

### P1 — Stop the disk-write storm (H4)

**P1.3 — Persistence log-level floor + batching.** Add `persistenceMinimumLogLevel` (default `.warning`) in `LoggerService` and buffer log rows (flush N / on interval) instead of one `save()` per line. *Functionality kept:* logs still captured (warnings/errors); just fewer transactions. *Proof:* Organizer Disk Writes trend toward <5 MB/day.
**P1.4 — Batch the merge inserts.** Route `mergeRemoteOperations` (`:1286`) and `saveNewEntries` (`:190`) through one batched `ctx.save()`. *Proof:* fs_usage shows one transaction per merge, not N.

### P2 — Memory & background CPU (H5, H6)

> **Note:** the *largest* share of the memory regression and the "Processing" battery is **H2/P1.1**, not H5 — the 56M-allocation churn comes from `toOperationDTO`. Land P1.1 first, then re-measure; H5/H6 below are the *remaining* memory/CPU after that.

**P2.1 — `fetchLimit` + paging + `autoreleasepool` per page** on `HealthKitService.syncAllData` (`:584`); **delta sync** keyed on a stored last-sync timestamp. *Functionality kept:* same data synced, in pages, only what changed.
**P2.2 — Batch `makeOtherAccountsInactive`** (`AccountService.swift:1408-1413`) into a single transaction; verify product-types migration (`:61-76`) has a run-once guard.

**P2 gate:** Allocations/Leaks clean across 3 account switches; Organizer Peak Memory < ~160 MB and Terminations trending down.

---

## 8. Dedicated: reducing the Hang Rate (51 → target <30 s/hr)

A "hang" = main thread blocked > 250 ms. Rank the main-thread blockers by how often they fire:

| Source | Fires when | Fix | Expected hang impact |
|---|---|---|---|
| **H2 — `toOperationDTO()` maps all entries (PROVEN)** | dashboard load, refresh, **account switch**, metric change, ×3 for 5.1.0 product types | P1.1/P1.2 (read pre-aggregated summaries; cheap pre-map signature; map once across types; `propertiesToFetch`) | **largest, measured** — 2.7 s per load on a 4k account; multi-second main-thread blocks |
| H1 percentile O(n) per frame | scrolling a baby/percentile graph | P0.1 | large for baby users (5.1.0) |
| H3 dead cache `.filter`/frame | scrolling any x-axis graph | P0.2 | moderate, broad |
| H4 per-row saves | sync / bulk import | P1.4 | spiky hangs during sync |
| SwiftUI Charts re-eval | scroll, many marks | windowing (P0.1) + `.equatable()`/`drawingGroup()` on the chart subtree where safe | moderate |

**Method (do this, don't guess):** wire **MetricKit** (`MXMetricPayload`/`MXDiagnosticPayload` → `cpuExceptionDiagnostics`, `hangDiagnostics`) to get **symbolicated hang stacks** from production. That converts "H2 is probably the biggest hang" into "here is the stack and its frequency," so P0/P1 are ordered by evidence, not by this table's guesswork.

---

## 9. What we still need

1. ~~MetricKit hang/CPU stacks~~ — **partially done.** A local Instruments trace (2026-06-04, 4k account) already gave the symbolicated #1 load-hang stack (H2 / `toOperationDTO`). Still want **MetricKit in production** to confirm field frequency and rank the *remaining* hangs (scroll H1/H3, sync H4).
2. **Fresh Organizer capture** of current `main`/internal build — the 51 s/hr may already be partly mitigated by 5.0.1/5.0.2 work (§4); measure before committing effort.

   **Recording-methodology lessons (learned the hard way 2026-06-04):**
   - **Keep traces short and targeted** — record ~10–15 s of the *specific* action (dashboard load), not minutes. A 3:44 Allocations recording produced a **5.9 GB un-indexed stream** that Instruments could not finalize and `xctrace export` could not read ("run data is missing"). Long churny recordings are unrecoverable.
   - For **Allocations**, turn **OFF "Record reference counts"** (Display Settings) to slash overhead.
   - **Physical device + Profile build + large account** (the bug is dataset-driven; a small account hides it).
   - **Never commit `.trace` bundles** — they are multi-GB binaries. Add `*.trace` to `.gitignore`.
   - To share data with reviewers/AI: export the **inverted, system-hidden call tree** as a screenshot, or `xcrun xctrace export` a *finalized* trace — not the raw bundle.
3. **Instruments `.trace`** on representative datasets (P0/P1 gates).
4. **Dataset distribution** — what does the 90th-pct *account* look like (entry count, years, # integrations, # accounts)? Confirm the "10k entry" assumption with analytics.
5. **Real review corpus** via App Store Connect API + ratings *by version* (separate 5.0.x sentiment from legacy).
6. **Disk-write & memory attribution** via fs_usage / Allocations to rank H4 vs H2 vs H5 with numbers, not code-reading.
7. **Android vitals** (Play Console: ANR, frozen frames, excessive wakeups) — same scrollable-chart redesign shipped on Vico; likely a parallel story, separate task.

---

## 10. Critic's corner (including of this report)

- **Attribution ≠ proof.** No symbolicated hang stack yet. If the dominant hang is H2 (main-thread fetch), the P0 chart work will fix hitches/battery but only dent the hang rate. **Do MetricKit (§8/§9) first** so we fix measured hangs.
- **I corrected the first-pass analysis.** Earlier notes claimed `handleEntryAdded` aggregates the full dataset on main and that Combine subscriptions never tear down — **both wrong** on reading the code. Treat any remaining unverified claim with the same skepticism.
- **`pointsToRender` is O(n)-per-frame but single-pass and capped output** — it could be O(log n)+slice, but it is *not* the disaster the hitch number alone implies for the weight chart. The baby path (H1) is the real offender.
- **The 4.46★ aggregate is a vanity number** (diluted by years of 4.x). Don't let "still 4.5 stars" deprioritize this; the *trend* is negative.
- **Disk 90→26 MB is not a win** — 5.0.0 shipped an order of magnitude over 4.x; 26 is "less bad."
- **Launch time improving is a trap** — fast to first frame, slow at everything after.
- **Perf fixes won't recover the design complaints.** Reviews also hate the low-density "zoomed-in" redesign and the removed draggable cursor — those are **product/design** regressions; route to design separately.
- **Metrics lag cuts both ways** — don't over-claim improvement from §4 optimizations until a fresh capture confirms it.

---

## 11. Recommendation

1. **Wire MetricKit** and take a **fresh Organizer + Instruments capture** — measure before building.
2. **Land P0 (chart engine)** — window/decimate + binary-search the percentile path (H1), re-key the window cache (H3), confirm scroll-end-only recompute (H4 spec). Gate on a hitch trace for weight *and* baby accounts.
3. **Land P1.1/P1.2 (fetch off main)** in parallel — likely the biggest single hang lever; plus the cheap disk-write fixes (P1.3/P1.4).
4. **Only then add the BPM and baby graphs.** Shipping them first puts the app's worst metric, multiplied, in front of its newest users.

All of it retains current functionality. The risk is in careful implementation + regression tests (downsampling shape/selection, scroll-end cadence), not in giving anything up.
