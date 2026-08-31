# Dispute ledger

Rows are appended by `/review-pr` § 4b.1 when a prior finding closes as `✅ Accepted` because the author **justified the code technically** or because an **existing test already covered it**. Both mean the reviewer fired on something that didn't need firing.

This is the reviewer's episodic memory. Without it, every run starts blind and the same false positive can be argued down on twenty PRs without anything ever changing.

## What counts as a dispute

| Close reason | Logged? | Why |
|---|---|---|
| Author gave a specific technical justification | ✅ yes | The rule didn't account for a legitimate case |
| "Already covered by test at `path:42`" (verified) | ✅ yes | The rule's precondition was wrong |
| Deferral closed by a verified ticket (`MOB-1234`) | ❌ no | The finding was **right** and is being scheduled |
| `⚠️ Partially` / `❌ Still open` / `🎫 Awaiting ticket` | ❌ no | Not a disagreement with the rule |

## The threshold — never act on one

A single dispute is noise. One author disagreeing once, or one junior pushing back on something they're wrong about, must not reshape a rule — that's how a review system quietly unlearns something true.

**A rule enters the review queue at ≥3 rows spanning ≥2 distinct authors or ≥2 distinct repos.** Crossing the threshold schedules a *human* look at the rule; it never changes behaviour on its own. The outcome of that look is one of:

- **Amend the rule** — the disputes point at a real gap in its logic.
- **Add a documented carve-out** — the rule is right in general, wrong for a named case. Add it to the rule's "Do NOT flag" list.
- **Keep it and note why** — the disputes were wrong. Record that here so the same case isn't re-litigated next quarter.
- **Delete the rule** — it costs more attention than it returns.

**Decay:** rows older than 2 releases (roughly 6 months) stop counting toward the threshold. A rule disputed under an old codebase convention shouldn't stay on the queue forever. Don't delete old rows — move them to § Archive.

## Standing review queue

Rules currently at or over the threshold. Empty is the healthy state.

_(none yet)_

## Log

| Date | Rule | Repo | PR | Author | Reason given |
|---|---|---|---|---|---|
<!-- Append one row per dispute. Newest at the bottom. Keep the rule id in the
     `<dir>/<file>#<slug>` form the run ledger uses, so the two can be joined. -->
| 2026-08-19 | appium/locators#selector-parity-gap | gg-engineering/meAppTest | #205 | AbdurRahmanGG | Reviewer's grep was wrong — the iOS id DOES exist (`AccessibilityID+Settings.swift:113`, applied at `GoalSettingScreen.swift:46`). The real gap is source-vs-runtime (declared + applied, still not in the a11y tree), not a missing symbol; docstring was accurate. |

## Archive (decayed — not counted)

| Date | Rule | Repo | PR | Author | Reason given |
|---|---|---|---|---|---|
