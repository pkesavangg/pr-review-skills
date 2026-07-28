# Plan — `/cs-triage` skill (#_cstech issue + log analysis)

**Jira:** [MOB-1914](https://greatergoods.atlassian.net/browse/MOB-1914) (under epic [MOB-1003](https://greatergoods.atlassian.net/browse/MOB-1003))
**Goal:** turn the #_cstech loop (CS reports an issue → read the emailed log → investigate → reply to CS → raise a MOB ticket if it's real) into one repeatable skill invocation.
**Status:** **built** — Phases 1 and 3 landed as [`.claude/skills/cs-triage/`](../../.claude/skills/cs-triage/SKILL.md). Phase 2 (pinning the log parser to a real emailed log) and Phase 4 (dry-run against past cases) are still open — see §10 and §11.

---

## 1. What I actually do today (read from #_cstech, channel `CBNRE7W75`)

I read ~3 months of the channel and 3 full threads. The loop is very consistent:

**Step 1 — CS posts.** Jasmine (`@Jaz`), Urmila, or Katy posts a message that almost always contains:

```
<customer email>  WG            ← or "Balance Health"
ACCOUNT ID: <22-char account id>
DEVICE: Apple iPhone (15)        ← or Samsung SM-A536U
DEVICE OS: iOS 26.5.2            ← or Android 16
APP VERSION: 5.0.3
<free-text narrative: what the customer says + what CS already tried>
```
…plus, sometimes: a screenshot, a scale SKU (`0375`, `0380`, `0397`, `0412`), a video link, and "I had them send a log."

**Step 2 — the log arrives by email.** Separate from Slack, and often days later. I confirm receipt in-thread ("We received the logs, we'll check").

**Step 3 — I analyse.** What I'm actually looking for, based on my own replies:
- Is a scale registered to this account at all, and which SKU / user slot? (a real case turned out to be *"the account currently has no scale registered"* — which was the whole answer)
- Duplicates that shouldn't exist. (*"2 paired 0380 scales, both assigned to user 2 — our app is not supposed to allow that"*)
- Where the pairing sequence stops — scan started / device discovered / connected / handshake done. (*"tried to re-pair 4 times, every attempt silently failed — scanning started but never discovered the scale"*)
- Whether readings are arriving over BLE and syncing. (a later log on the same case: *"the phone IS pulling weigh-ins over Bluetooth"*)
- **Is the log even from the failed attempt?** I've had to ask for a fresh log taken right after the failure more than once.
- **Does the log's device/app version match what CS reported?** I caught this on 2026-07-28: *"the phone in the video doesn't look like the device on the report (Samsung SM-A536U, Android 16)."*

**Step 4 — I reply in-thread.** My replies have a fixed shape: plain-language verdict → the mechanism explained in customer-safe words (bold the key fact) → numbered "what to have him do" → explicit questions back to CS → "have any other users reported this?" (isolated vs pattern) → Jira link if raised. **I never paste raw log lines** — CS forwards this to customers.

**Step 5 — ticket if real.** e.g. `MOB-1778` (0375 pairing), `MA-3945` (height revert).

**Five outcomes cover everything I saw:**

| # | Outcome | Real examples |
|---|---|---|
| A | **Not a bug — expected behaviour**, explain the mechanism | 0412 Wi-Fi list is 2.4 GHz-only; BT holds one phone at a time; "Users" greyed out; lean body mass is calculated not measured; scale showing the name is normal |
| B | **Account/data state issue** found in the log, fix by instruction | no scale registered; two paired 0380s on user 2; `.con` vs `.com` duplicate accounts |
| C | **Real bug → raise MOB ticket** | MOB-1778, MA-3945 |
| D | **Insufficient evidence** — need a fresh log / a specific answer | log predates the failure; device mismatch; "does the scale show pairing mode?" |
| E | **Already fixed in a shipped build** — tell CS to update | the whole 5.0.2 sweep |

The skill's job is to land on one of A–E with evidence, then produce the reply and (for C) the ticket.

---

## 2. Skill, not an agent

You provide the inputs and want output in one pass — no watching, no polling. That's a **skill**: one `SKILL.md` + reference files, invoked as `/cs-triage`. No cron, no `/loop`, no background agent. (If you later want the log-arrival email watched automatically, that's a separate scheduled routine — out of scope here.)

