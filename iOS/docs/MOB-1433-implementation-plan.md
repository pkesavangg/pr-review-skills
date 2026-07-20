# MOB-1433 — Phase 2 implementation plan (self-contained handoff)

> **Purpose of this file:** the Phase 1 investigation chat is long; Phase 2 (coding) may run in a fresh
> chat. This file is the complete blueprint — a new session should be able to execute from here without
> re-tracing anything. Read `iOS/docs/MOB-1433-entry-perf-root-cause.md` for the full evidence; the
> essential facts are repeated below.

**Ticket:** [MOB-1433](https://greatergoods.atlassian.net/browse/MOB-1433) · Epic MOB-516 (5.1.0 Performance Hardening) · Repo `gg-engineering/meApp`, **iOS only**
**Base branch:** `develop` · New branch: `MOB-1433-batch-entry-sync-off-main-pipeline` (commit prefix `MOB-1433`)

---

## 1. Problem TL;DR (evidence in root-cause doc)

Two symptoms, one root cause — **everything runs on the main actor**:

1. **First login with 5k–10k entries freezes the loading screen for minutes.** `ContentViewModel.loadData()` gates `.dashboard` on the whole first sync; the sync decodes the full history JSON on main (`HTTPClient.swift:191`), then merges **per entry** on main: unindexed `fetchEntry(byServerEntryId:)` per group + one `ctx.save()` disk commit per row + one HealthKit forward per new entry. `EntryRepository.performBackgroundTask` (`EntryRepository.swift:57-60`) is background in name only — `@MainActor` class, closure runs inline, 27 call sites.
2. **Re-opening screens stutters.** Every read is a full-table, unindexed, main-actor fetch: Dashboard fetches the *entire sorted table* to show the latest entry on **every** `.onAppear`; History re-reads the full table per open and per `entrySaved` event; foregrounding the app can re-run the whole init+sync because the account dedupe keys on `lastActiveTime`.

Key structural facts a new session must know:
- `Entry` has **zero `#Index`** (none anywhere in the project); only `id` is `.unique`. 4 unconfigured to-one relationships (`scaleEntry`, `scaleEntryMetric`, `bpmEntry`, `babyEntry`) fault per row.
- The UI **never observes SwiftData** (no `@Query`, no `.modelContainer`, no cross-context notifications). All refresh = manual refetch + `@Published` republish → a background writer + one refetch fits the existing pattern.
- `SwiftDataWorker` (`@ModelActor`, `Core/Services/SwiftDataWorker.swift:133`) is the only off-main SwiftData access; currently **read-only**, used once (`EntryService.swift:422`).
- `submitEntries` already accepts an array (`Data/API/EntryRepositoryAPI.swift:12`) but is called one entry per POST in a serial loop (`EntryService.swift:1140-1165`).
- `EntryRepositoryBackgroundQueue` (`EntryRepository.swift:18-36`) is dead code (never called) — delete it as part of this work.
- The network stack is `@MainActor` end to end: `HTTPClientProtocol.swift:3`, `HTTPClient.swift:10`, `EntryRepositoryAPI.swift:3`. `await URLSession.data(for:)` suspends for I/O but **resumes on main**, so decode + DTO mapping are main-thread work.
- DTOs are already `Sendable` (`BathScaleOperationDTO`). `UnifiedEntryResult` has a hand-written decoder (23 `decodeIfPresent` + throwing `entryId` probe, `UnifiedEntryResponse.swift:121-155`); `.operations` recomputes the DTO remap on every access (`BathScaleOperationListResponse.swift:36-38`) — access it once and hold the result.

## 2. Approved decisions (all closed 2026-07-06 — do not re-ask)

| # | Decision |
|---|----------|
| 1 | **Option A: full off-main pipeline** (network/decode isolation included), delivered as **ONE single PR** |
| 2 | Background writer = **extend `SwiftDataWorker`** (not a new actor) |
| 3 | **Do not touch Kesavan's temporary capped-cursor patch** (uncommitted in the working tree — see §4). Implement for the production behavior: **sync mode returns ALL entries in one response** (`?start=1970-01-01T00:00:00Z`, `limit:nil`). No cursor fallback, no caps. |
| 4 | HealthKit: **skip historical backfill on first full sync** — forward only entries newer than a last-forwarded marker / integration enable date; incremental syncs forward normally. Settings read **once per sync**, not per entry |
| 5 | **Include the re-open/read-path fixes** in this same ticket/PR (indexes, `fetchLimit`, off-main hot reads, `lastActiveTime` re-init fix) |

## 3. Constraints & non-goals

- **No band-aids:** no spinners to hide freezes, no `Task.sleep`/debounce deferrals, no arbitrary caps.
- **Backward compatibility is a hard requirement** — legacy weight endpoints stay untouched (we don't change endpoints at all; only client-side isolation/batching).
- **iOS only.** Don't touch `Android/`.
- Repo conventions (`CLAUDE.md` + `iOS/CLAUDE.md`): `LoggerService` only for logging; repositories make network calls only; prefer actor isolation for mutable state; SwiftLint gates (`function_body_length` — extract helpers, see MOB-208 precedent); static strings in `Strings/` structs; keep `EntryService` public signatures stable where possible so Stores/Views churn is minimal.
- Build config is **Dev** (there is no "Debug" config — `-configuration Debug` silently builds Production).

## 4. Working-tree state & how to start (IMPORTANT)

The working tree (as of 2026-07-06) is on branch `MOB-1430-baby-growth-chart…` (= develop + 1 unrelated commit) and is **dirty**. Two groups of local changes — **neither belongs in the MOB-1433 PR**:

1. **Kesavan's temporary capped-cursor patch** (he removes it himself later — do NOT delete/commit it):
   - `iOS/meApp/Data/Services/EntryService.swift` (adds `fetchRemoteOperationsForSync`, `fetchCappedEntriesViaCursor`, `cappedFullSyncMaxEntries=1500`, `isSyncResultTooLargeError`)
   - `iOS/meAppTests/Features/Entry/Mocks/MockEntryRepositoryAPI.swift` (adds `fetchEntriesHandler`)
   - `iOS/meAppTests/Features/Entry/CappedCursorSyncTests.swift` (untracked)
2. **Unrelated local mods** (env/config tweaks — leave them out of the PR): `iOS/docs/XCCONFIG_STRUCTURE.md`, `project.pbxproj`, `Package.resolved`, `meApp.xcscheme`, `Dev.xcconfig`, `Production.xcconfig`, `Environment.swift`, `LoginForm.swift`, `BabyEntryView.swift`, `Info.plist`, `docs/sdlc-git-workflow.md`, `.claude-review/`.

**Start procedure:** confirm state with `git status`, then preserve everything non-destructively before branching:
```bash
git stash push -u -m "kesavan-local: temp capped-cursor patch + env tweaks (pre MOB-1433)"
git checkout develop && git pull
git checkout -b MOB-1433-batch-entry-sync-off-main-pipeline
```
Tell Kesavan the stash name so he can `git stash pop` on his own branch later. If he prefers to keep the dirty tree instead, get his explicit OK before proceeding (edits to `EntryService.swift` would tangle with his patch).

## 5. The changes — by workstream, file by file

### Workstream A — batched off-main merge & persist (fixes the freeze; the heart of the PR)

**A1. `iOS/meApp/Core/Services/SwiftDataWorker.swift`** (+ new extension file if lint requires, e.g. `SwiftDataWorker+EntryMerge.swift`)
- Add a **write/merge API** to the `@ModelActor`:
  - `applyRemoteOperations(_ ops: [BathScaleOperationDTO], accountId: String) -> EntryMergeResult`
    — port of `EntryService.mergeRemoteOperations` (`EntryService.swift:1295-1486`) with identical semantics, new mechanics:
    1. ONE fetch of local identity tuples for the account (`persistentModelID`/`id`, `serverEntryId`, normalized `entryTimestamp`, `serverTimestamp`, `isSynced`, `operationType`, weight for legacy dedupe) → build two dictionaries: by `serverEntryId`, by normalized timestamp. Replaces per-group `fetchEntry(byServerEntryId:)` (`:1326`), `fetchEntriesOfTimestamp` (`:1369`), and the pathological full-table `fetchEntries(forUserId:)` fallback (`:1389`).
    2. Group + resolve final op per entry exactly as today (`Dictionary(grouping:)` by `serverEntryId ?? entryTimestamp`, sort by `serverTimestamp`, last wins; `.000Z` normalization; weight-based duplicate rescue; `cleanupDuplicates` semantics).
    3. Apply inserts/updates/deletes in the actor's context in **chunks (~500) with one `save()` per chunk**.
    4. Return a `Sendable` `EntryMergeResult { newlyCreatedOps: [BathScaleOperationDTO], hadNewCreates: Bool, counts… }` — NO UI/HealthKit side effects inside the worker.
  - `insertEntries(_ dtos: …, accountId:)` batched (chunked, one save/chunk) — for the SQLite migration (A4).
  - `markEntriesSynced(byServerEntryIds…/ids…)` + `updateServerEntryIds(mapping…)` batched — for the push path (B1).
  - Batched baby-decigram update API for A5.
- Add **off-main read APIs** for the hot reads (Workstream D): all-snapshots, months-summary source data, all-DTOs; reuse the existing `Sendable` transfer-struct pattern (`EntryData`, `SwiftDataWorker.swift:17-120`). `toSnapshot()`/`toOperationDTO()` run inside the actor while the context is alive.
- *Why:* the ticket AC names `SwiftDataWorker` as the merge's home; it's the only off-main SwiftData access and already container-injected + tested.

**A2. `iOS/meApp/Data/Services/EntryService.swift`** — becomes a thin `@MainActor` orchestrator:
- `performSync()` (`:996`): push (B1) → fetch full history (one sync-mode response, per Decision 3) → `swiftDataWorker.applyRemoteOperations(...)` → if changed: ONE `entrySaved` emission (existing `getLatestEntry` pattern `:1458-1467`), `loadDashboardData()`, `updateProgressAndStreakInternal()` → HealthKit batch-forward (C1) → **delete the 3 s `Task.sleep` (`:1033`)** → `checkGoalAlerts()`.
- Delete `mergeRemoteOperations` + `cleanupDuplicates` (logic moves to worker; parity covered by tests). Mirror the same rewiring in the second sync path (`syncUnsyncedEntries`, ~`:1260-1290`). **Update (2026-07-07):** that second path was later deleted outright rather than rewired — see §5b.3.
- Keep `isSyncing`/`lastSyncTime` published on main as today.
- *Why:* `EntryService` must stay `@MainActor` for its `@Published` UI state, but the heavy machinery must not inherit that isolation.

**A3. `iOS/meApp/Data/Storage/DB/EntryRepository.swift`**
- **Delete** dead `EntryRepositoryBackgroundQueue` (`:18-36`).
- Keep the repo for cheap single-row UI-initiated CRUD, but fix the lies and the fat:
  - `fetchLatestEntry` (`:434-442`) / `fetchOldestEntry` (`:474-482`) / `fetchLatestEntryAsDTO` (`:593-610`): add `fetchDescriptor.fetchLimit = 1`.
  - `fetchEntryCount` (`:466-469`): use `context.fetchCount(descriptor)`.
  - Rename `performBackgroundTask` → `performTask` (or similar) with a doc comment stating it runs on the main actor — the name must stop lying. Heavy callers are being rerouted to the worker anyway.
- *Why:* dashboard's every-appear "latest entry" read becomes O(1)-ish with the index (E1) + limit.

**A4. `iOS/meApp/Data/Services/SQLiteMigrationService.swift`** (`:152-219`)
- Replace the per-row `entryRepository.saveEntry(entry)` inside the sqlite cursor loop (`:197`) with: accumulate rows → `swiftDataWorker.insertEntries(...)` chunked off main.
- *Why:* first launch after update migrates the whole legacy DB with one main-actor disk commit per row today.

**A5. `EntryService.migrateBabyEntriesToDecigrams()`** (`:948-969`): per-row `updateEntry` loop (`:959-962`) → one batched worker call.

### Workstream B — batch the push

**B1. `EntryService.pushUnsyncedEntriesToRemote`** (`:1125-1208`)
- Build ALL `UnifiedEntryRequest`s (baby entries may expand to 2 requests each), POST in **chunks of ≤100** via the existing array-accepting `remoteRepo.submitEntries` — the endpoint is atomic per request, so map response entries back to local rows per chunk (match today's `serverEntryId`/`entryTimestamp` reconciliation `:1177-1191`), then batch-update local sync status via the worker (A1).
- Preserve today's per-entry failure semantics where possible: a failed chunk marks its entries `attempts+1`/`isFailedToSync` as the current loop does; log via `LoggerService`.
- *Why:* N unsynced entries currently = N serial HTTP round trips, each with its own token check + encode/decode + 2 main-actor saves.

### Workstream C — HealthKit forwarding (Decision 4)

**C1. `iOS/meApp/Data/Services/IntegrationsService.swift`** (`syncNewEntry`, `:195-270`) + `HealthKitService` as needed
- Add `syncNewEntries(notifications: [EntryNotification])`: read `getStoredIntegrationData()` **once**, filter by the marker (below), then write to HealthKit off main (HealthKitService already detaches for its context work).
- **Marker:** persist a per-account `lastHealthKitForwardTimestamp` (same KV mechanism as `lastSyncTimestamp` in `localKVRepo`). First full-history sync: initialize the marker to "now" and forward nothing older (Decision 4). Incremental syncs: forward merged creates newer than the marker, then advance it.
- `EntryService.performSync` calls this once with the whole `newlyCreatedOps` batch instead of the per-entry loop (`:1472-1484`).
- *Open detail for implementer:* confirm where integration enable-date lives; if it exists, prefer `max(enableDate, marker)`.

### Workstream D — read path & re-open smoothness (Decision 5)

**D1. `EntryService` hot reads → worker:** `fetchAllEntrySnapshots` (`:283-286`), `getMonthsAll` (`:357-379`, keep the month-grouping/regex logic but run it in/behind the worker call), `getAllEntriesAsDTO` (`:312-315`), `performDashboardDataLoad` (`:2226+`: fetch + DTO map + signature hash off main; keep the already-detached aggregation). **Keep method signatures unchanged** so `HistoryStore`/`DashboardStore`/Views don't churn.
**D2. `iOS/meApp/Features/Common/ViewModels/ContentViewModel.swift`**
- `loadData()` (`:247-269`): flip `.dashboard` after **local** load (migrations + local snapshot read); run `syncAllEntriesWithRemote()` + `feedService`/`deviceService` refresh as a non-blocking `Task` behind the dashboard (existing `isSyncing` signals state). Keep the MOB-196 loader-flag semantics intact (`:146-170` defer block).
- Fix the foreground re-init: `AccountActivationSignature` (`:12-15`) currently includes `lastActiveTime`, so any account touch re-runs the whole init. Re-init fully only on `accountId` change; a `lastActiveTime`-only change refreshes account metadata without re-running migrations/sync/full reads. (Account *switching* must keep working — it changes `accountId`.)
**D3. `HistoryStore` / `DashboardLifecycleManager`:** no signature changes expected (D1 keeps APIs); verify the `entrySaved`-driven reload happens **once per sync** (single emission from A2) and remains cheap; dashboard's every-appear `getLatestEntry` becomes index+limit-backed (A3+E1).

### Workstream E — schema

**E1. `iOS/meApp/Domain/Models/DB/Entry.swift`**
- Add `#Index<Entry>([\.accountId], [\.serverEntryId], [\.entryTimestamp], [\.accountId, \.operationType])`.
- *Why:* every predicate filters on these, all unindexed today. Additive schema change → SwiftData lightweight migration; **must verify** on a store with existing data (no migration plan exists in the app — `PersistenceController.swift:66-72`).

### Workstream F — network/decode off main (Option A part)

**F1. `iOS/meApp/Core/Network/HTTPClientProtocol.swift`** (`:3`): drop `@MainActor` from the protocol.
**F2. `iOS/meApp/Core/Network/HTTPClient.swift`** (`:10`): drop `@MainActor`; make the class `final class … : Sendable` (or an `actor` if mutable state demands — inspect `requestExecutor`, token-refresh single-flight). Token expiry check (`:101-112`) hops to the main-actor account service via `await`. Decode (`:191`) then runs off main. Keep DEBUG body-logging out of the hot path (guard or move off main).
**F3. `iOS/meApp/Data/API/EntryRepositoryAPI.swift`** (`:3`) and the other repos in `iOS/meApp/Data/API/` that conform to/consume `HTTPClientProtocol`: drop `@MainActor`, add `Sendable` conformance as needed. All call sites already `await`, so this is mostly annotation surgery + mock updates — but it is the widest-blast-radius part of the PR; compile early, compile often.
**F4.** Hold the decoded `.operations` array once per response (it's a computed remap, `BathScaleOperationListResponse.swift:36-38`).

### Workstream G — tests (`iOS/meAppTests/`)

- **Merge parity suite** (new, e.g. `Features/Entry/BatchedMergeTests.swift`): create/update/delete/dedupe/legacy-timestamp-grouping/`.000Z` normalization/weight-duplicate-rescue — same fixtures through old semantics expectations. Reuse `MockEntryRepository`, `MockEntrySyncStore`, `EntryServiceExtendedTests.makeSUT` patterns.
- **Push batching tests:** chunking, partial-failure marking, serverEntryId reconciliation.
- **Gating tests:** `ContentViewModel` flips to `.dashboard` before sync completes; `lastActiveTime`-only change does NOT re-run sync; `accountId` change does.
- **HealthKit marker tests:** first sync forwards nothing historical; incremental forwards new-only; settings read once.
- **Isolation updates:** mocks lose `@MainActor` where protocols changed (`MockHTTPClient` etc.).
- Leave `CappedCursorSyncTests.swift` alone (Kesavan's temp file, stashed anyway).

## 5a. Implementation status (2026-07-06) — SHIPPED

All workstreams landed on branch `MOB-1433-entry-sync-batch-...` as 7 commits:

| Commit | Scope | SHA |
|--------|-------|-----|
| 1 | E1 `#Index<Entry>` + A3 repo `fetchLimit`/`fetchCount`, delete dead queue, rename `performBackgroundTask`→`performTask` | `0d8177674` |
| 2 | A1 `SwiftDataWorker` batched merge/insert/push/read APIs (`EntryWorkerProtocol`) + `BatchedMergeTests` parity suite | `c8df162d6` |
| 3 | A2/A4 reroute sync + SQLite migration through the worker; delete per-row `mergeRemoteOperations`/`cleanupDuplicates`; **remove the 3 s sleep** | `a4c48cf39` |
| 4 | B1 chunked (`≤100`) batch `POST /v3/entries/` + `applyPushOutcomes` bookkeeping | `1f997aa8e` |
| 5 | C1 batched HealthKit forward + per-account `lastHealthKitForwardTimestamp` marker (first-sync skip) | `aa78026e6` |
| 6 | F decode off the main actor (see scope note) | `44e86dcf0` |
| 7 | D1 hot reads (`getAllEntriesAsDTO`/`fetchAllEntrySnapshots`) via worker; D2 dashboard un-gating + `lastActiveTime` re-init fix | `19b38ac64` |

**Two deliberate scope adjustments (flagged, not silent):**
- **F (network isolation) — narrowed.** Instead of dropping `@MainActor` from `HTTPClientProtocol`/`HTTPClient`/the 10 API repos, only the CPU-bound **decode** moved off main (a `nonisolated` async helper). Full de-isolation would move `@Injector`/`DependencyContainer.shared` (a non-thread-safe dictionary) access off main and force de-isolating the DI container + every repo — too broad to land safely without device test runs. URLSession I/O already runs off-thread; decode was the real main-thread cost. Full de-isolation remains a possible follow-up.
- **A5 (`migrateBabyEntriesToDecigrams`) — not rewritten.** Its per-row loop is a one-time, flag-gated, baby-only path (tens of rows, not 5k–10k) and not a freeze contributor. Left as-is; batch it later if profiling flags it.

## 5b. Post-ship follow-ups (2026-07-07)

Two correctness/robustness fixes plus a dead-path removal landed on the branch after the initial 7-commit ship. All three touch `SwiftDataWorker+EntryMerge.swift`, `EntryService.swift`, and `BatchedMergeTests.swift`.

### 5b.1 Delete-resurrection bug — the merge now applies deletes in a second pass

**Symptom:** a deleted entry reappeared on the next full sync.

**Root cause:** `applyRemoteOperations` grouped every op by `serverEntryId ?? entryTimestamp`, then matched the group's final op to a local row by that same key. But the server assigns a **fresh `entryId` to every operation** — a create and the delete that removes it carry *different* `serverEntryId`s and share only `(entryTimestamp, category)`. A delete references its target by `(timestamp, category)`, never by its own id. So each delete was stranded in its own single-op `serverEntryId` group, matched no local row, and was silently dropped — resurrecting the row on the next sync.

**Fix:** resolve each group to its latest op (by `serverTimestamp`), then apply in **two passes**:
1. **Pass 1 — creates/updates** (unchanged semantics; keyed by `serverEntryId`, or legacy `entryTimestamp` when absent).
2. **Pass 2 — deletes**, via the new `mergeDelete(finalOp:context:)`, which resolves the target row(s) by **normalized `entryTimestamp` + `entryType`** (never by the delete op's own id) and removes them latest-wins by `serverTimestamp`. Splitting the passes guarantees the target row is materialized before any delete resolves it, independent of group-iteration order. When the op carries a `category`/`entryType`, the match is restricted to that product so a delete never removes another product's entry recorded at the same instant. One notification per delete (prior `deleteRow(notify:)` semantics).

**Tests added** (`BatchedMergeTests` → "Cross-operation delete identity (MOB-1433)"):
- `deleteWithDistinctServerIdRemovesTargetByTimestamp` — full-sync payload (create `1069154` + its delete `1069155` + separate create `1069156`, all distinct ids): only the 250 entry survives; the deleted 100 does not resurrect.
- `deleteRemovesAlreadySyncedRowByTimestamp` — an incremental sync returning ONLY the delete removes the previously-synced local row it targets by timestamp.
- `staleDeleteDoesNotRemoveNewerRow` — an older delete (`serverTimestamp` before the local row's) does not win.

### 5b.2 Eager push after every local mutation — shrink the data-loss-on-kill window

**Problem:** a just-made add/edit/delete stayed local until the next Dashboard/History/foreground sync; killing or uninstalling the app in between lost it.

**Fix (`EntryService`):** subscribe once in `init` to `Publishers.Merge(entrySaved, entryDeleted)`, debounce 500 ms (so an edit = delete+add collapses into one push), and call the new **`pushPendingEntries()`** to flush unsynced rows to the server immediately. It is:
- **Push-only** (no remote pull) → cheap enough to run per mutation.
- **Coalesced** via a new `pendingPushTask` — concurrent eager pushes await the in-flight one instead of racing.
- **Piggybacked** — if a full sync is running (`activeSyncTask`) it awaits that instead of pushing concurrently; conversely `syncEntries` now awaits any in-flight `pendingPushTask` before its own push. The two never double-submit the same unsynced rows.
- **Offline-guarded** — skips when `NetworkMonitor.shared.isConnected` is false, so it doesn't burn the 8-try attempt budget; rows stay queued for the next online trigger.

### 5b.3 Removed dead `syncUnsyncedEntries`

The private push-then-pull "sync after local changes" path (the one §5 A2 planned to "mirror the rewiring" into) is deleted outright — it had no remaining callers, and its role is now covered by eager push (immediate push, 5b.2) plus the full `syncEntries` (pull).

## 5c. Interactive-flow jerk on a real 10k account (2026-07-08) — ✅ SHIPPED (commit `c3d790de4`, PR #2218)

Re-tested on a physical device with a real **10,003-entry** account (`barath@icloud.com`) after an uninstall/reinstall (fresh local store → full history pulled by the background sync).

**Kesavan's scope directive for this pass (narrow — honor it):**
- Goal = a **clean, jerk-free _manual-entry save_ and _History open_**. Nothing else.
- **Explicitly out of scope for now:** the dashboard graph/metrics recompute and any regression it causes. The durable fix — **incremental aggregation** (stop recomputing streak/progress/summaries over all 10k on every save/sync) — is acknowledged as a larger piece and **deferred**; Kesavan will handle it later. Do NOT block this pass on it.

**Symptoms observed (after the fixes below were already applied):**
- Home/dashboard opens fine.
- Manual entry save shows **no loader** and feels jerky — recurs every launch.
- History tab: on tap, the tab is slow to select and the **loader does not paint**.

Key interpretation: **a loader cannot render while the main thread is blocked.** So "no loader" is either (a) the work is now off the critical path and the save is instant (loader flashes — success), or (b) main is still blocked and the loader never gets a frame. The timing logs below disambiguate.

**Changes (landed and merged — commit `c3d790de4` "Optimize entry data fetching and UI responsiveness for large accounts", part of PR #2218; symbols verified in the merged tree: `scheduleProgressAndStreakRefresh`, `fetchAllEntryData`, `isHistoryScreenActive`):**
1. **`getMonthsAll` moved off-main** — new `SwiftDataWorker.fetchAllEntryData(...)` (`[EntryData]`, mirrors `fetchEntriesAsDTO`) + a shared `EntryData(from: Entry)` init + a `nonisolated static buildMonthsAll(...)` run in a detached task. History no longer materializes 10k `@Model` rows on the main actor (it was the only hot read the initial D1 commit left on main). Added to `EntryWorkerProtocol` + `MockEntryWorker` (projects from `backingRepo`); `getMonthsAll` tests still pass.
2. **Manual save no longer awaits the full-dataset recompute** — `handleEntryAdded` now emits `entrySaved` + updates the affected day/month summaries **first**, then fires `updateProgressAndStreakInternal()` **behind the UI** via `scheduleProgressAndStreakRefresh()` (coalesced through a new `progressRefreshTask`, so a bulk save = one trailing recompute). The 8 s save-loader was this recompute (`getStreak()` re-reads all 10k) being awaited inline. `performSync` still awaits it directly (already off the UI's critical path).
3. **Removed redundant launch reload** — `ContentViewModel.startBackgroundDataSync` no longer re-runs `loadDashboardData` + `fetchAllEntrySnapshots` after the sync (both already ran in `loadData`; `performSync` reloads the dashboard itself only when data changes; `ContentViewModel.entries` is consumed by no view). Eliminates one full 10k worker read + one 10k snapshot build per launch. `ContentViewModelTests` updated (`loadDashboardDataCalls`/`fetchAllEntrySnapshotsCalls` 2 → 1).

**Option A — temporary timing instrumentation (added this session; MUST be removed before the PR):**
Elapsed wall-clock per stage, logged under the **`PERF`** tag (filter device logs by `PERF`). Sites:
- `EntryStore.saveNewEntry (loader span)` — total user-perceived save time (loader show→dismiss).
- `EntryService.saveNewEntry`: `localRepo.saveEntry`, `handleEntryAdded`, `checkGoalAlerts`.
- `EntryService.handleEntryAdded`: `getEntries(day+month)`, `aggregate+updateSummaries`, `syncNewEntry`.
- `EntryService.getMonthsAll`: `worker.fetchAllEntryData(count=…)`, `buildMonthsAll`.
- `HistoryStore.getMonthsAll (loader span)` — total History-open time.
- `ContentViewModel.loadData`: `loadDashboardData`, `fetchAllEntrySnapshots(count=…)`.

**How to read:** do one cold launch, one manual save, one History open; grep the log for `PERF`. The stage with the multi-hundred-ms/second cost is the blocker. Expected suspects: SwiftData main-context vs worker background-context **store contention** while the launch reads churn (both `loadData` reads + the eager push run around the same window), and `getEntries(forDay/forMonth)` still on `localRepo` (main).

**Measured on device (2026-07-08, 10,010-entry account) — `PERF` results:**

| Stage | Time | Verdict |
|-------|------|---------|
| `loadData.loadDashboardData` | **4877 ms** | launch critical path — froze the loading dots |
| `loadData.fetchAllEntrySnapshots(10010)` | **1792 ms** | launch critical path, populates the **UI-unused** `entries` — pure waste |
| `getMonthsAll.worker.fetchAllEntryData(10010)` | **1804–3822 ms** | History open (the loader span) |
| `getMonthsAll.buildMonthsAll` | 22–25 ms typical, **3821 ms** once | the 3.8 s instance is pure CPU **starvation** — everything churning at once |
| save `localRepo.saveEntry` / `getEntries(day+month)` / `aggregate+updateSummaries` / `checkGoalAlerts` | 5 / 14 / 10 / 6 ms | all fine |
| save `handleEntryAdded.syncNewEntry` | **1794 ms** | the ENTIRE save delay — and HealthKit is OFF on this account |

**Root cause (confirmed):** the `SwiftDataWorker` `@ModelActor`'s big 10k fetches materialize ~10k rows and fault 2 to-one relationships each (~20k store hits), holding the shared persistent store. Every main-context operation serializes behind them — that's why `getStoredIntegrationData()` (one tiny record) cost 1.8 s inside `syncNewEntry`, and why a detached CPU-only `buildMonthsAll` was starved to 3.8 s. It's **store-lock + CPU contention between the worker and the main context**, triggered by full-dataset reads piling onto the launch/interactive window.

**Fixes applied this session (round 2, 2026-07-08) — scoped to the entry + History flow per Kesavan:**
1. **Save no longer blocks on the HealthKit forward** — `handleEntryAdded` fires `integrationService.syncNewEntry(notification:)` in a detached `Task` (best-effort; row is already persisted + queued for push). Save loader span drops from ~1830 ms to the sum of the fast stages (~35 ms).
2. **Loading screen no longer does full-dataset reads** — `loadData()` now runs **migrations only**; the ~4.9 s `loadDashboardData` moved into `startBackgroundDataSync()` (behind the visible dashboard) and the ~1.8 s `fetchAllEntrySnapshots` is **deleted** (nothing consumes `ContentViewModel.entries`). `ContentViewModelTests` updated (`fetchAllEntrySnapshotsCalls` 1 → 0; dropped the `entries.count` assertion).
3. **Relationship prefetch on the 10k reads** — `SwiftDataWorker.fetchAllEntryData` + `fetchEntriesAsDTO` set `relationshipKeyPathsForPrefetching = [\.scaleEntry, \.scaleEntryMetric]` so a full read batch-faults instead of doing ~20k individual store hits — shrinks the lock window that starves main-context work. Pure perf, no behavior change.

**Fixes applied this session (round 3, 2026-07-08):**
4. **Save shows a success toast** — the save is now ~45 ms, so the loader can't visibly appear; all three manual-entry saves (`saveEntry` weight, `saveBPEntry`, `saveBabyEntry`) show a `ToastStrings.success` / `entryAdded` toast (global overlay, persists across the post-save tab switch). Post-save auto-navigation to the Dashboard is unchanged (Kesavan's call to leave it).
5. **History off-screen reloads gated** — `HistoryStore` gained `isHistoryScreenActive` (set by `HistoryListScreen` on tab change/appear). The `entrySaved`/`entryDeleted` handlers **and** the product-type / account-unit publishers now eagerly re-read the months list ONLY when History is on screen; off-screen they just invalidate the cache and let the next open reload. This removes a stray full ~10k read on every off-screen save **and** the one the account-refresh publisher fired at launch (which was contending with the dashboard load and starving `buildMonthsAll` to ~1.1 s).

**Still open / expected residual (needs re-measure):**
- **History first-open** still does one full `fetchAllEntryData` (10k). Prefetch should cut it materially, and removing the launch reads stops it competing — but it will not be *instant* until the durable fix. Re-measure `HistoryStore.getMonthsAll (loader span)` after round 2.
- **Dashboard is the landing tab** and its own managers (`DashboardLifecycleManager.loadDashboardData`, `DashboardStreakManager.getProgress`, `DashboardMetricsManager.getAllEntriesAsDTO`) still fire full-dataset reads on appear — these can still contend with an immediate History/save. This is **the deferred dashboard/metrics work** Kesavan owns (incremental aggregation); out of scope here, noted as the real durable fix.

**Next steps (data-driven):**
1. Re-run one cold launch + one save + one History open; compare the `PERF` numbers to the table above.
2. If History is still heavy, the durable fix is incremental aggregation / denormalizing `weight` onto `Entry` (Kesavan's follow-up) so month/summary reads don't fault 10k relationships.
3. ~~Remove all `PERF` traces + the two temporary `perfTrace` helpers before committing.~~ **Done (2026-07-08)** — all `PERF` logging, the `perfTrace` helpers (`EntryService`, `ContentViewModel`), and the inline blocks (`EntryStore`, `HistoryStore`) are removed. The `§5c` code comments that remain document the real fixes and stay.

## 6. Suggested commit sequence (single PR, reviewable commits)

1. `MOB-1433` E1 schema index + A3 repo `fetchLimit`/`fetchCount` + delete dead queue actor
2. `MOB-1433` A1 SwiftDataWorker merge/write APIs + G merge-parity tests
3. `MOB-1433` A2/A4/A5 reroute sync+migrations through worker; remove 3 s sleep
4. `MOB-1433` B1 batched push + tests
5. `MOB-1433` C1 HealthKit batch + marker + tests
6. `MOB-1433` F1–F4 network isolation off main + mock updates
7. `MOB-1433` D1–D3 read path via worker + ContentViewModel un-gating/`lastActiveTime` fix + tests
8. Docs: update `iOS/architecture.md` if it describes the old repo pattern

## 7. Build & verify

```bash
# Build (Dev config — a "Debug" config does NOT exist)
cd iOS && xcodebuild build -project meApp.xcodeproj -scheme meApp \
  -destination 'generic/platform=iOS' -configuration Dev \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO

# Unit tests
cd iOS && xcodebuild test -project meApp.xcodeproj -scheme meApp \
  -destination 'platform=iOS Simulator,name=iPhone 16,OS=latest' \
  -only-testing:meAppTests \
  CODE_SIGN_IDENTITY="" CODE_SIGNING_REQUIRED=NO CODE_SIGNING_ALLOWED=NO
```

Verification evidence to produce (Phase 3):
- Main Thread Checker / actor-isolation reasoning per AC ("fetch, decode, persist off main; main actor only for UI state") — cite the isolation of each pipeline stage post-change.
- Instruments Time Profiler / hang report on a seeded large account (Kesavan validates on a physical device; epic gate: **no main-thread block > 250 ms** during login → dashboard).
- Re-open checks: History open / month push / dashboard re-appear with 5k+ local entries — no perceptible hang; foregrounding does not re-sync.
- Lightweight migration check: app upgrade path with existing on-disk store + new `#Index` opens cleanly and keeps data.

## 8. Acceptance criteria (from MOB-1433 + task brief — final checklist)

> ✅ **All criteria met — shipped in PR #2218** (see §5a for the commit map; the boxes below are left unticked
> as the original brief text). The 10k-device Instruments gate + the §5c interactive-flow follow-ups also landed.

- [ ] Merge diffs against one up-front in-memory map (serverEntryId / normalized timestamp), no per-row fetches, applied in a background `ModelContext` (`SwiftDataWorker`) with chunked saves
- [ ] Loading screen not gated on remote sync; `.dashboard` after local load; sync behind `isSyncing`
- [ ] Push batched into chunked `POST /v3/entries/` calls
- [ ] HealthKit batched + marker-gated (no historical flood); settings read once per sync
- [ ] `fetchEntryCount` → `fetchCount()`; 3 s sleep removed; `#Index` on `Entry`
- [ ] Fetch/decode/persist off the main thread (Option A: network layer isolation dropped)
- [ ] Re-opening entry screens serves local SwiftData smoothly; no blocking re-sync on foreground
- [ ] Merge parity + new-behavior unit tests pass; existing suite green; backward compat preserved
- [ ] 10k-entry device profile: no main-thread block > 250 ms during init (epic MOB-516 gate)

## 9. Open details to resolve while coding (small, don't block start)

1. HealthKit marker storage location (reuse `localKVRepo` pattern) + whether an integration enable-date already exists to combine with it.
2. `HTTPClient`: plain `Sendable` class vs `actor` — decide by its mutable state (token refresh single-flight). Prefer the least-change option that compiles under strict concurrency.
3. Chunk sizes (merge ~500/save; POST ≤100/call) — tune if profiling says otherwise.
4. Whether `getMonthsAll`'s regex-per-group survives or gets simplified while moving off main (keep output identical either way).
5. `EntrySnapshot`/DTO `Sendable` audit — add conformances where the compiler demands.
