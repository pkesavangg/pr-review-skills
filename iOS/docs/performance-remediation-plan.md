# 5.1.0 Performance Remediation — Technical Plan & Epic

**Date:** 2026-06-05 · *Updated 2026-07-08 — reconciled against `develop` after MOB-1433 shipped.* · **Platform:** iOS (`meApp/iOS/meApp`)
**Jira:** Epic **[MOB-516](https://greatergoods.atlassian.net/browse/MOB-516)** — "[meApp][iOS] 5.1.0 Performance Hardening" (project MOB / GGT-Mobile). *(This doc predates the MOB → MA→MOB rename; the epic is MOB-516, not "MA-XXXX".)*
**Companion docs:** plain-English summary → `performance-issues-overview.md`; full investigation + Instruments evidence → `performance-analysis-5.1.0.md`; the shipped entry-pipeline work → `MOB-1433-implementation-plan.md`.

> **Goal:** eliminate the load/scroll hangs, cut peak memory and "Processing" battery, and do it **with zero intended functionality change**, protected by unit tests and before/after Instruments traces. The dashboard chart + data-load work is also a **prerequisite for the 5.1.0 BPM and baby-growth graphs**.

---

## Status after MOB-1433 (reconciled 2026-07-08)

**[MOB-1433](https://greatergoods.atlassian.net/browse/MOB-1433) (the entry fetch/sync off-main pipeline) shipped and delivered a large slice of this epic.** The **login → dashboard loading-screen freeze is fixed** (it is no longer gated on the remote sync/merge). This plan's remaining scope is therefore narrower than when it was first written. Reconciliation against the current working tree:

| Task | Status now | What's left |
|---|---|---|
| **1 — Data load** | 🟡 **Partly shipped (MOB-1433)** | Off-main fetch+map (`SwiftDataWorker.fetchEntriesAsDTO`, `SwiftDataWorker.swift:229-242`), `#Index<Entry>` (`Entry.swift:23-28`), relationship prefetch, and a signature cache over the *aggregation* (`EntryService.swift:2152-2185`) all landed. **Still open (the durable fix, deferred in MOB-1433 §5c):** the full-history fetch+map still runs on *every* load (the signature gates aggregation, **not** the fetch); streak/progress still read the whole table on a cache miss (`getStreak` `:733`, `getProgress` `:476` — no incremental running totals); the **Baby load's fetch is still on the main actor** (`fetchBabyEntrySnapshots` `EntryService.swift:1870-1871`). |
| **2 — Chart engine** | 🟡 **Largely shipped (MOB-518, PR #2237)** | H1 (percentile path) FIXED — binary-search via `PercentileChartWindowing`/`SortedArrayIndex` (commit `813f0f98a`) + a v2 weight-graph engine rebuild. **Residual = H3** (`visibleChartSeriesData` still keyed on raw `scrollPosition`, `BaseSectionViewModel.swift:219`, not re-keyed). No longer blocks the 5.1.0 baby/BPM graphs (they land on the fixed engine). |
| **3 — Disk/logging** | 🟡 **Half shipped (MOB-1433)** | Merge batching DONE — `mergeRemoteOperations` moved to `SwiftDataWorker+EntryMerge.applyRemoteOperations` (chunked 500/save; push chunked ≤100). **Still open:** logging still does per-row insert+save with no persistence floor (`LoggerRepository.swift:57-58`, `LoggerService.swift:60-61`); `saveNewEntries` local bulk-save is still per-row (`EntryService.swift:227-237`). |
| **4 — Memory/CPU + MetricKit** | 🟡 **Partly shipped (MOB-1433)** | HealthKit settings-read-once + per-account forward marker DONE (`IntegrationsService.swift:248,257,276`). **Still open:** `syncAllData` `FetchDescriptor` is still unbounded — no `fetchLimit`/delta/`autoreleasepool` (`HealthKitService.swift:225-228`); account-switch fan-out unchanged (`AccountService.swift:1524-1526`); no MetricKit anywhere. |

**Revised suggested order (post-MOB-518):** Task 2 (chart engine) **largely shipped in MOB-518** — H1 + v2 weight-graph engine are done; only the H3 cache re-key remains. The **top remaining lever is now Task 1's *durable* incremental-aggregation** (the deferred MOB-1433 §5c piece — cheap signature gate **before** the fetch, incremental streak/progress totals, move the baby fetch off-main), then the Task 2 H3 residual, then Task 3 logging + Task 4 residuals. **MetricKit early** if possible, to rank residual hangs from the field.

> **Current file:line references** (verified 2026-07-08 — the ones in "Verified facts" below were written pre-MOB-1433 and have shifted): H1 percentile bypass — `BaseGraphChartContent.swift:60-64`, `.filter` `:71`, linear scans `:100-101`; H3 visible cache keyed on raw `scrollPosition` — `BaseSectionViewModel.swift:211`, per-frame `.filter` `:222`; binary-search helper (exists, in use) — `GraphDataPreparer.swift:469,482`; `pointsToRender` (≤200 cap, intact) — `BaseGraphViewCacheSupport.swift:70`.

---

## Verified facts this plan is built on

> ⚠️ **These facts were captured 2026-06-05, before MOB-1433.** Items 1–6 (data load, DTO map, index, signature cache, `mergeRemoteOperations`) describe the *pre-MOB-1433* code and their line numbers have moved — see **"Status after MOB-1433"** above for what actually shipped and the current line references. Items 7 (chart engine) and 8a (logging) are still accurate. Kept here for the evidentiary trail.

All confirmed by reading the working tree (and one Instruments trace on a 4,000-entry device):

1. **Proven #1 hang (Instruments):** `loadDashboardData → performDashboardDataLoad → EntryRepository.fetchEntriesAsDTO → Entry.toOperationDTO()` consumed **2.71 s / 93%** of a 2.9 s hang window. `Entry.toOperationDTO()` ([Entry.swift:139](../iOS/meApp/Domain/Models/DB/Entry.swift)) reads ~25 fields across 4 relationships; every read is SwiftData `ObservationRegistrar.access` / `getValue` / keyPath overhead × ~4,000 rows.
2. `fetchEntriesAsDTO` ([EntryRepository.swift:474](../iOS/meApp/Data/Storage/DB/EntryRepository.swift)) fetches **all** create-entries (no `fetchLimit`, no entry-type predicate) and maps **every** row, **off-main** (the threading is fine; the volume + per-row cost is not).
3. `loadDashboardData` runs on appear, account change, **every sync** (`EntryService.swift:924/1015/1161`), and **separately per entry type** (`.scale` + `.bpm` in `MultiDeviceSnapshotViewModel.swift:66/72`). For 5.1.0's three product types this full map runs **≈3×**.
4. The dataset-signature cache (`summaryCacheByEntryType`) is computed **after** the map (`EntryService.swift:2044`), so the expensive conversion is **never skipped**.
5. The same per-row Observation churn shows up in Allocations as **~56M transient allocations / 8.75 GiB churned** with steadily climbing memory — i.e. this one path drives **hang + memory + battery** together.
6. **`DailyWeightSummary` does not exist in code** — `CLAUDE.md` lists it, but there are zero references/constructors. Summaries today live only as in-memory `@Published` arrays on `EntryService`, maintained incrementally by `handleEntryAdded`/`updateDailySummary` ([EntryService.swift:2271](../iOS/meApp/Data/Services/EntryService.swift)). **The fix must therefore cache/short-circuit in memory (and optionally introduce a persisted summary), not "read an existing summary table."**
7. **Scroll path:** the weight chart is already windowed (`BaseGraphViewCacheSupport.pointsToRender`, ≤200-point cap), but the **baby/percentile path bypasses it** and does O(n) `.filter` + linear scans per series per frame ([BaseGraphChartContent.swift:72/101-102](../iOS/meApp/Features/Dashboard/Views/Components/BaseGraphChartContent.swift)); and the visible-window cache is keyed on raw `scrollPosition` so it's dead during scroll ([BaseSectionViewModel.swift:211](../iOS/meApp/Features/Dashboard/ViewModels/BaseSectionViewModel.swift)).
8. **Disk:** per-row `ctx.save()` per log line ([LoggerRepository.swift:58](../iOS/meApp/Data/Storage/DB/LoggerRepository.swift)) and per-entry saves in `mergeRemoteOperations` ([EntryService.swift:1286](../iOS/meApp/Data/Services/EntryService.swift)).

---

# EPIC: [MOB-516](https://greatergoods.atlassian.net/browse/MOB-516) — 5.1.0 Performance Hardening (iOS)

**Outcome:** dashboard load/scroll/switch hangs eliminated; Peak Memory and "Processing" battery materially reduced; BPM + baby graphs ship on a fast engine. **No functional regressions.**

**Epic-level acceptance criteria**
- On a ≥4,000-entry account (Profile build, physical device): **no main-thread block > 250 ms** on dashboard load, refresh, account switch, or scroll (Instruments Hangs/Animation Hitches before & after).
- Steady-state graph scroll: **hitch rate < ~5 ms/s**; no frame > 16.7 ms.
- `Entry.toOperationDTO()` no longer appears as a hot frame on routine loads.
- All existing unit tests pass; new tests added per task; coverage gates met (Data/Services 80%/85%, ViewModels 80%).
- **Behavior parity:** graphs, summaries, averages-on-scroll-end, dynamic y-axis, week/month/year/total, new-entry/delete/unit/weightless updates all identical to current — verified by tests + manual QA script in each task.

**Suggested order (revised post-MOB-518):** Task 2 (chart engine) **largely shipped in MOB-518** — H1 + the v2 weight-graph engine cleared the graph-scroll hang and unblocked the 5.1.0 graphs; only the H3 cache re-key remains. Remaining order: **Task 1-residual (durable incremental-aggregation) → Task 2 H3 → (Task 3 ∥ Task 4).** Wire MetricKit (Task 4) early if possible so field data ranks the rest. *(Pre-MOB-1433 order was Task 1 → Task 2 → Task 3 ∥ Task 4; the post-MOB-1433 revision made Task 2 first — now itself superseded by MOB-518.)*

---

## Task 1 — Dashboard data load: stop re-mapping the full history
**Status: 🟡 PARTLY SHIPPED in MOB-1433 — remaining half is the deferred durable fix.** MOB-1433 moved the fetch+DTO map off the main actor (`SwiftDataWorker.fetchEntriesAsDTO`), added `#Index<Entry>` + relationship prefetch, and added a signature cache over the *aggregation*. That killed the loading-screen freeze. It did **not** stop the full-history fetch+map from running on every load (the signature gates aggregation, not the fetch), and streak/progress still recompute from the whole table on a cache miss. Approach items 1 (gate *before* the fetch) and the incremental streak/progress work below are the remaining, deferred piece (MOB-1433 §5c — owned by Kesavan). Items 2–3 largely landed.
**Fixes:** residual load/switch/History contention on 10k accounts, ~56M-allocation memory churn, "Processing" battery. **Blocks 5.1.0.**

**Root cause (current):** `loadDashboardData` → `performDashboardDataLoad` (`EntryService.swift:2137-2191`) still calls `getAllEntriesAsDTO()` (`:2141`) on *every* load; the signature cache (`:2152-2185`) only short-circuits the **aggregation**, so a 10k-entry account still materializes + maps 10k rows each load (measured ~4.9 s of worker time contending with the main context, MOB-1433 §5c). Streak (`getStreak` `:733`) and progress (`getProgress` `:476`) do their own full-history fetch. The **Baby load's fetch is still on the main actor** (`fetchBabyEntrySnapshots` `:1870-1871`).

**Approach (remaining work in order of impact / lowest risk first):**
1. **Cheap signature gate *before* the fetch+map.** Derive a dataset signature from cheap queries that already exist — `fetchEntryCount(forUserId:)` + latest-entry timestamp (`fetchLatestEntry`) (and a metric-selection token). If the signature matches the last successful load for that `entryType`, **return the cached summaries immediately** and skip `fetchEntriesAsDTO` entirely. This kills the full-history read on every redundant load (account return, tab re-appear, repeated post-sync loads). *(Not yet done — MOB-1433 added the signature cache but computes it after the fetch.)*
   - **Move the Baby load off the main actor** — route `fetchBabyEntrySnapshots` (`:1870-1871`) through the worker like the weight/BPM paths.
   - **Incremental streak/progress:** maintain running totals so `getStreak`/`getProgress` don't re-read the full table on a cache miss (the deferred incremental-aggregation piece; may want a persisted summary — see caveat below).
2. **Map once, share across entry types.** `getAllEntriesAsDTO` already fetches *all* create-entries irrespective of type. Fetch+map **one** DTO array per signature and have `.scale` / `.bpm` / `.baby` aggregations filter that shared array in memory, instead of three independent full fetch+map passes.
3. **Reduce per-row cost.** ✅ *Largely done in MOB-1433* — `fetchEntriesAsDTO`/`fetchAllEntryData` set `relationshipKeyPathsForPrefetching = [\.scaleEntry, \.scaleEntryMetric]` (`SwiftDataWorker.swift:238,259`). Remaining option if profiling still flags it: add `\.bpmEntry`/`\.babyEntry` and/or `propertiesToFetch`.
4. **Coalesce redundant triggers.** ✅ *Largely done in MOB-1433* — concurrent loads de-duped via `activeDashboardLoadTasks`; sync-side recompute now gated on `dashboardDataChanged` (`EntryService.swift:1139-1150`) and coalesced behind the UI (`scheduleProgressAndStreakRefresh` `:2247-2258`).

**Files (≈3–4 source + 2–3 test):**
- `Data/Services/EntryService.swift` — `performDashboardDataLoad`, signature gate, shared-DTO reuse, debounce.
- `Data/Storage/DB/EntryRepository.swift` (+ `EntryRepositoryProtocol`, `SwiftDataWorker.swift`) — prefetch/limited fetch; a cheap count+latest query if not already exposed.
- `meAppTests/.../EntryServiceTests`, `EntryRepositoryTests` — signature-gate hits/misses, shared-map correctness, parity of produced summaries.

**No-regression guard:** unit test that summaries produced via the cached/shared path are **byte-identical** to the current full-map path for a fixed dataset; QA: load → add entry → delete → switch metric → switch account, confirm graph/numbers unchanged.

**Done when:** ✅ *the original bar is already met* — `Entry.toOperationDTO()` is no longer a main-thread hot frame (it runs on the worker) and the loading screen no longer hangs. **New bar for the remaining work:** on a 10k account, a redundant `loadDashboardData` (unchanged dataset) does **no** full-history fetch (signature gate hits before the fetch), and History/Dashboard open on a large account no longer stalls behind a 10k worker read (`getMonthsAll`/`loadDashboardData` worker time bounded, per MOB-1433 §5c re-measure).

---

## Task 2 — Chart engine: scroll hitch/hang + multi-series readiness
**Status: 🟡 LARGELY SHIPPED in MOB-518 (PR #2237).** H1 (percentile path) is fixed via binary search (`PercentileChartWindowing` + generic `SortedArrayIndex`, commit `813f0f98a`) and a v2 weight-graph engine rebuild landed; this no longer blocks the 5.1.0 baby/BPM graphs. **Residual: H3** — `visibleChartSeriesData` is still keyed on raw `scrollPosition` (`BaseSectionViewModel.swift:219`), not re-keyed/removed (open decision, see `MOB-516-implementation-plan.md` §2.1 Step 2b). The H1/H3 "approach" below is retained; H1 documents what shipped, H3 is the remaining item.
**Fixes:** scroll stutter/hang (H1, H3); **prerequisite for baby/BPM graphs.**

**Approach:**
1. **Window + binary-search the percentile path.** The percentile branch computes `pointsToRender` and then **discards it** for percentile series (`BaseGraphChartContent.swift:60-64`), returning `percentileBoundaryExtendedPoints` — an O(n) `.filter` (`:71`, no 200-cap) + `last(where:)`/`first(where:)` linear scans (`:100-101`). Route percentile series through the same `pointsToRender` windowing the weight chart uses, and replace the linear scans with binary search over the sorted points — **the helper already exists and is already in use: `binarySearchFirst`/`binarySearchLast` (`GraphDataPreparer.swift:469,482`).** O(n)→O(log n), per series.
2. **Re-key the visible-window cache** (`BaseSectionViewModel.swift:211`) — the key includes raw `scrollPosition`, which changes every frame, so the `.filter` at `:222` re-runs every scroll frame. Re-key on a **quantized scroll bucket + dataHash** so it hits across frames within a bucket. *(Note: `chartSeriesData` `:158-166` already short-circuits the store fetch during scroll when the metric is unchanged — this is only about the `visibleChartSeriesData` filter.)*
3. **Confirm scroll-end-only recompute** of window average / y-axis (subscribe to `isScrolling == false` — the flag exists, `BaseSectionViewModel.swift:23`), matching the spec ("average updates when scroll ends").

**Files (≈4–5 source + 2–3 test):**
- `Features/Dashboard/Views/Components/BaseGraphChartContent.swift`
- `Features/Dashboard/Managers/Graph/BaseGraphViewCacheSupport.swift`
- `Features/Dashboard/ViewModels/BaseSectionViewModel.swift`
- `Features/Dashboard/Views/Components/BaseGraphView.swift` (defer onChange work)
- `Features/Dashboard/Managers/Graph/GraphDataPreparer.swift` (reuse binary search)
- tests: ViewModel cache-hit ratio, windowing/percentile point-set parity (snapshot of produced points), boundary interpolation correctness.

**No-regression guard:** assert the rendered/visible point set for known weight **and** baby datasets matches current output (downsampling preserves shape; crosshair still selects real points). QA: scroll all periods on weight + baby + BPM; verify average settles on lift, y-axis matches visible data, crosshair snaps to real entries.

**Done when:** Animation Hitches trace on weight + baby accounts shows < ~5 ms/s steady-state, no frame > 16.7 ms.

---

## Task 3 — Disk writes & logging
**Status: 🟡 HALF SHIPPED in MOB-1433.** The remote-merge batching (item 2) landed; the logging storm (item 1) and the local bulk-save (`saveNewEntries`) are still open.
**Fixes:** ~8× disk-write regression; spiky hangs during sync.

**Approach:**
1. **Persistence log-level floor.** *Still open.* Logging still does per-row `ctx.insert` + `ctx.save()` per line (`LoggerRepository.swift:57-58`) and persists everything except `.debug` — no persistence floor (`LoggerService.swift:60-61`; each line builds a fresh `LoggerRepository` and saves, `:77,87`). Add `persistenceMinimumLogLevel` (default `.warning`) in `LoggerService` so `.info`/`.success` don't hit disk; buffer rows and flush in batches instead of one `ctx.save()` per line. *(One thing did change: the per-row save now runs on a background context, so it's off main — but it's still one transaction per line.)*
2. **Batch merge inserts.** ✅ *Done in MOB-1433* — `mergeRemoteOperations` was moved off-main to `SwiftDataWorker+EntryMerge.applyRemoteOperations` with one up-front identity fetch and chunked saves (500/chunk); the push is chunked ≤100 (`EntryService.swift:1171,1254-1271`). **Residual:** `saveNewEntries` (the local manual/bulk-save path) is **still per-row** — `for entry in entries { saveEntry; handleEntryAdded }` (`EntryService.swift:227-237`). Route it through the worker's batched `insertEntries` if bulk local imports show up in a disk trace.

**Files (≈2 source + 1 test):** `Core/Services/LoggerService.swift`, `Data/Storage/DB/LoggerRepository.swift` (+ `Data/Services/EntryService.swift` only if batching `saveNewEntries`); tests for log-level filtering + batch save count.

**No-regression guard:** warnings/errors still persisted; merged entries identical (count + content) to per-row path. **Done when:** Organizer Disk Writes trends toward <5 MB/day.

---

## Task 4 — Memory/CPU residuals + production instrumentation
**Status: 🟡 PARTLY SHIPPED in MOB-1433.** HealthKit now reads settings once per sync and gates historical backfill with a per-account marker; the HealthKit *fetch* bound, account-switch batching, and MetricKit are all still open.
**Fixes:** remaining memory/terminations (after Task 1), account-switch stall, and gives us field hang data.

**Approach:**
1. **HealthKit sync:** *Partly done.* `syncAllData`'s `FetchDescriptor<Entry>` is **still unbounded** — no `fetchLimit`, no delta filter, fetches the whole create history (`HealthKitService.swift:225-228`); add `fetchLimit` + paging + `autoreleasepool` per page + **delta sync** keyed on a stored last-sync timestamp. *(Already landed in MOB-1433: the fetch runs off-main and writes commit in 1000-entry chunks; integration settings are read once per sync and forwarding is gated by `lastHealthKitForwardTimestamp` — `IntegrationsService.swift:248,257,276`.)*
2. **Account switch:** *Still open.* `makeOtherAccountsInactive` still does N-1 sequential `updateAccountClearingTokens` writes (`AccountService.swift:1524-1526`); the `$activeAccount` sink still calls `ServiceRegistry.registerSessionServices()` on every switch (`:56-62`, no `.removeDuplicates()`). Batch the writes into one transaction; verify the product-types migration has a run-once guard.
3. **MetricKit:** *Still open — nothing present.* Add an `MXMetricManagerSubscriber` service (hang + CPU-exception diagnostics) wired in `ServiceRegistry`/AppDelegate, logging/uploading payloads — ongoing production signal to rank residual hangs.

**Files (≈4–6 source + 2 test):** `HealthKitService.swift`, `AccountService.swift`, `AccountMigrationService.swift`, new `MetricKitService.swift` + `ServiceRegistry`/AppDelegate registration; tests.

**No-regression guard:** same data synced (paged, delta); same account-switch outcome. **Done when:** Allocations Mark-Generation across a sync shows bounded retention; Peak Memory < ~160 MB; MetricKit payloads arriving.

---

## Rough sizing (remaining work after MOB-1433)

| Task | Remaining status | Source files | Test files | Risk | Blocks 5.1.0? |
|---|---|---|---|---|---|
| 1 — Data load | 🟡 durable incremental-aggregation only (off-main move done) | ~2–3 | ~2 | Med–High (incremental totals + possible persisted summary/migration) | **Yes** (residual) |
| 2 — Chart engine | 🟡 largely shipped (MOB-518); residual = H3 cache re-key | ~1 | ~1 | Low (H3 only) | Unblocked |
| 3 — Disk/logging | 🟡 logging floor + `saveNewEntries` only (merge batching done) | ~2 | ~1 | Low | No |
| 4 — Memory/CPU + MetricKit | 🟡 HealthKit fetch-bound + account-switch + MetricKit (settings/marker done) | ~4 | ~2 | Med (HealthKit delta logic) | No |

---

## Honest caveats (carry these into the tickets)

- **"No regression" is the design intent, enforced by tests + before/after traces — not an absolute guarantee.** The real risk is implementation bugs in two spots: chart downsampling (must preserve line shape *and* keep full data for crosshair selection) and data-load cache invalidation (must recompute on real data change — add/delete/edit/unit/weightless/account-switch). Both are explicitly test-covered above.
- **Constraint — snapshot boundary:** Task 1/3 must keep returning DTOs/snapshots, never `@Model`, across the actor boundary (enforced by SwiftLint `no_published_swiftdata_model` + `check-snapshot-boundary.sh`).
- **`CLAUDE.md` is stale** re `DailyWeightSummary` — confirmed 2026-07-08: there is **no such `@Model` in code** (zero references); daily/monthly summaries live only as in-memory `@Published` arrays on `EntryService` (`:51-62`). The durable Task-1 fix (incremental running totals so even a redundant/first load skips the full map) is the **deferred piece from MOB-1433 §5c that Kesavan owns** — it is now the *remaining* Task-1 scope. If it takes the persisted-summary route (a new `@Model` + lightweight migration) it is the larger/higher-risk sub-task; the cheaper interim is the signature-gate-before-fetch (Task 1 approach item 1), which needs no schema change.
- **Coverage gates make the test files mandatory**, not optional padding.
- **Measure before/after each task** on a large account; don't rely on this plan's predicted magnitudes.
