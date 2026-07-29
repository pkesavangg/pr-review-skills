---
name: cs-triage
description: Triage a customer-reported app issue from #_cstech using the diagnostic log the customer emailed in — work out whether it's expected behaviour, an account/data state problem, a real defect, or an unusable log, then draft the CS reply and (if it's a defect) a MOB bug ticket. Use whenever a CS report from #_cstech is pasted in (the "ACCOUNT ID / DEVICE / DEVICE OS / APP VERSION" block), whenever a "Weight Gurus App Help Request" log email or its Timestamp/Data rows are pasted or attached (including .ini/.txt/.csv exports of it), or when asked to "analyse this log", "check this customer log", "triage this CS issue", "what's wrong for this customer", "did the log show anything", "cs_tech issue", or when a customer log is pasted alongside a symptom. Drafts only — never posts to Slack and never creates a Jira issue.
---

# CS issue triage (#_cstech)

Turn one customer report + one diagnostic log into a verdict, a reply CS can send, and a ticket
draft. **You draft; Kesavan sends.** Never post to Slack, never create a Jira issue, never comment
on a ticket from this skill.

**Inputs:** the CS message (pasted) and the log (pasted). Either may arrive alone — if the log is
missing, run §1–§2 and §7 only, and say what's needed.

---

## The one rule that keeps answers honest

The customer is running a **released** build. Their app does **not** contain the code in your
working tree — the checkout is usually `develop` (Phase 2) or a feature branch. Read the code at the
**ref matching their APP VERSION** (`reference/code-lookup.md`) and **name that ref in your
summary**. A confident answer derived from unreleased code is worse than no answer.

Never invent a cause. "The logs confirm X but don't point to a cause" is a valid, frequently
correct conclusion.

---

## 1. Parse the report and echo it back

Two different inputs arrive, and they carry different fields.

**The CS message** (from Jaz, Urmila, or Katy) carries a fixed block:

```
<customer email>  WG            ← or "Balance Health" / baby
ACCOUNT ID: <22-char id>
DEVICE: Apple iPhone (15)        ← or Samsung SM-A536U
DEVICE OS: iOS 26.5.2            ← or Android 16
APP VERSION: 5.0.3
<narrative: the symptom + what CS already tried>
```

**The emailed log** (`guru@weightgurus.com` → `info`/`dev`) carries a *different*, thinner header —
no account id, no OS, and `Device:` is null because neither app attaches device info to the upload:

```
Weight Gurus App Help Request - <customer email>
Email from: <customer email> · Name · DOB · Device: null · App Version · Account Created
Timestamp	Data      ← two columns only, tab-separated
```

Extract: customer email · account id · device · OS · app version · product · **scale SKU** ·
reporter · thread link if given · the symptom in one line. Take account id, device/OS and SKU **from
the log rows** when the header lacks them — `reference/log-format.md` §Recovering device/OS/SKU has
the exact rows. Print this back as a table **before** analysing; a bad parse must be visible
immediately.

Missing SKU or app version? Note it; both change the analysis.

**One email may contain several customers' logs**, concatenated — each new
`Weight Gurus App Help Request - <email>` line starts a new section, and the sections can be
different platforms. Split first, triage each separately, and never let rows cross a section
boundary.

---

## 2. Three gates before any analysis

Run all three. Each has produced a wrong answer on a real report.

**2a. Platform — from the log, not the report.** Derive iOS vs Android from log content per
`reference/log-format.md` §Platform. The decisive signal is the **tag prefix**: every iOS row is
`Tag: message`, no Android row has a tag at all — the two uploaders differ, so it can't cross over.
`HealthKit*` ⇒ iOS, `Health Connect` ⇒ Android. State the evidence. The `DEVICE` line is
CS-transcribed and has been wrong.

**2b. Log freshness — and which way the file is ordered.** **Android arrives newest-first, iOS
oldest-first**, so read both ends before deciding where things stopped. Compare the failure window to
the log's real range. If the log doesn't cover it → **outcome D**, stop, draft the "capture a fresh
log immediately after it fails" reply. Two platform facts change what "missing" means:
*iOS* deletes the account's local rows after a successful upload (so a second log may genuinely lack
the earlier failure); *Android* keeps them but only ever uploads **the last 5 days**. Both upload the
**active account only**. iOS *scale-log* rows are all stamped at upload time — never build a timeline
from them.

**2c. Identity cross-check.** Does the log's platform / app version / account id match the report?
Any mismatch gets stated out loud — it usually means the customer is describing a different phone
than the one that produced the log.

---

## 3. Check the playbook before investigating

Read `reference/known-issues.md`. If the symptom matches a confirmed entry, say so up front
("this matches K-01 — the 0412's network list is scanned by the scale, 2.4 GHz only"). Still confirm
against the log, but this is what turns a long investigation into a short one — most channel volume
is recurring questions.

