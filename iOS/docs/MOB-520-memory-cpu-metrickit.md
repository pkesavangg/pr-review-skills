# MOB-520 — MetricKit production telemetry (+ account-switch cleanup)

> **What this doc is.** The needed-check + execution plan for
> **[MOB-520](https://greatergoods.atlassian.net/browse/MOB-520)** under epic
> **[MOB-516](https://greatergoods.atlassian.net/browse/MOB-516)** (5.1.0 Performance Hardening). Written
> **after** the heavy perf work already shipped (MOB-1433 off-main pipeline, the MOB-516 cold-login fix,
> MOB-518 chart engine, MOB-519 disk/logging), so it verifies *"is this still needed?"* against the **current**
> tree — not the stale line numbers in the ticket.
>
> **Reframed 2026-07-21 (Kesavan):** the ticket originally bundled three items. **MetricKit is now the primary
> deliverable** and the natural standalone 5.1.0 piece; the **account-switch cleanup** stays as a minor residual;
> the **HealthKit full-sync** item is **dropped** — confirmed working on device, do not touch it (see §2.4).
>
> **Scope:** iOS only · **Base:** `develop` · **Build:** Dev config only.
> All `file:line` refs verified on **2026-07-21** (branch `MOB-519-logging-disk-writes-batch-persistence-floor`).

---

## 1. What MOB-520 is now

| # | Item | Priority in this task |
|---|------|-----------------------|
| **1** | **MetricKit telemetry** — an `MXMetricManagerSubscriber` service (launch-tier), logging/uploading hang + CPU-exception diagnostics. | ★ **Primary — ship in 5.1.0** |
| **2** | **Account-switch cleanup** — batch `makeOtherAccountsInactive` into one write; add `.removeDuplicates()` to the `$activeAccount` sink. | Minor residual |
| ~~3~~ | ~~HealthKit full-sync bound + delta~~ | **Dropped** — works on device (§2.4) |
| ~~4~~ | ~~Product-types migration run-once guard~~ | **Already done** (§2.3) |

Ticket meta: **GG-High**, **To Do**, assignee Kesavan, **6h** estimate, label `ios`. Parent MOB-516.

> **Ticket line numbers are stale** (it cites `HealthKitService.swift:584`, `AccountService.swift:1408` — pre-5.1.0).
> Current locations are in §2/§3. Re-grep the symbol, never trust the ticket's line.

---

## 2. Is it still needed? — **YES.** Verified 2026-07-21

| Item | Verdict | Evidence |
|---|---|---|
| **1. MetricKit** | 🔴 **FULLY ABSENT** | Zero hits for `MetricKit` / `MXMetric*` anywhere in the tree. |
| **2a. Account-switch batched write** | 🔴 **OPEN** | `makeOtherAccountsInactive` still loops N-1 saves. |
| **2b. `$activeAccount` sink dedupe** | 🔴 **OPEN** | `.dropFirst()` only, no `.removeDuplicates()`. |
| **2c. Migration run-once guard** | ✅ **DONE** | `migrateProductTypesIfNeeded` guards on `productTypes.isEmpty`. |
| ~~HealthKit fetch/delta~~ | ⛔ **OUT OF SCOPE** | Confirmed working on device; do not touch. |

### 2.1 MetricKit — absent (the reason to do this task)
No `MetricKit` import, no `MXMetricManagerSubscriber`, no `MetricKitService.swift`. This is the **highest-leverage
remaining item in the whole epic**: every hang stack the 5.1.0 work chased came from a *one-off local Instruments
trace on one device*. MetricKit is the only way to know whether the residuals — or any surviving hang — actually
matter **in the field, across the fleet**, instead of guessing from a single local capture. The epic plan
explicitly says to wire it early.

### 2.2 Account switch — both code items still open
- `makeOtherAccountsInactive` ([AccountService.swift:1522-1528](../meApp/Data/Services/AccountService.swift#L1522))
  loops every other account and calls `updateAccountClearingTokens` per row → `AccountRepository.updateAccount`
  → `saveClearingTokens()` ([AccountRepository.swift:48-58](../meApp/Data/Storage/DB/AccountRepository.swift#L48))
  = **one `ctx.save()` disk commit per account.** N-1 sequential commits per switch.
- The `$activeAccount` sink ([AccountService.swift:56-83](../meApp/Data/Services/AccountService.swift#L56)) has
  `.dropFirst()` but **no `.removeDuplicates()`**, so a re-publish of the same account re-runs
  `registerSessionServices()` (~8–12 services). MOB-1433's D2 fix was on the **`ContentViewModel`** publisher — a
  different sink; this one is untouched.

> **Severity caveat (honest sizing).** Account count is capped at `AppConstants.Account.maxAccounts` (a handful),
> so 2a is N-1 commits over a *small* N — a real but **minor** win. 2b (avoid re-registering ~8–12 services on a
> redundant publish) is the more visible half. This is why it's a residual, not a blocker.

### 2.3 Migration run-once guard — already satisfied (no work)
`migrateProductTypesIfNeeded`
([AccountMigrationService.swift:805-837](../meApp/Data/Services/AccountMigrationService.swift#L805)) already guards
`guard account.productTypes.isEmpty else { return }` (line 806) — it no-ops once populated, even though the sink
calls it on every non-nil publish. **Cite line 806 in the PR; no code change.**

### 2.4 HealthKit full sync — dropped (works on device)
`syncAllData` ([HealthKitService.swift:208](../meApp/Data/Services/HealthKitService.swift#L208)) still fetches the
full history without a `fetchLimit`/delta, but the MA-3941 **chunked commit**
([:266-280](../meApp/Data/Services/HealthKitService.swift#L266)) bounds the in-flight payload, and **Kesavan has
confirmed the full sync works with no issues on device.** Per that call, the fetch-bound/delta refinement is
**out of scope for MOB-520** — leave `syncAllData` as-is. (If a future field trace flags it, reopen as its own
task; a manual "force full re-sync" path already exists.)

---

## 3. The fix

### Fix 1 ★ — MetricKit subscriber (primary; standalone 5.1.0 piece)

**How MetricKit behaves (design the service around this):** it is a **passive, always-registered listener** for
diagnostics the **OS already collects** — you instrument nothing. Delivery is **batched and delayed** (aggregated,
roughly daily, typically arriving on a *later* app launch), **not real-time.** So its value is *fleet-wide
statistics* ("which hang stacks fire, and how often, across real users"), **not** live crash reporting. Overhead
is near-zero; payloads are call-stack trees + counters (no PII / no health data).

**Implementation — new `Core/Services/MetricKitService.swift` conforming to `MXMetricManagerSubscriber`:**
- On init `MXMetricManager.shared.add(self)`; on deinit `.remove(self)`.
- Implement **both** callbacks:
  - `didReceive(_ payloads: [MXMetricPayload])` — aggregate perf metrics (hang time, CPU, memory, launch, disk).
  - `didReceive(_ payloads: [MXDiagnosticPayload])` — the ones the epic wants: **`MXHangDiagnostic`** and
    **`MXCPUExceptionDiagnostic`** (each with a symbolicated `MXCallStackTree`), plus `MXCrashDiagnostic` /
    `MXDiskWriteExceptionDiagnostic`.
- Serialize `payload.jsonRepresentation()` and route through **`LoggerService`** (project rule — never `os_log`/
  `print`); upload to the log/analytics pipeline if a sink exists.
- Register as an **essential (launch-tier)** service in `ServiceRegistry` (MetricKit delivers the *previous*
  launch's payloads shortly after startup, so it must be subscribed at launch), and confirm AppDelegate hands off
  early enough. Follow the singleton `.shared` + double-register (concrete + protocol) pattern.
- **Behaviour:** read-only diagnostics → no product behaviour changes; cannot regress anything.
- **Test aid:** Xcode Debug ▸ **Simulate MetricKit Payloads** fires the handler on demand (no 24h wait).

### Fix 2 — collapse the account-switch writes + dedupe the sink (minor residual)
1. **Batch the deactivation.** Replace the per-row loop in `makeOtherAccountsInactive`
   ([:1522-1528](../meApp/Data/Services/AccountService.swift#L1522)) with one repository call that flips
   `isActiveAccount = false` on all other accounts and does **one** `ctx.save()` — e.g.
   `AccountRepository.deactivateAccounts(exceptId:)`, keeping the `clearTokenFieldsBeforeSave` invariant inside
   the repo.
2. **Dedupe the sink.** Add `.removeDuplicates { $0?.accountId == $1?.accountId }` to the `$activeAccount` chain
   ([:56](../meApp/Data/Services/AccountService.swift#L56)) so a re-publish of the same account doesn't re-run
   `registerSessionServices()` / theme / migration. **Guard the real switch:** a genuine switch changes
   `accountId` and must still fire — cover in tests.

---

## 4. Files to change

| File | Change |
|---|---|
| **`Core/Services/MetricKitService.swift`** (new) | Fix 1 — `MXMetricManagerSubscriber`; log/upload metric + **diagnostic** payloads. |
| `Core/Services/ServiceRegistry.swift` + AppDelegate | Fix 1 — register MetricKit as an essential (launch-tier) service. |
| `Data/Storage/DB/AccountRepository.swift` (+ protocol) | Fix 2 — batch `deactivateAccounts(exceptId:)`, one save. |
| `Data/Services/AccountService.swift` | Fix 2 — call the batch method; add `.removeDuplicates()` to the sink. |
| `meAppTests/…` | MetricKit payload-handling; account-switch single-transaction + dedupe (switch still fires). |

**Boundary rule (unchanged):** services publish/return `Sendable` snapshots/DTOs — the `@Model` never crosses the
actor boundary (`no_published_swiftdata_model` + `check-snapshot-boundary.sh`).

---

## 5. Tests

- **MetricKit:** given a synthetic `MXDiagnosticPayload`, the service serializes + logs via `LoggerService`
  (inject/mock the manager so the test is deterministic). Assert both callbacks handle empty + multi-payload
  arrays.
- **Account switch:** A→B deactivates A in **one** save (assert a single write/commit), B ends active, tokens
  cleared. `.removeDuplicates()` — re-publishing the **same** accountId does **not** re-run
  `registerSessionServices()`, but a genuine switch (different accountId) **does**.
- Coverage gates: `Data/Services` 80% (85% account); ViewModels 80%.

---

## 6. Verification (device / Instruments — Kesavan runs it)

- **MetricKit:** Xcode Debug ▸ Simulate MetricKit Payloads → confirm payloads serialize + reach the logs; then a
  real background/relaunch cycle produces a payload on the next launch (expect the one-launch delay).
- **Account switch:** switch accounts repeatedly under Time Profiler — no per-switch write storm, no double
  service re-registration; correct data after each switch.

---

## 7. Risks

- **MetricKit** is read-only diagnostics → effectively zero behaviour risk. Care points: subscribe early enough to
  catch the previous launch's payload; keep log volume low (payloads are periodic/on-crash, so naturally low).
- **Account batch write** — must preserve `clearTokenFieldsBeforeSave` for every row in the batch (tokens live in
  Keychain; a re-persisted token is a security regression). Cover in the parity test.

---

## 8. Ticket hygiene (flag before working it)

- **Missing release label.** MOB-520 carries only `ios`; sibling MOB-1515 has `ios` + `release-meapp-5.1.0`. Add
  `release-meapp-5.1.0` if MetricKit is committed to 5.1.0 (recommended — see §9).
- **Unsized on Story Points.** `customfield_10028` is null; 6h time estimate only. Set SP. With HealthKit dropped
  and the migration guard already done, the remaining scope (MetricKit + a small account-switch cleanup) is ~**3
  pts** / the estimate likely trims from 6h → ~4h.
- **Priority vs epic.** GG-High overstates a telemetry-hook + minor cleanup; the epic is GG-Medium. Recommend
  **GG-Medium**.

---

## 9. Recommendation

1. **Ship MetricKit (Fix 1) in 5.1.0 as the headline of MOB-520** (or split it into its own small task if you want
   it fully independent). Cheap, zero-risk, and it's the only thing that turns "we profiled it once locally" into
   ongoing field evidence — which is what tells you whether anything else in the epic still matters.
2. **Account-switch cleanup (Fix 2)** — do it alongside if there's room; it's a small, safe win. Defer to a
   follow-up if the release is tight. Not a blocker.
3. **HealthKit — out of scope** (works on device).
4. **Migration guard — mark done** (already in place).

---

*Line numbers verified 2026-07-21 on branch `MOB-519-logging-disk-writes-batch-persistence-floor` (develop-based).
Re-grep symbols if they drift. Companion: [`MOB-516-implementation-plan.md`](MOB-516-implementation-plan.md) §5.*
