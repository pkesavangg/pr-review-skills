# Known issues playbook

Confirmed causes and their CS-safe explanations, seeded from answers already given in #_cstech.
**Check this before investigating** — most channel volume is recurring questions.

**Privacy:** entries hold *explanations only*. Never add a customer email, account id, or log
excerpt to this file.

Each entry: symptom CS reports → what's actually happening → what to tell CS → outcome class.

---

### K-01 · 0412 Wi-Fi list is missing the customer's network
**Symptom:** the network the phone is on doesn't appear in the scale's list (e.g. "Buster 5" absent,
"Buster 51" present).
**Mechanism:** the list on that screen is **scanned and sent by the scale**, not the phone — and the
0412 radio is **2.4 GHz only**. A 5 GHz SSID can never appear. A trailing "5" in the name is a strong
hint it's the 5 GHz band.
**Tell CS:** have them join the router's **2.4 GHz** network; if the router only exposes 5 GHz, the
2.4 GHz band must be enabled in router settings. If the real 2.4 GHz network still doesn't appear,
ask for a fresh log.
**Outcome:** A (expected behaviour).

### K-02 · Bluetooth scale only works on one phone
**Symptom:** works on the husband's phone, not the wife's; or "shows Connected on one phone only".
**Mechanism:** the scale holds a BLE connection to **one phone at a time**. Whichever phone is
actively connected works; the other shows nothing until the first disconnects or closes the app.
**Tell CS:** on the *other* phone, turn Bluetooth off or fully close the app, wake the scale, then
retry on the phone that needs it. Both accounts can use one scale — BLE individually per phone, or
Wi-Fi by selecting the right name on the scale.
**Outcome:** A.

### K-03 · "Users" option is greyed out
**Symptom:** can't open the scale's user list; greyed out on both phones.
**Mechanism:** that option only becomes tappable on the phone that is **actively BLE-connected at
that moment**. On any other phone it stays greyed out — expected.
**Tell CS:** connect that phone to the scale first (it shows "Connected"), then the option enables.
**Outcome:** A. Related: K-02, K-12.

### K-04 · "The scale shows my name now — did it switch to Wi-Fi?"
**Symptom:** customer thinks 5.0.0 changed how the scale connects, because it displays their name
and feels slower.
**Mechanism:** name display + user selection has **always** been there — it's how the scale decides
which profile a reading belongs to; 5.0.0 didn't change it. Transport is chosen **after** name
selection: app open with BLE connected → **Bluetooth**; app closed / BT off → Wi-Fi fallback. The
small delay is the identification step, not Wi-Fi.
**Tell CS:** no formula change; still Bluetooth when the app is open. Do ask whether the delay is
specifically *after* the name is selected — that part would be worth investigating.
**Outcome:** A.

### K-05 · Scale displays "E1" while pairing
**Symptom:** scale shows `E1`; app keeps spinning on the pairing screen.
**Mechanism:** `E1` = the **scale timed out waiting for the app to finish pairing**. The scale is
doing its part; our side didn't complete the handshake.
**Tell CS:** confirm the exact phone and OS (reports have named the wrong handset), and try pairing
from a second phone to establish whether it's handset-specific.
**Outcome:** C or D — a real handshake failure worth a ticket once the handset is confirmed.

### K-06 · Lean body mass in Apple Health looks wrong
**Symptom:** lean body mass shows in lbs and the customer questions the value.
**Mechanism:** we calculate it — `Weight − (Weight × BodyFat% / 100)` — per entry and write it to
Apple Health **as a weight value** in the user's unit. It is **not measured by the scale** and is
**not displayed anywhere in the app**, including History.
**Tell CS:** the value comes from us, not Apple; the unit is expected.
**Outcome:** A.

### K-07 · Goal percentage / progress bar doesn't move
**Symptom:** "I updated my goal but the percentage didn't update."
**Mechanism:** the progress bar is anchored to the **starting weight** set on the Goal Setting
screen — **not** the latest weigh-in. If current weight is above starting weight, the bar stays
empty even after changing the goal; only "lbs to goal" moves. There is no numeric percentage shown —
only the bar.
**Tell CS:** don't assume; ask for starting weight and goal weight before/after the change, what
they changed it to, and a screenshot of the Goal Setting screen.
**Outcome:** A — but confirm which number they mean first.

### K-08 · Duplicate paired scale on one user slot
**Symptom:** app crashes when a reading arrives.
**Mechanism:** the account had the **same scale paired twice, both on the same user slot** — which
the app is **supposed to block**. A duplicate that slips through is a likely crash cause on an
incoming reading.
**Tell CS:** remove **both** scales from the app, then pair fresh.
**Outcome:** B, plus C for the pairing guard that let it through.

### K-09 · No scale registered on the account
**Symptom:** "not receiving any readings."
**Mechanism:** the account has **no scale registered** — so nothing can arrive. Check this first for
any "no readings" report; it's fast and it's often the whole answer.
**Tell CS:** free the scale from any other connected phone, wake it, and run setup again on this
phone.
**Outcome:** B.

### K-10 · Two accounts with near-identical emails
**Symptom:** "years of data disappeared" / "the app shows two logins with my email."
**Mechanism:** two **separate accounts** whose emails differ by a character (e.g. `.con` vs `.com`,
or differing case). The system treats them as distinct; the app blocks exact duplicates only. Data
isn't lost — it's on the other account.
**Tell CS:** check for the near-identical address before treating it as data loss.
**Outcome:** B.

### K-11 · Password reset link doesn't work
**Symptom:** customer reports the reset link failing.
**Mechanism:** the link **expires after a few minutes**, and a link that has already been used
cannot be reused. Could not reproduce on our side — the link works when fresh.
**Status:** the exact configured expiry duration is **unconfirmed** — pending from Jebins (backend).
Don't quote a specific duration until it's confirmed.
**Tell CS:** ask what the customer actually saw, how many users hit it, and whether it reproduces.
**Outcome:** A/D — backend-owned, don't answer the duration from here.

### K-12 · Deleting a profile stored on the scale
**Symptom:** customer wants to remove a user profile from the scale itself.
**Mechanism:** possible, but the app must be **BLE-connected** to the scale first.
**Tell CS, in order:** connect over Bluetooth (wake scale, open app) → Settings → Add & Edit Scales →
the scale shows **"Connected"** → tap the scale → **Users** → delete the profile.
**Outcome:** A. Related: K-03.

### K-13 · Issues fixed in 5.0.2
**Fixed in 5.0.2:** Apple Health / Health Connect auto-sync · dashboard showing the average instead
of the latest weigh-in · crashes (incl. barcode scanner) · Android data loss after upgrade ·
QR/barcode camera landscape orientation · the "0375" discover popup on launch · accessibility
zoom/button-shape layout breakage · height reverting (Android only — iOS never reproduced).
**Still open as of 2026-07-28:** the "1313" case (scale confirmed on Wi-Fi at the router but readings
not reaching the app) · general performance/responsiveness · overall UI/UX dissatisfaction (product
feedback, not a defect).
**Tell CS:** ask them to update, then confirm whether the issue persists — and to update their store
review if it's resolved.
**Outcome:** E — but verify the fix really is in that build with
`git log v5.0.1..v5.0.2 -- <path>` before claiming it.

---

## Adding an entry

After a case reaches a **confirmed** root cause, append in the same shape: next `K-nn` · symptom in
CS's words · mechanism · what to tell CS · outcome class · related K-refs. Explanations only — no
customer data.