**Confirmed decisions:**
- **Input:** you paste the log text into chat (along with the CS message).
- **Writes:** none. The skill drafts everything; you send/create. It will not post to Slack or touch Jira.
- **Knowledge base:** yes — a growing playbook of confirmed root causes, checked before analysing and appended after.

---

## 3. Files to create

**As built** — in the repo, not the global skills dir, so the team shares one playbook and it's reviewable:

```
.claude/skills/cs-triage/
├── SKILL.md                    # the pipeline (§4)
└── reference/
    ├── log-format.md           # log schema + platform fingerprint + checklists (§5, §5a)
    ├── code-lookup.md          # which git ref to read for a given app version (§5b)
    ├── device-catalog.md       # SKU → setup type (§8)
    ├── known-issues.md         # the playbook — 13 seeded entries, appended over time (§6)
    └── reply-templates.md      # one reply shape per outcome A–E (§7)
```

Two departures from the original plan, both deliberate:
- **Repo, not `~/.claude/skills/`.** It's committed under the root `.claude/skills/` (generic, triggers anywhere in the monorepo) so the playbook is shared with the team and grows under review, rather than living in one machine's home directory.
- **No separate `knowledge/resolved.md`.** New confirmed causes append straight into `known-issues.md` — one file to read, one to write, no "which file was it in?" ambiguity.

---

## 4. The pipeline (what one `/cs-triage` run does)

**Step 0 — parse the inputs.** From the pasted CS message, extract: customer email, account ID, device, OS, app version, product (WG / Balance / baby), SKU if present, reporter (Jaz/Urmila/Katy), the Slack thread permalink if given, and the symptom in one line. Print this block back so I can spot a bad parse immediately.

**Step 1 — consult `known-issues.md` FIRST.** If the symptom matches a confirmed entry, say so up front: *"This looks like known issue K-03 (0412 2.4 GHz only)."* Still read the log to confirm — but this is what turns a 40-minute investigation into a 2-minute one, and it's most of the channel's volume.

**Step 2 — decide the platform *from the log itself*, then sanity-check against the report.** Three checks, all of which have burned me:
- **Platform:** derive iOS vs Android from the log content, never from the CS `DEVICE` line (§5a). State the verdict and the evidence.
- **Freshness:** last log timestamp vs the reported failure. If the log ends before the failure window, that's outcome **D** — stop and draft the "send a fresh log right after it fails" reply.
- **Identity:** does the log's platform / app version / account ID match the CS block? Flag any mismatch explicitly. This is the check that caught the Samsung-vs-video discrepancy on 28 Jul.

Everything downstream forks on the platform verdict — which checklist runs, which codebase gets read, and which platform label the Jira draft carries.

**Step 3 — build a timeline, not a dump.** Group log rows by `sessionId`, order by `timestamp`, and summarise per session: how it started, what the account state was, what was attempted, where it stopped. Every `type=e` row surfaces with its `tag`/`tagId`.

**Step 4 — run the subsystem checklist** matched to the symptom (pairing / entry sync / Wi-Fi setup / health integration / auth / history-data / crash). Each item is a question with a verdict + the log evidence behind it. Details in §5.

**Step 5 — read the code the customer is actually running.** With `tag`/`tagId` in hand, jump straight to the source (iOS `Features/ScaleSetup/`, `Data/Services/`; Android `core/service/`, `bleWrapper/`) to confirm whether the observed behaviour is intended. This is what separates outcome **A** from **C**, and it's the slowest step by hand.

**Hard rule: read the *released* ref, never the working tree** (§5b). A customer on 5.0.3 is running `main`. The checkout is usually a Phase 2 / feature branch whose code is in nobody's hands. Getting this wrong produces a confident, wrong answer — which is worse than no answer.

**Step 6 — classify A–E** and state confidence. If the log genuinely doesn't say, the skill must say *"the logs confirm X but don't point to a cause"* — exactly how I wrote MOB-1778 — rather than inventing one.

**Step 7 — produce three artifacts** (§9), then offer to append to `knowledge/resolved.md`.

---

## 5. `references/log-format.md` — what the skill needs to know

Verified from the codebase:

**Row schema** — iOS `LogEntry` (`iOS/meApp/Domain/Models/DB/LogEntry.swift`), Android `LogRepository` / `LogDao`:

| Field | Meaning |
|---|---|
| `accountId` | which account (a log can span accounts after switching) |
| `sessionId` | UUID per app launch — **the grouping key for a timeline** |
| `tag` | class/component (e.g. `BluetoothService`, `EntryStore`) |
| `tagId` | function/method |
| `type` | `i` info · `e` error · `d` debug · `s` success |
| `message` | short description |
| `timestamp` | epoch **milliseconds** |
| `data` | stringified extra context |

