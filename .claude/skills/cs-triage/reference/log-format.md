# Reading a meApp diagnostic log

There are **two different artifacts** and confusing them is the most common triage mistake:

| | What it is | Where you see it |
|---|---|---|
| **The DB row** | the app's local log record — 9 fields incl. `type`, `tag`, `sessionId`, `accountId` | never; it stays on the phone |
| **The emailed file** | what `guru@weightgurus.com` forwards to `info`/`dev` — **two columns, `Timestamp` + `Data`** | this is what you actually triage |

**The upload flattens the row to `{time, data}` and throws the rest away.** So any rule phrased in
terms of `type=e`, `tag`, `tagId`, or `sessionId` **cannot be applied to a CS report.** The DB schema
is documented at the bottom only to explain what was lost.

---

## The emailed file

```
Weight Gurus App Help Request - <customer email>
Inbox
WG App Logs

guru@weightgurus.com
<send date>
to info, dev

Email from: <customer email>
Name: <full name>
DOB: <Date.toString()>
Device: null                      ← effectively always null; see below
App Version: 5.0.3
Account Created: <Date.toString()>

Timestamp	Data
2026-06-29T08:39:56.003Z	Log sending initiated
2026-06-29T08:39:47.098Z	Debug menu navigation requested
…
```

Tab-separated, two columns. `Timestamp` is an **ISO-8601 UTC string with milliseconds** (the DB
stores epoch ms; `formatLogsForAPI` / `DateTimeConverter.timestampToIso` convert on upload).

### Five things about that header

1. **No ACCOUNT ID, no OS, and `Device:` is null.** Neither platform attaches device info to the
   upload — iOS never builds it; Android builds a `DeviceInfo` in `LogRepository.sendLogs()` and then
   never puts it in `SendLogRequest`. Recover device/OS **from the rows** instead (below).
2. **Account id comes from the rows**, not the header — Android logs `Current account ID: <id>`;
   both platforms carry `accountId=<id>` in most service rows.
3. `App Version` is the version **at upload time**, self-reported by the app. Trust it over the CS
   message, and use it to pick the ref (`code-lookup.md`).
4. `Name` / `DOB` / `Account Created` come from the server account record, not the log.
5. **One email can carry several customers' logs concatenated** — a new
   `Weight Gurus App Help Request - <email>` line starts a new section, after a run of blank lines.
   Split first, triage each section separately, and **never let rows cross a section boundary**.
   (The two sections may be different platforms — one Android, one iOS.)

### Recovering device / OS / SKU from the rows

| Want | iOS row | Android row |
|---|---|---|
| Device + OS | `PushNotificationService: Device info updated: model=iPhone (iPhone16,1), manufacturer=Apple, os=iOS 26.5, appVersion=5.0.3` | not logged — infer from the report |
| Scale SKU | `DeviceInfo(… modelNumber: Optional("gG BS 0412"), deviceName: "gG BS 0412", protocolType: Optional("R4") …)` | `GGDeviceDetail(… deviceName=<broadcast name>, protocolType=A3 …)` — a broadcast name, **not** an SKU; map it via `device-catalog.md` before quoting a model |
| Wi-Fi provisioned? | `isWifiConfigured: Optional(true)` | `isWifiConfigured=false` |

---

## Platform — iOS vs Android from the emailed file

### 1. Tag prefix — decisive

**Every iOS row is `Tag: message`. No Android row has a tag at all.** This is structural, from the
two upload mappers, so it cannot cross over:

- iOS [`LoggerService.swift:356`](../../../../iOS/meApp/Core/Services/LoggerService.swift) —
  `let logMessage = "\(logEntry.tag): \(logEntry.message)"`
