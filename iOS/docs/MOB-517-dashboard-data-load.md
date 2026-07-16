# MOB-517 — Dashboard data load: stop re-mapping the full entry history

> **What this doc is.** The work plan + current-state audit for
> [MOB-517](https://greatergoods.atlassian.net/browse/MOB-517) — **Task 1** of epic
> [MOB-516](https://greatergoods.atlassian.net/browse/MOB-516) (5.1.0 Performance Hardening). It records
> what the ticket asked for, **what already landed under MOB-1433** (so we don't redo it), **what genuinely
> remains**, and — as of the **2026-07-13 cold-login Instruments trace** — the finding that **MOB-517 is not
> the cold-login hang** (§2). Sibling of MOB-518 (the chart engine, Task 2); this one is the **data-load**
> path, not rendering.
>
> **Scope:** iOS only · **Base:** `develop` · **Build:** Dev config only.
> All `file:line` refs verified against the working tree on **2026-07-13**.

---

## 0. Status snapshot (2026-07-13)

| Item | State |
|---|---|
| Ticket | GG-**High**, **In Progress**, 5 pts, on **MOB DEV Sprint 31**, "Blocks 5.1.0", assignee Kesavan |
| Dedicated MOB-517 commits | **None** — the overlapping work landed under **MOB-1433** (PR #2218) |
| Core fix (pre-fetch signature gate) | ❌ **Not implemented** — the `toOperationDTO()` map still runs on every load |
| Cold-login hang (Instruments, 2026-07-13) | ❌ **SEVERE — ~37 s of main-thread hangs** in a 59 s trace… **(now FIXED + merged under MOB-516, PR #2268 — see §9)** |
| …but is it MOB-517's map? | ❌ **NO.** Attribution: `performDashboardDataLoad` ≈ **9 %**, `toOperationDTO` **0 %**. The freeze is **History `getMonthsAll` + on-login sync contention** (§2). |
| MOB-517's actual severity | **Warm-load optimization** (latency/battery/multi-product), **not** the cold hang. Size it from a **warm** trace (§8). |
| Label hygiene | ⚠️ carries only `ios`; missing `release-meapp-5.1.0` despite "Blocks 5.1.0" |

---

## 1. Is it really needed? (decision — the cold trace has now answered half of it)

**Two separate questions, two answers:**

1. **Are the MOB-517 code changes still absent?** — **Yes.** Neither PR #2218 (MOB-1433) nor PR #2237
   (MOB-518) added the pre-fetch gate, the share-map-across-types, the bpm/baby prefetch, or the debounce.
   That's a fact of the diffs, not a trace question.
2. **Is MOB-517 the 5.1.0 hang it was filed as?** — **No** — the **2026-07-13 cold-login trace settles this**
   (§2). The cold-login freeze is a **SwiftData store-contention pile-up** (History `getMonthsAll` on the main
   thread + the on-login full-history sync), **not** the dashboard DTO map. `performDashboardDataLoad` is
   ~9 % of the hang and `toOperationDTO` is absent. And MOB-517's gate is **structurally useless on cold
   login** anyway — every entry is brand new, so a "skip the map if unchanged" gate never short-circuits on
   first sync.

**So MOB-517 as scoped will NOT fix the cold-login hang.** It remains a legitimate **warm-load** optimization
(repeat loads / account-switch / metric-switch, where the map re-runs on unchanged data) — but its priority
should be judged from a **warm trace** (§8), not the stale pre-MOB-1433 "hang" evidence in the ticket. The
real cold-login blocker needs its **own** ticket (§9).

**Decision (edit inline — Kesavan):**
- [ ] **Keep MOB-517 GG-High, do it now** — a warm trace still shows a hang/slow render attributable to the map.
- [x] **Downgrade MOB-517 to GG-Medium, do it as a warm-load optimization** — the cold trace proves it's not
  the hang; confirm the warm-load cost with a warm trace, then schedule *(recommended given §2).* 
- [ ] **Defer MOB-517** — MOB-1433's off-main move is "good enough"; re-scope as a post-release optimization.
- [x] **Raise the cold-login-hang ticket first** (§9) — ✅ **done and SHIPPED** as the MOB-516 cold-login fix (PR #2268); that was the actual 5.1.0 freeze, independent of MOB-517.

---

## 2. Trace findings — cold login (2026-07-13) — the hang is real, but it is NOT MOB-517

**Capture:** `iOS/docs/cold login trace.trace` · iPhone 15, iOS 26.2 · Instruments 26.6, **Time Profiler +
Hangs** (Include Microhangs > 250 ms) · 59.65 s · Production build · fresh install → login with a large
account.

**The main thread is frozen ~37 s out of the 59 s — 9 back-to-back Severe Hangs** (3.5–6 s each, from
~00:15 to ~00:52) plus one 272 ms microhang at 00:07. Filtering the Time Profiler to the **main thread during
the hang window** and ranking by **inclusive** stack presence (what % of the 37 287 frozen main-thread samples
pass through each function):

| % of the frozen main thread | Symbol | Whose code |
|---:|---|---|
| **~49 %** | `NSManagedObjectContext.performAndWait` (synchronous Core Data **on the main thread**) | — |
| **34 %** | `EntryService.getMonthsAll` | **History** full-table read |
| **26 %** | `SwiftDataWorker.fetchAllEntryData` / `extractEntryData` | full-table fetch + row materialization |
| **21 %** | `BathScaleMetric.bmr.getter` | per-row **relationship faulting** |
| **14 %** | `EntryService.performSync` | MOB-1433 on-login full-history sync |
| **11 %** | `SwiftDataWorker.saveOrRollback` | sync's chunked writes |
| **~9 %** | `getStreak` / `resetMetricsToLatestEntry` / `updateMetrics` | streak + metrics recompute |
| **~9 %** | `EntryService.performDashboardDataLoad` | **MOB-517's function** |
| **0 %** | `toOperationDTO` | **MOB-517's target — absent** |

**So the cold-login freeze is a SwiftData store-contention pile-up, not the dashboard DTO map:**

- **History's `getMonthsAll` → `fetchAllEntryData` → per-row relationship faults (`bmr.getter`) are executing
  synchronously on the main thread inside `performAndWait`** — even though MOB-1433's own comment at
  [EntryService.swift:396-400](../meApp/Data/Services/EntryService.swift#L396) says this was moved off-main.
  The `@ModelActor` worker's context work is landing on the main thread (or the main thread is blocked on the
  store lock the concurrent sync holds). Either way it's a **SwiftData concurrency bug**, and it is the single
  biggest cost.
- **The on-login full-history sync** (`performSync` + `saveOrRollback`, ~25 % combined) is hammering the same
  SQLite store at the same time → the **repeating ~4 s hang cycles = each sync batch**.
- **MOB-517's `performDashboardDataLoad` is only ~9 %**, and **`toOperationDTO` — the exact thing MOB-517
  proposes to skip — doesn't appear at all** (MOB-1433 already moved it off-main).

> **Why MOB-517 can't fix this even if we did it:** cold login is the *maximally-changed* case — every entry
> is new — so a "skip the map when the dataset is unchanged" gate never short-circuits on first sync. And the
> map is already off-main. The cold-login win lives in §9, not here.

**Raw exports** (main-thread inclusive attribution, hang table) are in the session scratchpad; re-generate with
`xcrun xctrace export --input "iOS/docs/cold login trace.trace" --xpath '…time-profile…'`.

---

## 3. Root cause of the MOB-517 *warm-load* cost (verified in code, 2026-07-13)

> This is the cost MOB-517 was filed against. Post-MOB-1433 it is **background** work (off-main) that gates
> first paint on a *repeat* load — **not** the cold-login main-thread hang in §2.

Every dashboard load re-fetches and re-maps **all** create-type entries via `Entry.toOperationDTO()`
(~24 fields across 4 relationships per row), then filters by type **in memory**, then computes the signature.
The signature is computed **after** the map, so it can only skip the *aggregation* step — never the fetch+map
that the ticket targets.

| Step | Where | Cost |
|---|---|---|
| Load entry point, **per entry type** | `performDashboardDataLoad(entryType:)` — [EntryService.swift:2137](../meApp/Data/Services/EntryService.swift#L2137) | called for `.scale` and `.bpm` (and baby) → **N× per multi-product load** |
| Full fetch + `toOperationDTO()` map, **unconditional** | `getAllEntriesAsDTO()` [EntryService.swift:2141](../meApp/Data/Services/EntryService.swift#L2141) → `worker.fetchEntriesAsDTO` [SwiftDataWorker.swift:229](../meApp/Core/Services/SwiftDataWorker.swift#L229) | the historical hotspot; **runs every load** (now off-main) |
| Signature computed **after** the map | `makeDTOSignature` [EntryService.swift:2157](../meApp/Data/Services/EntryService.swift#L2157) (def [:631](../meApp/Data/Services/EntryService.swift#L631)) | itself a **full O(n × ~24-field)** pass; skips only aggregation |

> **Note (ticket confirmed):** `DailyWeightSummary` **does not exist** in the code — the iOS `CLAUDE.md`
> "Key Domain Models" table is **stale**. The fix must cache / short-circuit **in memory**, not read a
> summary table.

---

## 4. What MOB-1433 already delivered — DO NOT redo

MOB-1433 (PR #2218, merged) overlapped this ticket and already covers part of it:

- ✅ **Fetch + map moved off the main actor** to `SwiftDataWorker` — the *dashboard DTO map* no longer runs on
  main (which is why `toOperationDTO` is absent from §2).
- ⚠️ **…but the trace (§2) shows the off-main move did NOT take for the History read path** — `getMonthsAll` /
  `fetchAllEntryData` / `extractEntryData` are still on the main thread via `performAndWait`. This is a
  MOB-1433 gap/regression and the biggest cold-login cost → **track it in the §9 ticket.**
- ✅ **Relationship prefetch** so a 10k read isn't ~20k faults — [SwiftDataWorker.swift:238](../meApp/Core/Services/SwiftDataWorker.swift#L238)
  — **but only `scaleEntry` + `scaleEntryMetric`** (not `bpmEntry` / `babyEntry`), and **no `propertiesToFetch`**.
  (The trace still shows `BathScaleMetric.bmr.getter` faulting at 21 % — the prefetch isn't covering the read
  in §2.)
- ✅ **In-flight load coalescing** — `activeDashboardLoadTasks` [EntryService.swift:2118](../meApp/Data/Services/EntryService.swift#L2118) dedups concurrent loads for the same entry type.
- ✅ **Post-map signature cache** — `summaryCacheByEntryType` skips the *aggregation* re-compute when the DTO
  signature is unchanged ([EntryService.swift:2152-2185](../meApp/Data/Services/EntryService.swift#L2152)).

---

## 5. What genuinely remains (the MOB-517 core)

Ordered by leverage. The cheap primitives already exist — `fetchEntryCount`
[EntryRepository.swift:444](../meApp/Data/Storage/DB/EntryRepository.swift#L444) and `fetchLatestEntry`
[:411](../meApp/Data/Storage/DB/EntryRepository.swift#L411) (both `fetchLimit = 1`) — they're just **not wired
as a gate**.

1. **Cheap signature gate BEFORE fetch+map (the headline).** Derive a cheap token per account from
   `fetchEntryCount` + latest-entry `entryTimestamp` (+ a metric/unit token if summaries depend on it). If it's
   unchanged since the last successful load for that entry type, **return the cached summaries and skip the
   `getAllEntriesAsDTO()` fetch+map entirely.** Helps **warm** loads only (cold login is all-new → never
   short-circuits — see §2).

2. **Map once, share across entry types.** A single dashboard load currently calls `getAllEntriesAsDTO()`
   once **per** `.scale` / `.bpm` / baby → the full fetch+map runs N×. Build **one** DTO array per signature
   and **filter by type in memory** for each product, instead of re-fetching+re-mapping per type.

3. **Reduce per-row cost (only what the trace confirms).** Extend `relationshipKeyPathsForPrefetching` to
   `bpmEntry` / `babyEntry` (today only weight relationships are prefetched); consider limiting
   `propertiesToFetch`. The §2 `bmr.getter` faulting shows this is still real for at least one read path.

4. **Coalesce / debounce redundant loads.** In-flight dedup exists, but redundant loads still fire from the
   **account-change sink** and the **immediate post-sync** path (load call sites:
   [EntryService.swift:126](../meApp/Data/Services/EntryService.swift#L126),
   [:984](../meApp/Data/Services/EntryService.swift#L984),
   [:1141](../meApp/Data/Services/EntryService.swift#L1141),
   [:2549](../meApp/Data/Services/EntryService.swift#L2549)). Add a short debounce so a burst collapses to one.

5. **Tests (after the change).** Assert the cached/shared path yields **identical** summaries to the full-map
   path for a fixed dataset, and that an unchanged dataset performs **0** `toOperationDTO()` maps.

---

## 6. Files to change

| File | Change |
|---|---|
| `Data/Services/EntryService.swift` | Pre-fetch signature gate in `performDashboardDataLoad`; one shared DTO array filtered per type; debounce the account-change / post-sync loads |
| `Core/Services/SwiftDataWorker.swift` | Cheap `count + latest-timestamp` gate helper (off-main); extend prefetch to `bpmEntry`/`babyEntry`; optional `propertiesToFetch` |
| `Data/Storage/DB/EntryRepository.swift` (+ its protocol) | Expose the cheap count+latest gate if the service needs it through the repo boundary |
| `meAppTests/…/EntryServiceTests`, `EntryRepositoryTests` | Cached/shared-path parity + "0 maps on unchanged dataset" |

**Boundary rule:** keep returning **DTOs / snapshots** across the actor boundary — never the SwiftData
`@Model` (the `check-snapshot-boundary.sh` / `no_published_swiftdata_model` rule).

---

## 7. Acceptance / no-regression (from the ticket, validated)

- **Warm trace:** on a *repeat* load / account-switch, `toOperationDTO` ~0 on an unchanged dataset.
- **Parity test:** cached / shared-path summaries **identical** to the full-map path for a fixed dataset.
- **QA flow:** load → add → delete → switch metric → switch account → the graph and numbers are unchanged.
- **Multi-product:** a scale+BPM(+baby) account does the full fetch+map **once**, not per type.

---

## 8. Verification (device / Instruments — Kesavan runs it)

- ✅ **Cold-login trace — DONE (2026-07-13).** Findings in §2: severe hang, **not** MOB-517.
- ⏳ **Warm-load trace — STILL OWED (this is what sizes MOB-517).** On a large account already synced locally:
  put the phone in **Airplane Mode** (so it doesn't re-sync), then **force-quit and relaunch** under Time
  Profiler + Hangs — or switch to another account and back. This isolates `getAllEntriesAsDTO()`/the DTO map
  with no sync contention.
  - If the warm reload **still hangs main / is slow to first paint** → keep MOB-517 higher priority.
  - If it's off-main and fast (likely, post-MOB-1433) → confirm **GG-Medium** and schedule as a warm-load
    optimization.
- After the fix: the warm trace shows `toOperationDTO` ~0 on a repeat load; add/delete/metric-switch still
  updates correctly; account switch shows the right data.

---

## 9. Related — the ACTUAL cold-login hang (separate ticket) — ✅ SHIPPED

> **Update:** this cold-login hang has since been **fixed and merged** under MOB-516 (PR #2268, merge commit
> `dce7e549d`, branch `MOB-516-cold-login-hang-off-main`) — the four hot `@MainActor → worker` calls now run
> from `Task.detached` contexts, moving the reads/merge off the main thread. See
> [MOB-516-cold-login-hang-fix.md](MOB-516-cold-login-hang-fix.md) for the shipped mechanism. The framing below
> is the original pre-fix analysis, kept for the trail.

The §2 trace surfaced the real 5.1.0 cold-login blocker, which is **not** MOB-517:

> **Cold-login main-thread hang: History `getMonthsAll` (+ `fetchAllEntryData`/`extractEntryData`/relationship
> faults) runs synchronously on the main thread via `performAndWait`, contending with the on-login
> full-history sync's store writes (`performSync`/`saveOrRollback`).** ~37 s of Severe Hangs on a large account.

- **Root cause + full fix plan:** [MOB-516-cold-login-hang-fix.md](MOB-516-cold-login-hang-fix.md). In short:
  the stock `@ModelActor` `SwiftDataWorker` runs on the **main thread** (its default executor doesn't force
  off-main; every caller is `@MainActor`). Primary fix = give the worker a **custom background-queue
  executor**; then coalesce the double login sync/load trigger, extend prefetch, and move streak/metrics
  off-main.
- **Where it belongs:** a MOB-1433 follow-up or a new task under MOB-516 (the off-main move it claims didn't
  fully land). **This is where the cold-login 5.1.0 win is** — prioritise it over MOB-517.

---

## 10. Risks (for the MOB-517 change itself)

- **Stale graph** if the cheap gate token misses a real change — the token must include everything a summary
  depends on (count + latest timestamp + metric/unit). Mitigated by the parity test and keeping the existing
  post-map signature as a backstop.
- **Multi-product correctness** — sharing one DTO array across types must still filter correctly per
  `matchesDTOEntryType`; covered by the QA flow + parity test.

---

*Line numbers verified 2026-07-13 on the `MOB-518-…` working tree (develop-based). Trace: `iOS/docs/cold login
trace.trace` (2026-07-13). Re-grep symbols if they drift.*
