# Cold-login hang — root cause + fix plan (SwiftDataWorker runs on the main thread)

> **What this doc is.** The technical solution for the **cold-login main-thread hang** surfaced by the
> 2026-07-13 Instruments trace while investigating [MOB-517](https://greatergoods.atlassian.net/browse/MOB-517).
> This is the **real 5.1.0 cold-login freeze** and is **separate from MOB-517** (the dashboard DTO-map
> optimization) — see [MOB-517 doc §2/§9](MOB-517-dashboard-data-load.md). Belongs under epic
> [MOB-516](https://greatergoods.atlassian.net/browse/MOB-516) (5.1.0 Performance Hardening), most naturally as
> a **MOB-1433 follow-up** (the off-main move MOB-1433 claims did not actually take).
>
> **Scope:** iOS only · **Base:** `develop` · **Build:** Dev config only.
> All `file:line` refs verified against the working tree on **2026-07-13**.

---

## 1. Symptom (Instruments, `iOS/docs/cold login trace.trace`)

Fresh install → login with a large account → **the main thread is frozen ~37 s of a 59 s trace** (9 back-to-back
Severe Hangs, 3.5–6 s each, 00:15–00:52). ~49 % of the frozen samples are inside synchronous Core Data
`NSManagedObjectContext.performAndWait` **on the main thread**, with the heaviest identifiable work being
History's `getMonthsAll` (34 %), `SwiftDataWorker.fetchAllEntryData`/`extractEntryData` (26 %), per-row
relationship faulting `BathScaleMetric.bmr.getter` (21 %), and the on-login sync `performSync` +
`saveOrRollback` (~25 % combined). Full attribution table in [MOB-517 doc §2](MOB-517-dashboard-data-load.md).

---

## 2. Root cause

### 2.1 The core bug — `SwiftDataWorker` executes on the main thread

`SwiftDataWorker` is a **stock `@ModelActor`** with the default executor — no custom executor
([SwiftDataWorker.swift:170-171](../meApp/Core/Services/SwiftDataWorker.swift#L170)):

```swift
@ModelActor
actor SwiftDataWorker { … modelContext.fetch(descriptor) … }   // DefaultSerialModelExecutor
```

The `@ModelActor` `DefaultSerialModelExecutor` **does not guarantee off-main execution** — it is a *serial*
executor, not a *background* one. Every caller here is `@MainActor` (`EntryService` is
`@MainActor` — [EntryService.swift:10-12](../meApp/Data/Services/EntryService.swift#L10) — and it constructs the
worker at [:102](../meApp/Data/Services/EntryService.swift#L102)). So when a `@MainActor` method does
`await worker.fetchAllEntryData(…)`, the actor's job runs **on the main thread** and blocks it for the length of
the fetch + row materialization + relationship faults. **The trace confirms this directly:** the worker's own
methods (`fetchAllEntryData`, `extractEntryData`, `fetchEntriesAsDTO`) appear on the **Main Thread** stack
inside `performAndWait`. This is the well-known SwiftData `@ModelActor` pitfall, and it is the single biggest
cost (≈ the whole 49 % `performAndWait` block).

> This is why MOB-1433's comment "…so none of it runs on main"
> ([EntryService.swift:396-400](../meApp/Data/Services/EntryService.swift#L396)) didn't hold: moving the code
> *into the `@ModelActor`* isn't enough — the default executor still runs it on main.

### 2.2 Amplifier — the cold-login work piles onto the (main-bound) worker at once

At login several heavy operations run through the same main-bound worker back-to-back:

- **Double trigger.** `startBackgroundDataSync()` runs `loadDashboardData(.scale)` then
  `syncAllEntriesWithRemote()` ([ContentViewModel.swift:289-305](../meApp/Features/Common/ViewModels/ContentViewModel.swift#L289)),
  **and** the `activeAccount` sink independently runs `syncAllEntriesWithRemote()` + `loadDashboardData(.scale)`
  on first account publish ([EntryService.swift:124-128](../meApp/Data/Services/EntryService.swift#L124)) → the
  sync + dashboard load can fire **twice**.
- **The full-history sync** merges + `saveOrRollback`s in chunks — each chunk a main-thread block (because of
  §2.1) → the repeating ~4 s hang cycles.
- **Streak + metrics recompute** (`getStreak` / `resetMetricsToLatestEntry` / `updateMetrics`, ~9 %) also run
  on the main actor.

### 2.3 Amplifier — per-row relationship faulting

`extractEntryData` reads `BathScaleMetric` fields; the read paths that back it prefetch only
`scaleEntry` + `scaleEntryMetric` ([SwiftDataWorker.swift:238,259](../meApp/Core/Services/SwiftDataWorker.swift#L238)),
so `bmr.getter` still faults per row (21 % of the hang). No `propertiesToFetch` limiting.

---

## 3. The fix (ordered by leverage)

> **Implementation status (2026-07-13, branch `MOB-516-cold-login-hang-off-main`):**
>
> - **Fix 1 (custom `ModelExecutor`) — ATTEMPTED, CRASHED, REVERTED.** Swapping the `@ModelActor`'s executor
>   for a background-queue `BackgroundModelExecutor` compiled and *did* move the work onto a background serial
>   queue — but **SwiftData rejects a foreign executor at runtime**:
>   `SwiftData/ModelActor.swift:43: Fatal error: Unexpected executor — …BackgroundModelExecutor`. So a custom
>   `ModelActor` executor is **not viable on this SwiftData version (iOS 26)**. Reverted to the stock
>   `@ModelActor`. **This also confirms the root cause:** the crash stack was on a background thread, i.e. the
>   default `DefaultSerialModelExecutor` runs the actor's job **on the calling thread** — which is the main
>   thread when driven from `@MainActor EntryService`. The off-main *direction* is right; the *mechanism* must
>   change (see §3.1).
> - **Fix 3 (prefetch in `fetchProgressData`) — DONE** (`SwiftDataWorker.swift`, build ✅ Dev SUCCEEDED). Safe,
>   independent of the executor question.
> - **Fix 2 — no change needed** — `syncAllEntriesWithRemote` already coalesces via `activeSyncTask`
>   ([EntryService.swift:1031](../meApp/Data/Services/EntryService.swift#L1031)); `loadDashboardData` via
>   `activeDashboardLoadTasks`; the account-sink's `accountChanged` guard doesn't fire at cold login.
> - **Fix 4 — pending the corrected Fix 1** (streak/progress ride on the worker; metrics `getLatestEntry` piece
>   deferred pending re-trace).
>
> ### 3.1 Corrected off-main mechanism (replaces the custom executor)
> Since SwiftData won't let us swap the executor and `DefaultSerialModelExecutor` runs on the **caller's**
> thread, the fix is to **call the worker from a non-main context** so the job enqueues (and runs) on a
> background thread — wrap the hot `@MainActor → worker` read calls in
> `Task.detached(priority: .userInitiated) { … await worker.… }`. This keeps the **stock executor** (no
> "Unexpected executor" crash) while moving execution off main. `EntryWorkerProtocol: Sendable`, so the
> capture is clean.
>
> **VALIDATED + fully rolled out (2026-07-13, build ✅ Dev SUCCEEDED).** The detached-task mechanism was proven
> on the trace `iOS/docs/after_fix_cold_login.trace`: main-thread work fell **~37 s → 9.3 s (−75 %)**, no
> crash, and the two initially-fixed paths (`getMonthsAll`, `getAllEntriesAsDTO`) went to **0 %** on the main
> thread. The residual was exactly the deferred paths, so the same pattern is now applied to **all four** hot
> `@MainActor → worker` calls in `EntryService`:
> - `getAllEntriesAsDTO()` → `fetchEntriesAsDTO` (dashboard load)
> - `getMonthsAll()` → `fetchAllEntryData` + grouping (History, the 34 % path)
> - `getProgress()` → `fetchProgressData` (streak, was 18 %)
> - `syncAllEntriesWithRemote()` → `applyRemoteOperations` (sync merge — the 8.9 s hang, was ~27 %)
>
> **Fix 4 dropped as unnecessary:** the metrics `getLatestEntry`/`resetMetricsToLatestEntry` path measured
> **0.1 %** in the after-fix trace — not worth the `Entry`→snapshot API change.
>
> ### ✅ FINAL RESULT — cold-login hang eliminated (`iOS/docs/2_after_fix_cold_login.trace`, 2026-07-13)
> | | Severe hangs | Main thread |
> |---|---|---|
> | Before any fix | **9** (~37 s frozen) | 100 % blocked on SwiftData `performAndWait` |
> | After 2 read paths | 2 (~11.7 s) | sync + streak residual |
> | **After all 4 paths** | **0** | **91.6 % under `__CFRunLoopRun` (idle/UI); only ~2 % (~130 ms) residual SwiftData** |
>
> **Zero hangs recorded.** The main thread is now dominated by the normal run loop + UIKit/SwiftUI/Charts
> rendering; `fetchEntriesAsDTO` / `fetchAllEntryData` / `fetchProgressData` / `applyRemoteOperations` are all
> off the main thread. The ~2 % residual is a few small reads, not worth chasing.
>
> **Remaining before merge:** (1) a **correctness pass on the sync merge** — `applyRemoteOperations` (a write)
> now runs from a background task, so confirm entry counts / no dupes / graph+history populate after a big-account
> login; (2) commit to the branch + PR to `develop`; (3) unit test that the hot `@MainActor → worker` calls stay
> off-main (guard against regressing back to a direct `await worker.…`).

### Fix 1 ★ — force `SwiftDataWorker` genuinely off the main thread (the primary fix)

Give the worker a **custom `ModelExecutor` backed by a dedicated background `DispatchQueue`**, so every
operation (reads AND the sync merge/saves) runs off-main. This alone converts the ~37 s main-thread hang into
background work. Replace the stock `@ModelActor` macro with a hand-rolled `ModelActor` conformance:

```swift
// Runs the worker's ModelContext work on a dedicated background queue, NOT the main thread.
// The stock @ModelActor DefaultSerialModelExecutor does not guarantee off-main execution;
// driven from @MainActor EntryService it ran on the main thread (cold-login hang, MOB-516).
final class BackgroundModelExecutor: ModelExecutor, SerialExecutor, @unchecked Sendable {
    let modelContext: ModelContext
    private let queue = DispatchQueue(label: "com.gurus.weight.swiftdata-worker", qos: .userInitiated)
    init(modelContext: ModelContext) { self.modelContext = modelContext }
    func enqueue(_ job: consuming ExecutorJob) {
        let unowned = UnownedJob(job)
        let executor = asUnownedSerialExecutor()
        queue.async { unowned.runSynchronously(on: executor) }
    }
    func asUnownedSerialExecutor() -> UnownedSerialExecutor { UnownedSerialExecutor(ordinary: self) }
}

actor SwiftDataWorker: ModelActor {                 // was: @ModelActor actor SwiftDataWorker
    nonisolated let modelContainer: ModelContainer
    nonisolated let modelExecutor: any ModelExecutor

    init(modelContainer: ModelContainer) {
        self.modelContainer = modelContainer
        let context = ModelContext(modelContainer)
        self.modelExecutor = BackgroundModelExecutor(modelContext: context)
    }
    // all existing methods unchanged — they now run on the background queue
}
```

**Validate by re-profiling:** the worker's `fetchAllEntryData` / `extractEntryData` / `fetchEntriesAsDTO`
frames must move OFF the Main Thread; the Hangs track must clear. *(This is the documented SwiftData
`@ModelActor`-runs-on-main workaround; treat it as the hypothesis the re-trace confirms.)*

### Fix 2 — kill the double login trigger + let sync settle before the heavy reads

- **Coalesce** the two login paths so `syncAllEntriesWithRemote()` + `loadDashboardData()` fire **once**, not
  from both `startBackgroundDataSync` ([ContentViewModel.swift:297-299](../meApp/Features/Common/ViewModels/ContentViewModel.swift#L297))
  **and** the `activeAccount` sink ([EntryService.swift:124-128](../meApp/Data/Services/EntryService.swift#L124)).
  This is the same **debounce/coalesce** item MOB-517 lists (its §5.4).
- Even off-main (Fix 1), the sync's chunked writes and the dashboard/streak reads contend on the one SQLite
  store lock. Running them on the **single serial worker queue** (Fix 1) already serializes them (no lock
  thrash); additionally consider **deferring the dashboard/streak reads until the initial full-history sync
  settles** on a cold account, so first paint isn't racing the merge.

### Fix 3 — stop the per-row faulting (`bmr.getter`, 21 %)

Extend `relationshipKeyPathsForPrefetching` to cover every relationship `extractEntryData` reads (add
`bpmEntry` / `babyEntry`; confirm the metric relationship is actually prefetched on the `fetchAllEntryData`
path), and add `propertiesToFetch` if profiling still shows column faults. (Overlaps MOB-517 §5.3.)

### Fix 4 — move streak/metrics recompute off the main actor (~9 %)

`getStreak` / `resetMetricsToLatestEntry` / `updateMetrics` run on the main actor at login. Route their heavy
reads through the (now off-main) worker, or defer them until after first paint.

---

## 4. Files to change

| File | Change |
|---|---|
| `Core/Services/SwiftDataWorker.swift` | **Fix 1** — replace stock `@ModelActor` with a `ModelActor` conformance + `BackgroundModelExecutor` (dedicated background queue). **Fix 3** — extend prefetch / `propertiesToFetch`. |
| `Features/Common/ViewModels/ContentViewModel.swift` | **Fix 2** — single login sync/load path. |
| `Data/Services/EntryService.swift` | **Fix 2** — remove/guard the duplicate `activeAccount`-sink sync+load; **Fix 4** — streak/metrics reads via the worker/deferred. |
| `meAppTests/…/SwiftDataWorkerTests`, `EntryServiceTests` | Off-main assertion (worker not on main), single-trigger test, parity. |

**Boundary rule (unchanged):** the worker returns `Sendable` DTOs/`EntryData` — never the `@Model` across the
actor boundary (`check-snapshot-boundary.sh`).

---

## 5. Verification (device / Instruments — Kesavan runs it)

Re-run the **cold-login** trace (Time Profiler + Hangs) on the same large account after Fix 1:
- **Hangs track clear** on the main thread (the ~37 s of Severe Hangs gone).
- Worker frames (`fetchAllEntryData`/`extractEntryData`/`fetchEntriesAsDTO`) appear on a **background** thread,
  not Main.
- Login → dashboard first paint no longer freezes; spinner instead of a frozen UI.
- Then confirm Fix 2 (one sync, not two) and Fix 3 (`bmr.getter` faulting drops).

---

## 6. Risks

- **SwiftData concurrency is delicate.** The custom executor must confine ALL `modelContext` use to its queue
  (it does — every worker method hops through the executor). Test create/merge/delete/read paths thoroughly;
  a `@Model` must never escape the worker. Watch for regressions in the sync merge (`saveOrRollback`).
- **`@unchecked Sendable`** on the executor is required and acceptable (the queue provides the serialization);
  keep the `ModelContext` private to the executor.
- **Behaviour parity:** this changes *where* the work runs, not *what* it computes — dashboard/History/streak
  results must be byte-identical. Cover with the existing worker/service tests.

---

## 7. Relationship to MOB-517

- **This ticket (cold-login hang)** = the actual 5.1.0 freeze; Fix 1 (off-main worker) is the big win.
- **MOB-517** = a *warm-load* optimization (skip the DTO re-map on unchanged data). Its prefetch (§5.3) and
  debounce (§5.4) items overlap Fix 3 / Fix 2 here. MOB-517's own pre-fetch gate does **not** help cold login
  (all data is new). Do **this** first.

---

*Line numbers verified 2026-07-13 on the `MOB-518-…` working tree (develop-based). Trace: `iOS/docs/cold login
trace.trace`. Re-grep symbols if they drift.*