**Upload path:** `LoggerService.sendLogsToServer()` → `LoggerApiRepository.sendLogs()` → `POST .log` with `LogsPayload { version, logs: [{ time, data }] }` (`iOS/meApp/Domain/Models/API/LogsPayload.swift`). `data` is either a string or `[message, extraData]`. Rows are deleted locally after a successful upload.

### 5a. Telling iOS from Android — from the log alone

The two schemas were deliberately mirrored (iOS `LogEntry` vs Android `LogEntity` — same nine fields), so **the schema does not identify the platform.** These three signals do:

| Signal | iOS | Android |
|---|---|---|
| **`type` values** *(decisive)* | `i` `e` `d` **`s`** (success) | `e` `i` `d` **`w`** **`v`** **`a`** |
| **`tag` shape** | `*Store` (`LoginStore`), `AppDelegate`, `HTTPClient`, `ObservableForm`, `LoggerService`, `Dashboard*Manager` | `*ViewModel` / `*VM` / `*Viewmodel` (`AddDeviceViewModel`, `BabyDashboardVM`, `BLESetupViewmodel`), `AppViewModel`, `AuthTokenInterceptor`, `CapacitorStorageHelper` |
| **`accountId`** | nullable (`String?`) — may legitimately be absent | non-null (`String`) — always populated |

**Rule:** a single `s` row ⇒ iOS. A single `w`/`v`/`a` row ⇒ Android. Both are structurally impossible on the other platform, so one row settles it. Tag shape is the fallback and the cross-check (`*Store` ⇒ iOS, `*ViewModel`/`*VM` ⇒ Android). Verified sets: 26 distinct iOS `tag:` literals, 70 Android `TAG` constants — worth dumping both into this reference file so the match is exact rather than heuristic.

Note some tags exist on **both** (`AccountService`, `DeviceRepository`) — never decide platform from those alone.

Then: compare the verdict to the CS `DEVICE` / `DEVICE OS` line and say so out loud when they disagree.

### 5b. `references/code-lookup.md` — read the shipped code, not the checkout

`main` is the 5.0.x release line; `develop` carries Phase 2 (Me.Health 2.0), which no customer has. Verified refs:

| Customer's APP VERSION | Read this ref |
|---|---|
| 5.0.3 (current shipped) | `main` — confirmed `versionName = "5.0.3"`, `versionCode = 800004` |
| 5.0.0 / 5.0.1 / 5.0.2 | tag `v5.0.0` / `v5.0.1` / `v5.0.2` |
| 5.0.4 (in flight) | `origin/release/5.0.4` |
| anything Phase 2 / 2.0 | `develop` — **not a customer-facing answer** |

Tags `v5.0.0`–`v5.0.3` exist (v5.0.2 → 2026-05-25, v5.0.3 → 2026-06-29), plus `origin/release/5.0.1`–`5.0.4`.

**Mechanics — read without checking out** (safe with a dirty tree, no branch switching, never disturbs in-progress work):

```bash
git show main:iOS/meApp/Features/Common/Constants/Scales.swift
git grep -n "0412" v5.0.2 -- Android/app/src/main
git ls-tree main -- iOS/meApp/Features/ScaleSetup/ --name-only
git diff v5.0.2..main -- iOS/meApp/Features/Dashboard/   # "was this changed between their build and now?"
```

**Proof this matters — found while writing this plan.** The SKU catalog I originally cited, `iOS/meApp/Features/Common/Constants/Devices.swift` (type `DeviceItemInfo`), **does not exist in any shipped build**:

```
$ git show v5.0.3:iOS/meApp/Features/Common/Constants/Devices.swift
fatal: path ... exists on disk, but not in 'v5.0.3'
```

On `main` and `v5.0.3` the same data lives in **`Scales.swift`** as **`ScaleItemInfo`** (identical blob on both refs). `Devices.swift` is the Phase 2 rename in the working tree. Cite the working-tree path in a CS reply and you're describing code the customer doesn't have. That single check is why this step is a hard rule, not a nicety.

The skill should also **state which ref it read** in the investigation summary, so a wrong assumption is visible rather than silent.

