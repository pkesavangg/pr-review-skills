# MOB-1433 — Entry fetch/sync UI hangs: root-cause report (Phase 1)

**Ticket:** [MOB-1433](https://greatergoods.atlassian.net/browse/MOB-1433) · Epic: MOB-516 (5.1.0 Performance Hardening)
**Scope:** iOS only. Symptoms: (1) app freezes at the loading screen for minutes on first login with 5k–10k entries; (2) UI stutters/hangs when re-opening entry screens (History, Dashboard).
**Status:** Phase 1 — investigation only. No product code changed. **Awaiting approach sign-off (see Decisions at the bottom) before Phase 2.**

---

## 1. The pipeline, traced end to end

Every stage below runs on the **main actor**. That is the single architectural root cause; everything else is a multiplier.

```
ContentViewModel.loadData()                          @MainActor  (ContentViewModel.swift:247)
  ├─ migrateFromSQLiteIfNeeded()                     per-row saveEntry on main (SQLiteMigrationService.swift:190-208)
  ├─ migrateBabyEntriesToDecigrams()                 per-row updateEntry on main (EntryService.swift:959-962)
  ├─ syncAllEntriesWithRemote() → performSync()      @MainActor  (EntryService.swift:996)
  │    ├─ pushUnsyncedEntriesToRemote()              1 POST per entry, serial (EntryService.swift:1140-1165)
  │    ├─ FETCH  GET /v3/entries?start=1970-01-01    whole history, one response, limit:nil (EntryService.swift:1061)
  │    │    └─ EntryRepositoryAPI @MainActor (EntryRepositoryAPI.swift:3)
  │    │       └─ HTTPClient @MainActor (HTTPClient.swift:10)
  │    │          └─ await URLSession.data(for:)     I/O off-thread, but resumes ON MAIN (HTTPClient.swift:147)
  │    ├─ DECODE JSONDecoder().decode(...)           whole array, one shot, ON MAIN (HTTPClient.swift:191)
  │    │       custom per-entry decoder: 23 decodeIfPresent + throwing entryId probe
  │    │       (UnifiedEntryResponse.swift:121-155), then per-entry DTO remap ~11 Int→Double
  │    │       (UnifiedEntryResponse.swift:167-202, recomputed on every `.operations` access)
  │    ├─ MERGE  mergeRemoteOperations()             per-entry, ON MAIN (EntryService.swift:1295-1486)
  │    │    ├─ fetchEntry(byServerEntryId:) per group  → unindexed table scan (EntryRepository.swift:349-356)
  │    │    ├─ legacy path: fetchEntries(forUserId:) per group on timestamp miss → FULL TABLE per group
  │    │    │    (EntryService.swift:1389)
  │    │    ├─ saveEntry/updateEntry/deleteEntry per row → one ctx.save() DISK COMMIT each
  │    │    │    (EntryRepository.swift:196, 231, 262)
  │    │    └─ integrationService.syncNewEntry per new entry → settings re-read + HealthKit write ×N
  │    │         (EntryService.swift:1472-1484, IntegrationsService.swift:195-270)
  │    ├─ loadDashboardData()                        full-table DTO read ON MAIN (EntryService.swift:2207)
  │    ├─ updateProgressAndStreakInternal()          fetchEntryCount = fetch-all + .count (EntryRepository.swift:466-469)
  │    └─ try? await Task.sleep(3s)                  (EntryService.swift:1033)
  ├─ loadDashboardData(.scale)                       full table AGAIN
  ├─ fetchAllEntrySnapshots()                        full table + toSnapshot() ×N + 4 relationship faults/row
  │                                                   (EntryService.swift:283-286)
  └─ …feed, device sync
       ↓ only then
ContentViewModel.updateViewState → .dashboard        (ContentViewModel.swift:200, 273-276)
```

**Why it presents as "stuck":** `contentViewState` stays `.initializing` until the entire chain finishes; the main actor is saturated, so even `LoadingDotsView`'s animation can't tick. No timeout, no progress.

### The lynchpin: a "background" task that isn't

```swift
// EntryRepository.swift:57-60  (class is @MainActor, line 43)
private func performBackgroundTask<T>(_ work: (ModelContext) throws -> T) async throws -> T {
    let backgroundContext = ModelContext(container)
    return try work(backgroundContext)   // no Task.detached, no actor hop → runs inline on MAIN
}
```

All **27** call sites (every entry read and write in the app) execute synchronously on the main thread. The actor built to fix this (`EntryRepositoryBackgroundQueue`, EntryRepository.swift:18-36, MA-3898) is **defined but never called**. The one real off-main reader, `SwiftDataWorker` (`@ModelActor`, SwiftDataWorker.swift:133), is used for exactly one call (`fetchProgressData`, EntryService.swift:422) and has **no write API**.

### Schema multiplier

`Entry` (Entry.swift:17) has **zero `#Index`** (none exists anywhere in the project) and only `id` unique. Every predicate filters on unindexed `accountId`/`serverEntryId`/`entryTimestamp` → full scans. Each row faults **4 unconfigured to-one relationships** (`scaleEntry`, `scaleEntryMetric`, `bpmEntry`, `babyEntry`, Entry.swift:47-50) when mapped. `EntryRepository` uses **no `fetchLimit` anywhere** — even `fetchLatestEntry` sorts the whole table and takes `.first` (EntryRepository.swift:434-442).

**Order of magnitude at N = 5k–10k:** merge = N unindexed lookups (O(N²) row visits) + N disk commits (~1–10 ms each → 10 s–2 min alone) + N HealthKit forwards, all serialized on main. Plus ≥3 more full-table reads (`loadDashboardData`, `fetchAllEntrySnapshots`, `fetchEntryCount`/`getStreak`) in the same init window, plus a one-shot decode of the full JSON body on main, plus the fixed 3 s sleep. Minutes of frozen UI is the expected outcome, not an anomaly.

---

## 2. Symptom 2 — stutter on screen (re)open

The re-open path never uses the network; it's pure SwiftData — but every read is full-table, on main, unindexed:

| Trigger | What runs | Shape at 5k–10k |
|---|---|---|
| History tab select | `HistoryStore.loadMonths()` → `getMonthsAll()` → `fetchEntries(forUserId:opType:)` (EntryRepository.swift:321-333) | full `create` table + grouping + per-group regex + sort, on main (EntryService.swift:357-379) |
| History, baby mode | `fetchAllEntrySnapshots()` (EntryService.swift:283) | full table + `toSnapshot()` ×N + percentile math on main (HistoryStore.swift:342-368, 940-999) |
| Month detail push (every push = new view) | `loadEntries(for:)` → `fetchEntries(forMonth:)` (EntryRepository.swift:365-384) | month-scoped fetch + dedupe group/sort on main (HistoryStore.swift:396-424) |
| Dashboard **every** `.onAppear` | `getLatestEntry()` → `fetchLatestEntry` (EntryRepository.swift:434-442) | **full sorted table → `.first`**, on main (DashboardLifecycleManager.swift:759-762) |
| Dashboard data load | `getAllEntriesAsDTO()` (EntryRepository.swift:504-522) | full table + `toOperationDTO()` ×N on main; only the aggregation after it is detached (EntryService.swift:2243-2260). The "cache hit" check still pays the full fetch + full-array hash first (EntryService.swift:2230-2249) |
| App foreground | `ContentViewModel` account-publisher dedupe keys on `accountId` **+ `lastActiveTime`** (ContentViewModel.swift:12-15, 87-93); AccountService rewrites `lastActiveTime` on refresh → foreground can re-run the **entire** `loadData()` incl. full sync | the whole Section 1 pipeline again |
| Any `entrySaved` event | `HistoryStore` re-runs `loadMonthsInternal` (full re-read) per event (HistoryStore.swift:105-165) | during a sync that merges M new entries, stores re-read the table repeatedly |

Two more structural facts:

- **The UI never observes SwiftData.** No `@Query`, no `.modelContainer`, no cross-context change notifications anywhere. Refresh = manual full refetch + `@Published` republish. (This is good news for the fix: a background writer + one refetch fits the existing pattern with no observation rewiring.)
- **The server cursor pager is dead code w.r.t. the UI.** `HistoryStore.loadFirstPage/loadNextPage/pagedEntries` (HistoryStore.swift:688-724) are referenced by no view. History renders full in-memory arrays.

---

## 3. About the temporary capped-cursor patch in the working tree

The uncommitted edits (`fetchRemoteOperationsForSync` / `fetchCappedEntriesViaCursor`, cap = 1500, + `CappedCursorSyncTests.swift`) are a **temporary local patch**; production code fetches the entire history in one sync-mode response.

Two findings about it, for the record:

1. **It doesn't touch the root cause.** ≤5000-entry accounts still receive the full payload in one response, and the per-entry main-actor merge/persist behind it is unchanged — a 1500-entry merge is still ~seconds of main-thread block.
2. **It silently truncates history.** Because the sync timestamp is persisted after the capped read, older entries are never backfilled — and since the History UI has no server-paging path (dead pager, above), a >5000-entry account would permanently see only its newest 1500 entries. The patch's own comment assumes "History's on-scroll server paging" serves older data; that paging is not wired to any view.

The real fix below replaces this patch with an **uncapped**, batched, off-main sync (keeping the useful part: the sync-mode-refusal detection and cursor fallback shape).

---

## 4. Proposed fixes

Both options share the same core (the ticket's ACs); they differ in how far off the main actor the *network/decode* stage moves.

### Core (in both options)

1. **Batched off-main merge + persist.** Give `SwiftDataWorker` (@ModelActor) a write API. New merge algorithm:
   - one background fetch of local identity tuples (`serverEntryId`, normalized `entryTimestamp`, `id`, `serverTimestamp`, `isSynced`) → in-memory dictionary;
   - diff all remote ops against it (pure Swift, `Sendable` DTOs — they already are);
   - apply inserts/updates/deletes in the worker's context in chunks (e.g. 500/batch), **one `save()` per chunk** — same semantics as today's create/update/delete/dedupe rules, proven by parity unit tests;
   - one `entrySaved` notification + one main-actor refetch of published state at the end (matches the app's existing manual-refresh pattern; no SwiftData observation rewiring needed).
2. **Stop gating the loading screen on sync.** `loadData()` flips to `.dashboard` after *local* load (migrations if trivially fast + local read); `syncAllEntriesWithRemote()` continues behind the dashboard via the existing `@Published isSyncing`. Dashboard/History already re-render on `entrySaved`/publisher events when the merge lands.
3. **Full sync without a giant one-shot payload and without caps:** try sync mode; on the server's >5000 refusal (detection logic from the temp patch), fall back to **uncapped** cursor paging at `maxLimit=100`, feeding each page into the batched merge as it arrives. Bounded memory, incremental progress, no data loss. Subsequent syncs stay sync-mode deltas via the persisted timestamp.
4. **Batch the push:** `submitEntries` already accepts an array (EntryRepositoryAPI.swift:12) — chunked POSTs (≤100/call per server page conventions), then batch-update local sync status in one save.
5. **Batch/defer HealthKit forwarding:** read integration settings once per sync; forward merged entries as a batch off main (see Decision 4 for first-sync policy).
6. **Read path:** add `#Index` on `Entry` (`accountId`, `serverEntryId`, `entryTimestamp`); `fetchLatestEntry`/`fetchOldestEntry` get `fetchLimit = 1`; `fetchEntryCount` uses `fetchCount()`; move the hot full-table reads (`getAllEntriesAsDTO`, `getMonthsAll`, `fetchAllEntrySnapshots`) into `SwiftDataWorker` so History/Dashboard (re)opens don't block main; remove the 3 s sleep; fix the `lastActiveTime` re-init trigger so foregrounding doesn't re-run the full pipeline (dedupe on `accountId` only, refresh account metadata without re-sync).

### Option A — full off-main pipeline (recommended)

Also move **network + decode** off the main actor for the entries path: `EntryRepositoryAPI` and `HTTPClient` lose `@MainActor` (become plain `final class`/actor with `Sendable` conformance; token check via `await` into the main-actor account service). Decode of a 5000-entry body then happens on a background thread.

- **Pros:** satisfies the ticket AC literally ("fetch, decode, persist off main"); removes the last 100–300 ms-class main-thread hit (one-shot decode + per-entry DTO remap); benefits every other API call in the app.
- **Cons:** `HTTPClient` isolation change touches every service that conforms to/consumes `HTTPClientProtocol` (broad but mechanical — all call sites already `await`); mocks/tests need isolation updates; the riskiest part of the PR to review.

### Option B — merge/persist/reads off-main only (lower risk)

Keep `HTTPClient`/`EntryRepositoryAPI` on `@MainActor`. Decode stays on main, but with the uncapped cursor paging (100 entries/page) each decode is small; the single big sync-mode decode only occurs for ≤5000-entry accounts (~one 100–300 ms hitch behind the dashboard, not the loading screen).

- **Pros:** much smaller blast radius; still eliminates the minutes-long hang (merge/persist dominates by 2–3 orders of magnitude); ship-fast.
- **Cons:** doesn't literally meet "decode runs off the main thread" — the AC would need a note; occasional decode hitch remains measurable in Instruments for large sync-mode responses; the network layer stays an architectural debt item.

**My recommendation: A**, executed as two reviewable PRs — PR-1 = the Core items (merge/persist/reads/gating; this alone fixes both symptoms), PR-2 = the network/decode isolation change. If time pressure hits, PR-1 alone ≈ Option B.

---

## 5. Verification plan (Phase 3 preview)

- Unit tests: batched-merge parity suite (create/update/delete/dedupe/legacy-timestamp grouping vs. current behavior), chunked-push tests, uncapped-cursor-fallback tests (replacing the temp patch's capped tests).
- Instruments/MainThreadChecker evidence on a seeded 10k-entry account (Profile build, physical device): login → dashboard, no main-thread block > 250 ms (epic MOB-516 gate); History open / month push / dashboard re-open hitch-free.
- Backward-compat: legacy weight endpoints untouched; existing local data upgrades in place (index addition is additive — verify lightweight migration on a store with existing rows).

---

## 6. Decisions needed (pick one per item — edit this file inline)

**Decision 1 — approach**
- [ ] **A. Full off-main pipeline** (Core + network/decode isolation, two PRs) ← recommended
- [ ] **B. Core only** (merge/persist/reads off-main; decode stays on main, AC note)

**Decision 2 — where the background writer lives**
- [ ] **Extend `SwiftDataWorker`** with entry write/merge APIs ← recommended (it's the ticket AC's named home; container-injected, tested)
- [ ] **New dedicated `@ModelActor` (e.g. `EntrySyncWorker`)** keeping `SwiftDataWorker` read-only

**Decision 3 — the temporary capped-cursor patch + branch housekeeping**
Current state: branch `MOB-1430-…` = develop + 1 unrelated commit; the capped-cursor edits + `CappedCursorSyncTests.swift` are uncommitted on top.
- [ ] **Discard the temp patch**; MOB-1433 branch starts clean from `develop`, reimplementing refusal-detection + uncapped fallback properly ← recommended (avoids shipping the 1500-entry truncation)
- [ ] **Keep the temp patch as an interim commit** (its own PR) and layer MOB-1433 on top

**Decision 4 — HealthKit forwarding on FIRST full-history sync**
- [ ] **Forward everything, batched off main** (full parity with today's intent; 10k historical samples pushed to Apple Health once)
- [ ] **Skip historical backfill on initial sync** — forward only entries newer than the integration's enable date / last-forwarded marker; incremental syncs forward normally ← recommended (avoids flooding Health with decade-old rows; matches MA-3886's actual goal of catching *new* Wi-Fi entries)

**Decision 5 — scope of the re-open fixes in this ticket**
- [ ] **Include** index + `fetchLimit` + off-main hot reads + `lastActiveTime` re-init fix (all evidence-linked to the stutter symptom) ← recommended
- [ ] **Split** the re-open/read-path work into a follow-up MOB task under MOB-516, keep MOB-1433 sync-only

---

*Phase 1 deliverable. Evidence gathered 2026-07-06 from the working tree at `develop`+`MOB-1430` with uncommitted sync edits. No product code has been modified.*