- Android [`SendLogRequest.kt` `LogEntry.from`](../../../../Android/app/src/main/java/com/dmdbrands/gurus/weight/domain/model/api/support/SendLogRequest.kt) —
  `data = logEntity.message` (tag dropped, and the entity's own `data` column is never uploaded)

| Shape | Platform |
|---|---|
| `EntryService: Full entry sync started: accountId=…` | **iOS** |
| `Scan initialised` · `syncScales called` · `Token is still valid for 3091 minutes` | **Android** |

Android messages that happen to start with a bracketed function name
(`[checkHealthConnectPermissionDisabled] ENTRY`) are **not** tags — note the `[` and the absence of
`: `.

### 2. Health integration — decisive

`HealthKit*` ⇒ iOS. `Health Connect` / `checkHealthConnectPermissionDisabled` /
`healthConnectStatus=INSTALLED` ⇒ Android. The two SDKs are platform-exclusive.

### 3. Continuation-line syntax — decisive when present

An iOS row with extra data renders as **two lines**: a quoted `"Tag: message"` line, then a
`["…"]` array line carrying the Swift `debugDescription`. Swift `Optional("…")` inside it ⇒ iOS.
Android never emits these (its `data` column isn't uploaded), and its inline dumps are Kotlin
`toString()` — `Name(field=value, other=value)`, `=` not `:`, no `Optional`.

### 4. Vocabulary — corroboration only

| iOS | Android |
|---|---|
| `HelpStore`, `ScaleStore`, `BtWifiScaleSetupStore`, `ScaleSettingsStore`, `DashboardMetricsManager`, `PermissionsService`, `BluetoothService`, `IntegrationService` | `getOperationsFromAPI`, `FCM token`, `AccountInfo(id=…)`, `DeviceDetail(type=NEW_DEVICE, data=GGDeviceDetail(…))`, `Debug menu navigation requested`, `sortedActiveFirst()` |

Do not decide platform on these alone — several service names exist on both.

---

## Row order — check it before reading a timeline

**The two platforms arrive in opposite order** and nothing in the file says which:

- **Android — newest first (descending).** The `Log sending initiated` / `Current account ID` /
  `Sending N logs for account` trailer sits at the **top**.
- **iOS — oldest first (ascending).** `HelpStore: … log upload completed successfully` sits at the
  **bottom** (and appears more than once if the customer tapped send repeatedly).

Read the first and last timestamps before concluding anything about "where it stopped".

---

## Freshness, retention, and coverage — per platform

| | iOS | Android |
|---|---|---|
| Uploaded window | **all** retained logs for the active account | **last 5 days only**, current account only |
| Local retention | 5 days (`AppConstants.TimeoutsAndRetention.logRetentionDays`), cleaned at launch | separate cleanup path (`cleanupOldLogs`, 7-day default) |
| After a successful upload | **local rows for that account are deleted** (`sendLogsToServer`) | **rows are kept** — `clearLogsForCurrentAccount()` is a separate, user-driven action |

Consequences for gate 2b:
- On **iOS**, a second log from the same customer legitimately may not contain the earlier failure —
  the first upload wiped it. Ask for a log captured *immediately after* the next failure.
- On **Android**, a re-send should still contain it if it was within 5 days; a missing failure older
  than that is the 5-day filter, not a bug.
- **Only the active account's rows are uploaded** on both platforms. On a multi-account phone the log
  is silent about what the other accounts did.
- **iOS "scale logs" are a separate upload** (`HelpStore: Scale logs upload completed successfully`)
  carrying firmware log text — and `formatScaleLogsForAPI` stamps **every** row with `Date()` at
  upload time, not the event time. Never build a timeline from scale-log timestamps.

---

## Reading values — the ×10 fixed-point trap

Entry and account payloads store **tenths as integers**. Divide by 10 before quoting anything:

| In the log | Actually |
|---|---|
| `weight: Optional(1218.0)` | 121.8 lb |
| `bmi: Optional(202.0)` | 20.2 |
| `goalWeight=1852.0`, `initialWeight=1836.0` | 185.2 lb / 183.6 lb |
| `height=660` | 66.0 in |

Display-layer rows are already formatted (`Scan started with current weight: 185.8`), so the same
log shows both conventions. Telling CS a customer weighs 1218 lb is an avoidable embarrassment.

---

## Finding failures without a level column

There is no `type`, so `type=e` scanning is impossible. Grep the `Data` column for:

`Failed` · `failed` · `Error` · `error` · `Exception` · `failures=` with a non-zero value ·
`No valid entries` · `Unknown` · `timeout` · `denied` · `=null` / `: nil` where a value was expected ·
`HTTP 4` / `HTTP 5`.

Also treat these as signal even though they aren't errors:
- **counts that should not be zero** — `Successfully fetched 0 devices from API`, `scalesCount=0`,
  `Updated last 7 days: 0 entries`, `remoteOperationCount=0`
- **a `create` DTO with every body-comp field `0.0`** — the reading arrived weight-only; check
  `impedanceSwitchState` / `sessionImpedanceSwitchState` on the `DeviceInfo` row
- **`requested=0, effective=1`** style mismatches in `BluetoothService: Synced devices to Bluetooth SDK`
- **a create followed by a delete of the same `entryId`** — the customer removed it themselves

Without `sessionId` you cannot group by app launch. Segment the timeline by **time gaps** and by
launch/lifecycle markers instead (`Startup flow complete`, `Navigation to dashboard successful`,
`ScaleStore: Scale setup in-progress status updated to true/false`).

---

## The log contains PII — plan for it

Contrary to the old "no PII" assumption, real logs carry: **customer email, first/last name, gender,
DOB, zipcode, height, goal/initial weight** (Android's `AccountInfo(…)` dump), **weights and entry
payloads**, **BLE MAC addresses / broadcast ids**, and the **full FCM push token**.

So: the log stays in the chat. Never write it — or any excerpt of it — into a repo file, a Confluence
page, or a Jira description. Bug tickets get *paraphrased* evidence plus the account id.

---

## What is NOT in the log

- **`.debug` / `d` rows are console-only on iOS and never persisted.** Their absence proves nothing.
- Anything older than the retention/upload window (above).
- Anything from a non-active account.
- The row's `type`, `tag` (Android), `tagId`, `sessionId`, and the entity `data` column (Android) —
  dropped by the uploader.

## The DB row, for reference only

iOS `LogEntry` ([`iOS/meApp/Domain/Models/DB/LogEntry.swift`](../../../../iOS/meApp/Domain/Models/DB/LogEntry.swift), SwiftData) and Android
`LogEntity` ([`Android/app/src/main/java/com/dmdbrands/gurus/weight/data/storage/db/entity/log/LogEntity.kt`](../../../../Android/app/src/main/java/com/dmdbrands/gurus/weight/data/storage/db/entity/log/LogEntity.kt), Room table `logs`)
are mirrored: `id`, `accountId`, `sessionId` (UUID per launch), `tag`, `tagId`, `type`, `message`,
`timestamp` (epoch ms), `data`. Levels are `i`/`e`/`d`/`s` on iOS and `i`/`e`/`d`/`w`/`v`/`a` on
Android. **None of this reaches the emailed file** — it is only visible in-app via the debug menu, so
use it when someone shares a screen recording of the debug log viewer, never for a CS email.

Upload path: iOS `LoggerService.sendLogsToServer()` → `LoggerApiRepository.sendLogs()`;
Android `LogManager.sendLogs()` → `LogRepository.sendLogs()` → `POST /support/log`. Both send
`{ version, logs: [{ time, data }] }`.

---

## Checklists by symptom

Answer each item with a verdict **and the log evidence**. Skip the checklists the SKU can't apply to
(see `device-catalog.md`).

**Pairing / BLE**
- Is any device already registered to this account? (`scalesCount=`, `serverScales=`,
  `Successfully fetched N devices from API`) Which SKU, which user slot?
- Are there duplicate device rows (same scale twice, or two scales on one user slot)? — see K-08
- Scan started? Device discovered (MAC/broadcast id seen)? Connect attempted? `getDeviceInfo`
  succeeded? Registration/`Pushed local changes` completed?
- If discovery never happened: did the scan run at all, or run and find nothing? (Different causes.)
- Android only: `DeviceDetail(type=NEW_DEVICE …)` means discovered-but-not-yet-paired — not a failure.

**Entry sync**
- Reading received from scale → saved locally (`New entry saved locally: entryId=…, source=…`) →
  pushed (`Unsynced entry push completed … createsSynced=N, failures=N`) → server. Which arrow breaks?
- `totalEntries=` before/after tells you whether the server accepted it.
- Are readings arriving but landing on a different account/user slot?
- Body-comp all zeros ⇒ weight-only reading, not a sync failure.

**Wi-Fi setup** (`0384` `0385` `0396` `0397` `0412`)
- Network list present? Remember it is **scanned by the scale**, not the phone — 2.4 GHz only (K-01).
- Credentials submitted? Join result? `isWifiConfigured` on the device row? Any entries arriving
  server-side afterwards?

**Health integration**
- iOS: `HealthKitService: HealthKit sync new entry started/completed … payloadCount=N`,
  `IntegrationService: Logged HealthKit integration entry to server … permissionsCount=N`.
- Android: `healthConnectStatus`, `permissionStatus=NONE|PARTIAL|ALL`, `isAlreadyConnected`,
  `hasPartialOrAllPermission` — a `permissionStatus=NONE` with `isAlreadyConnected=true` is the
  revoked-permission state, not a sync bug.

**Auth**
- Login attempts, `Token expires at:` / `Token is still valid for N minutes`, 401s, account switches
  (`Reset scale discovered state for account switch`).

**History / data ("my data disappeared")**
- Entry counts by range (`Updated last 7/30 days: N entries`, `Found N scale entries`), sync cursor
  (`getOperationsFromAPI using sync timestamp`), deletions.
- A count of 0 for a recent window alongside a large lifetime total is usually a *date-range* or
  timezone question, not data loss.
- Check for a second account with a near-identical email (K-10) before concluding data loss.

**Crash-adjacent**
- Clusters of the error keywords above, and what immediately preceded each one. A log that simply
  **ends** mid-flow with no upload trailer is consistent with a crash — say "consistent with", and
  ask for the Firebase Crashlytics record rather than asserting it.