**Retention / gaps the skill must account for:**
- `.debug` rows are **console-only, never persisted** — absent from any customer log by design. Don't conclude "the app never got there" from a missing debug line.
- Retention is 30 days (`AppConstants.TimeoutsAndRetention.logRetentionDays`), auto-cleaned at launch.
- Successful uploads delete local rows → **a second log can be missing the earlier failure.** This explains a lot of "the log doesn't show it."
- No PII/tokens by policy, so there's no customer identity in the rows themselves — correlate via `accountId`.

**⚠️ The one gap I can't close from the repo:** the exact shape of the **file you receive by email** (rendered text? JSON? CSV? one row per line — which columns, what date format?). I found no sample on your machine. **Paste one real log when we build this** and I'll pin the parser to it. Until then the skill uses tolerant parsing (find timestamps + level markers + tags heuristically) and reports what it couldn't parse rather than guessing.

**Subsystem checklists** (the part that does the work):

- **Pairing/BLE** — scan started? device discovered (MAC/SKU)? connect attempt? handshake/registration completed? user slot assigned? Is a device already registered on the account? Any duplicate device rows?
- **Entry sync** — reading received from scale → written locally → `POST /v3/entries/` → response. Which arrow breaks?
- **Wi-Fi setup (0384/0385/0396/0397/0412)** — network list came *from the scale* (2.4 GHz only), credentials submitted, join result, subsequent server-side entries.
- **Health integration** — HealthKit / Health Connect write attempts, permission grants, which data types.
- **Auth** — login, token refresh, 401s, account switching.
- **History/data** — entry counts by date range, sync cursor position, deletions (relevant to every "my data disappeared" report).
- **Crash-adjacent** — clusters of `type=e`, and what immediately preceded them.

---

## 6. `references/known-issues.md` — seeded from the channel

Thirteen entries I can seed today, all from answers you already gave in #_cstech (each entry: symptom → mechanism → CS-safe explanation → what to tell the customer → source thread):

1. **0412 Wi-Fi list missing the 5 GHz SSID** — the list is scanned and sent *by the scale*, which is 2.4 GHz-only. 5 GHz networks can never appear.
2. **BT scale holds one phone at a time** — second phone shows not-connected; this is by design.
3. **"Users" option greyed out** — only tappable on the phone that's *actively* BT-connected right then.
4. **Scale shows the user's name before sending** — normal identification step; not new in 5.0.0, and not a Wi-Fi fallback. Transport is chosen *after* name selection: BT if the app is open and connected, Wi-Fi otherwise.
5. **Scale displays "E1"** — scale timed out waiting for the app to finish pairing. Scale side is fine; our handshake didn't complete.
6. **Lean body mass in Apple Health** — `Weight − (Weight × BodyFat%/100)`, calculated by us, written as a weight value, never displayed in-app.
7. **Goal % / progress bar not moving** — the bar is anchored to the *starting weight* on the Goal Setting screen, not the latest weigh-in. If current > starting, it stays empty and only "lbs to goal" moves.
8. **Duplicate paired scale, same user slot** — shouldn't be possible; when it happens it can crash on an incoming reading. Fix: remove both, re-pair.
9. **No scale registered on the account** — the first thing to check for any "not receiving readings" report.
10. **Near-identical duplicate accounts** (`.con` vs `.com`) — separate accounts; looks like data loss.
11. **Password reset link** — expires after a few minutes and is single-use. *(Exact duration still pending from Jebins as of 2026-07-28 — mark as unconfirmed.)*
12. **Delete a profile from the scale** — needs an active BT connection: Settings → Add & Edit Scales → shows "Connected" → tap scale → Users.
13. **Fixed in 5.0.2** — HealthKit/Health Connect auto-sync, dashboard showing average instead of latest, crashes, Android data loss, QR/barcode landscape, 0375 discover popup, accessibility-zoom layout. *(Still open: "1313" Wi-Fi-connected-but-not-syncing; general performance.)*

Each run that lands on a *new* confirmed cause appends to `knowledge/resolved.md`, so the playbook compounds.

---

## 7. `references/reply-templates.md`

One template per outcome A–E, each following my established shape: address the reporter → verdict in plain words → **bold** the key mechanism → numbered "what to have him do" → explicit questions back → isolated-or-pattern question → Jira link. Hard rules baked in:

- No raw log lines, no `tag`/class names, no internal jargon — CS forwards this to customers.
- If we couldn't reproduce, say so and say what we tested on.
- Never assume the symptom: ask for starting weight / goal weight / screenshot when the report is vague (as I did on the goal-percentage one).
- Always ask whether other users are hitting it.