---

## 4. Resolve the SKU before drawing conclusions

Look the scale up in `reference/device-catalog.md`. Its setup type decides which checklist applies
and which questions are even sensible. Asking a `0375` owner about Wi-Fi networks, or a `0397` owner
about Bluetooth pairing, wastes a round-trip with a real customer.

---

## 5. Build a timeline, then run the checklist

The emailed file has **no `sessionId`, `tag`, or `type` columns** — the uploader drops them, so you
cannot group by session or scan for `type=e`. Instead:

- Normalise to ascending order, then segment by **time gaps** and lifecycle markers
  (`Startup flow complete`, `Navigation to dashboard successful`, setup-in-progress toggles).
- Summarise per segment: how it started, the account/device state, what was attempted, where it stopped.
- Find failures by **keyword** — `Failed`/`Error`/`Exception`, `failures=` non-zero, `HTTP 4xx/5xx`,
  plus the should-not-be-zero counts listed in `reference/log-format.md` §Finding failures.
- Join continuation lines: an iOS row with extra data spans two lines (a quoted `"Tag: message"`
  then a `["…"]` payload).
- **Divide entry/account values by 10** before quoting them (`weight: Optional(1218.0)` = 121.8 lb).

Then run the checklist matching the symptom from `reference/log-format.md` §Checklists — pairing,
entry sync, Wi-Fi setup, health integration, auth, history/data, or crash-adjacent. Each item is a
question answered with a verdict **plus the log evidence behind it**.

When the log is ambiguous, use the iOS tag prefix (or the Android message wording) to jump to the
source — **at the released ref** (§the one rule, and `reference/code-lookup.md`). This is what
separates "expected behaviour" from "real defect".

---

## 6. Classify

| Outcome | Meaning |
|---|---|
| **A — expected behaviour** | The app is doing what it's designed to do; explain the mechanism |
| **B — account/data state** | Something real but fixable by instruction (no scale registered, duplicate pairing, duplicate accounts) |
| **C — real defect** | Raise a MOB bug |
| **D — not enough evidence** | Log doesn't cover the failure, or a specific fact is missing |
| **E — already fixed** | Shipped in a newer build; tell CS to update and re-check |

State confidence, and say plainly when the log doesn't reveal a cause. For **E**, confirm the fix
really is in that build — `git log`/`git diff` between their version and the fixed one
(`reference/code-lookup.md`), not memory.

---

## 7. Output three things

1. **Investigation summary** (internal) — the parsed table, the three gate results, **the ref you
   read**, the session timeline, checklist verdicts with evidence, code references, outcome +
   confidence.
2. **CS reply draft** — from `reference/reply-templates.md`, matching the outcome. Follows Kesavan's
   shape: verdict in plain words → **bold** the key mechanism → numbered "what to have them do" →
   explicit questions back → "have other users reported this?" → ticket link if raised.
3. **MOB bug draft** — outcome C only. Bug in `MOB` under the right epic; PHI Involved, Bug Severity,
   Environment Found = Production; one platform label matching §2a's verdict; account id, device, OS,
   app version, SKU, log evidence, and the #_cstech thread link in the body. Follow the
   `mob-jira-issue` skill for exact field shapes. **Draft it — do not create it.**

Then offer: *"Add this root cause to the playbook?"* — on a yes, append an entry to
`reference/known-issues.md` in the existing format.

---

## Hard rules

- **No raw log lines in the CS reply.** No `tag`s, class names, or internal jargon — CS forwards
  this text to customers. Keep the mechanism, drop the implementation.
- **Never commit customer data — the log is full of it.** Real logs carry the customer's email, name,
  DOB, zipcode, height, goal weight, entry weights, BLE MAC addresses, and the full FCM push token.
  Playbook entries carry *explanations* only; the log stays in the chat — never a repo file, never a
  Confluence page, never a Jira description. Bug tickets get paraphrased evidence plus the account id.
- **Say when we couldn't reproduce**, and what was tested on.
- **Always ask whether other users are hitting it** — isolated vs pattern changes the priority.
- **Don't assume the symptom.** If the report is vague ("the percentage isn't updating"), ask for
  the specific values and a screenshot rather than guessing which number they mean.
- **Backend questions are Jebins'.** No server logs or DB access here — flag them, don't answer them.

## Reference

| File | Use |
|---|---|
| `reference/log-format.md` | Log schema, platform fingerprint, per-symptom checklists |
| `reference/code-lookup.md` | APP VERSION → git ref, and how to read code at that ref |
| `reference/device-catalog.md` | SKU → setup type / body-comp |
| `reference/known-issues.md` | Confirmed causes and their CS-safe explanations |
| `reference/reply-templates.md` | One reply shape per outcome A–E |
