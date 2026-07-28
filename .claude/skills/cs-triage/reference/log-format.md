# Reading a meApp diagnostic log

## Row schema — identical on both platforms

iOS `LogEntry` ([`iOS/meApp/Domain/Models/DB/LogEntry.swift`](../../../../iOS/meApp/Domain/Models/DB/LogEntry.swift), SwiftData) and Android
`LogEntity` ([`Android/app/src/main/java/com/dmdbrands/gurus/weight/data/storage/db/entity/log/LogEntity.kt`](../../../../Android/app/src/main/java/com/dmdbrands/gurus/weight/data/storage/db/entity/log/LogEntity.kt), Room table `logs`)
were deliberately mirrored:

| Field | Meaning |
|---|---|
| `id` | row id |
| `accountId` | which account produced the row — a log can span accounts after switching |
| `sessionId` | UUID per app launch — **the grouping key for a timeline** |
| `tag` | class/component that logged |
| `tagId` | function/method that logged |
| `type` | level — see below |
| `message` | short description |
| `timestamp` | epoch **milliseconds** |
| `data` | stringified extra context |

**Because the schema matches, the schema tells you nothing about the platform.** Use the fingerprint
below.

## Platform — deciding iOS vs Android from the log

### 1. `type` values — decisive

| Platform | Levels |
|---|---|
| **iOS** | `i` info · `e` error · `d` debug · **`s` success** |
| **Android** | `e` error · `i` info · `d` debug · **`w` warning** · **`v` verbose** · **`a` assert** |

**One `s` row ⇒ iOS. One `w`, `v`, or `a` row ⇒ Android.** Each set is structurally impossible on
the other platform (iOS `LogEntry.LogType` has no `w`/`v`/`a`; Android's `AppLog` has no `s`).
A log containing only `i`/`e`/`d` is ambiguous on this signal — fall through to tags.

### 2. `tag` shape — the fallback

| Platform | Distinctive tags | Shape |
|---|---|---|
| **iOS** | `AppDelegate`, `HTTPClient`, `ObservableForm`, `LoggerService`, `LoggerRepository`, `LoginStore`, `MyKidsStore`, `GGMeAppLogger`, `GifView`, `RefetchedEntryWrapper`, `API`, `Dashboard*Manager` (Chart/Data/Display/Goal/Graph/GridEditing/Lifecycle/Metrics/Streak), `DashboardSyncCoordinator` | `*Store`, `*Coordinator` |
| **Android** | `AppViewModel`, `AddDeviceViewModel`, `BtScaleSetupViewModel`, `BLESetupViewmodel`, `AppsyncScaleSetupViewModel`, `BabyDashboardVM`, `BaseDashboardVM`, `BpDashboardVM`, `DashboardSnapshotVM`, `BabyScaleBLESetupVM`, `AuthTokenInterceptor`, `CapacitorStorageHelper`, `DebugMenuScreen`, `CustomTabManager`, `DataSettingsManager` | `*ViewModel`, `*Viewmodel`, `*VM` |

**`*ViewModel` / `*VM` ⇒ Android** (MVI); **`*Store` ⇒ iOS** (MVVM+Stores). These are architectural,
so they can't cross over.

**Tags that exist on BOTH — never decide platform from these:** `AccountService`,
`AccountRepository`, `DeviceRepository`, `IntegrationService`, `IntegrationRepository`,
`AnalyticsService`, `BodyCompositionService`, `BabyProfileService`.

The lists above are verified but partial (iOS has ~26 literal `tag:` strings; Android ~70 `TAG`
constants — many call sites pass a constant rather than a literal). Refresh them with:

```bash
grep -rhoE 'tag: "[A-Za-z0-9_]+"' iOS/meApp --include="*.swift" | sort -u
grep -rhoE 'const val TAG = "[A-Za-z0-9_]+"' Android/app/src/main --include="*.kt" | sort -u
```

### 3. `accountId` nullability — weak signal

iOS declares it `String?` (may legitimately be absent); Android declares it non-null `String`
(always populated). Rows with a genuinely empty `accountId` lean iOS. Corroborate, don't conclude.

## What is NOT in the log — read this before concluding "the app never got there"

- **`.debug` / `d` rows are console-only on iOS and never persisted.** Their absence proves nothing.
- **Retention is 30 days** (`AppConstants.TimeoutsAndRetention.logRetentionDays`), auto-cleaned at
  launch in batches.
- **A successful upload deletes the local rows.** So a *second* log from the same customer can be
  missing the earlier failure entirely. This explains a lot of "the log doesn't show it".
- **No PII, tokens, or health values** by policy — correlate via `accountId`, and don't expect
  customer identity in the rows.

## Upload shape (what the emailed artifact derives from)

`LoggerService.sendLogsToServer()` → `LoggerApiRepository.sendLogs()` → `POST .log` with
`LogsPayload { version, logs: [{ time, data }] }`
([`iOS/meApp/Domain/Models/API/LogsPayload.swift`](../../../../iOS/meApp/Domain/Models/API/LogsPayload.swift)). `data` is either a plain string or
`[message, extraData]`.

> **Format caveat.** The exact rendering of the file that arrives by email (plain text / JSON / CSV,
> column order, date format) is **not pinned in this reference yet** — pin it against the first real
> log. Until then: parse tolerantly (locate timestamps, level markers, tags heuristically) and
> **report what you could not parse** rather than guessing. If the parse is thin, say so — a thin
> parse is outcome **D**, not a licence to speculate.

## Checklists by symptom

Answer each item with a verdict **and the log evidence**. Skip the checklists the SKU can't apply to
(see `device-catalog.md`).

**Pairing / BLE**
- Is any device already registered to this account? Which SKU, which user slot?
- Are there duplicate device rows (same scale twice, or two scales on one user slot)? — see K-08
- Scan started? Device discovered (MAC/SKU seen)? Connect attempted? Handshake/registration completed?
- If discovery never happened: did the scan run at all, or run and find nothing? (Different causes.)

**Entry sync**
- Reading received from scale → written locally → `POST /v3/entries/` → response. Which arrow breaks?
- Are readings arriving but landing on a different account/user slot?

**Wi-Fi setup** (`0384` `0385` `0396` `0397` `0412`)
- Network list present? Remember it is **scanned by the scale**, not the phone — 2.4 GHz only (K-01).
- Credentials submitted? Join result? Any entries arriving server-side afterwards?

**Health integration**
- Write attempts to HealthKit / Health Connect, permission grants, which data types, failures.

**Auth**
- Login attempts, token refresh, 401s, account switches.

**History / data ("my data disappeared")**
- Entry counts by date range, sync cursor position, deletions.
- Check for a second account with a near-identical email (K-10) before concluding data loss.

**Crash-adjacent**
- Clusters of `type=e`, and what immediately preceded each one.