---

## 8. `references/device-catalog.md`

Points at the authoritative in-repo source **on the released ref** — `main:iOS/meApp/Features/Common/Constants/Scales.swift`, the `SCALES` array of `ScaleItemInfo`, which gives every SKU its `setupType` and `bodyComp` flag. *(In the working tree this file is `Devices.swift` / `DeviceItemInfo` — same data, Phase 2 rename. See §5b.)*

| Family | SKUs | setupType |
|---|---|---|
| AppSync | 0340–0371 | `.appSync` |
| Bluetooth | 0375, 0376, 0380, 0382 · 0378, 0383 | `.bluetooth` · `.lcbt` |
| Wi-Fi | 0385, 0396 · 0384, 0397 | `.wifi` · `.espTouchWifi` |
| BT+Wi-Fi (R4) | **0412** (AccuCheck Verve) | `.btWifiR4` |
| Baby | 0220, 0222 | `.babyScale` — **`develop` only, not in any shipped build** |

The released catalogue is **24 models**; the working tree lists 26. The two extra are the baby scales, which Phase 2 adds — another instance of why §5b matters. Note also that this array backs the *help-screen* model list, so absence from it isn't proof a SKU is unsupported.

Why it matters: the SKU decides which checklist applies. Asking a `0375` owner about Wi-Fi networks, or a `0397` owner about Bluetooth pairing, wastes a round-trip with the customer. The skill resolves SKU → transport before it says anything.

---

## 9. What one run outputs

1. **Investigation summary** (for me) — parsed report block, freshness/identity check, session timeline, subsystem verdicts with log evidence, code references, outcome A–E + confidence.
2. **#_cstech reply draft** (for CS) — in my voice, ready to paste into the thread.
3. **MOB bug ticket draft** — only for outcome C. Follows `/mob-jira-issue`: `MOB` project, Bug, **PHI Involved** set, a **GG-** priority, exactly one platform label (`ios`/`android`) + `release-X.Y.Z`, active sprint, Story Points, description carrying account ID / device / OS / app version / SKU / the log evidence / the #_cstech permalink. Drafted, not created.

Then: *"Append this root cause to the playbook?"*

---

## 10. What I need from you

| # | Item | Why | When |
|---|---|---|---|
| 1 | **One real emailed log** (any customer, ideally a pairing failure) | pins the parser to the actual file shape — the only real unknown left | before/at build |
| 2 | **A second log of a different kind** (entry-sync or Wi-Fi), if handy | proves the format assumption generalises | nice-to-have |
| 3 | **One recent case end-to-end** — CS message + log + the reply you sent | lets me tune the reply templates against a known-good answer | at build |
| 4 | Confirm the reset-link expiry once Jebins replies | closes playbook entry K-11 | whenever |

Nothing else. The channel history, log schema, SKU catalog, and Jira conventions I already have.

---

## 11. Build order

- **Phase 1 —** `SKILL.md` + `device-catalog.md` + `code-lookup.md` + `known-issues.md` (13 seeded entries). Already useful: instant triage on recurring questions, no log needed.
- **Phase 2 —** `log-format.md` (incl. the platform fingerprint + full tag lists) + the parser, pinned to your sample log. This is the core.
- **Phase 3 —** `reply-templates.md`, tuned against case #3 above.
- **Phase 4 —** dry-run on 2–3 past cases where I already know your answer; compare the skill's verdict to yours and correct the gaps.
- **Phase 5 —** `knowledge/resolved.md` append flow.

Phases 1–3 are one working session. Phase 4 is the one that decides whether this is trustworthy.

---

## 12. Limits — stated up front

- **It won't watch anything.** No email polling, no Slack monitoring. You invoke it.
- **It won't write.** No Slack posts, no Jira creation. Drafts only, by your choice.
- **It can't see the backend.** No server logs, no "Mark" admin system, no DB. Account-side questions (Jebins' territory) will be flagged as backend, not answered.
- **A log that doesn't contain the failure can't be made to.** Outcome D exists precisely so the skill says "get a fresh log" instead of manufacturing a cause.
- **Screenshots/videos** in the CS message: I can read images you paste, but that's a manual add-on, not part of the pipeline.
- **meApp only** (WG / Balance / baby) to start. SageApp/Kettle would need its own log format and playbook — additive later, not assumed now.
