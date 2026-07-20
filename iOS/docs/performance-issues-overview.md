# Weight Gurus (iOS) — Performance Issues, in Plain Words

**Date:** 2026-06-05 · *Updated 2026-07-08* · **Epic:** MOB-516
**Platform:** iOS only
**Audience:** Anyone — product, QA, support, leadership. No code knowledge needed.

---

> ## ✅ Progress update (2026-07-08)
> **The worst problem — the app freezing on the loading screen after login — is now fixed** (work item MOB-1433). The heavy history-processing was moved off the part of the app that has to stay responsive, so the home screen no longer waits on it, and the login → home wait is gone.
>
> **What's still to do:**
> 1. **The graph-scroll work is now largely done** (work item MOB-518, since merged): the weight-chart engine was rebuilt and the slow multi-line "baby/percentile" path was sped up (it now finds points the fast way instead of scanning them all), so the new blood-pressure and baby-growth graphs land on the fixed engine. One small internal caching cleanup remains, but the scroll stutter this item described has been addressed.
> 2. **On very large accounts (10,000+ entries), some screens are still slow to open** (e.g. History) because the app still re-processes the whole history in the background instead of remembering the result. This "remember the result" fix is a bigger, separate piece that's planned but not yet done.
> 3. Smaller cleanups (log-writing, health-sync, account switching) are partly done; the rest are low-priority.
>
> The rest of this document is the original plain-English write-up. It still reads correctly as *the problem*; just note that item 1 in "What users actually feel" (the loading freeze) is the part now fixed.

## The one-line story

The app got rewritten for 5.0, and in the rewrite it started **redoing a lot of heavy work over and over** — most painfully, **re-reading and re-processing your entire weight history every time the home screen opens or refreshes.** On accounts with a few thousand entries, that makes the app **freeze for several seconds**, drains battery, and uses too much memory. The good news: **the work it's redoing doesn't need to be redone that often.** We can make it remember the result and only recompute when your data actually changes — which fixes the freezing, the battery, and the memory **all at once**, without changing anything you see or do. *(Update: the loading-screen freeze is now fixed — see the progress note above; the graph-scroll stutter and the large-account slowness are what remain.)*

---

## What users actually feel

These are real, and they match both Apple's performance reports *and* recent App Store reviews ("the lag between clicking and info display is unacceptable", "you can no longer drag the line cursor across the graph"):

| What you notice | What's happening underneath |
|---|---|
| Home screen / graph **freezes for a few seconds** when it loads or refreshes | The app re-reads and re-processes your *whole* history every time |
| The graph **stutters when you scroll** | The app re-does drawing math on every frame instead of once |
| **Switching accounts is slow** | Same re-processing happens again for the new account |
| **Battery drains faster** | All that repeated work keeps the processor busy |
| App sometimes **reloads after being in the background** | It uses too much memory, so the phone shuts it down |

Measured proof: on a **4,000-entry** account, just *loading* the screen froze the app for **3–4 seconds at a time**, repeatedly.

---

## Which parts of the app are affected

- **The dashboard / graph** — the worst hit. Loading, refreshing, scrolling, and switching the week/month/year/total views.
- **Account switching** — re-triggers the same heavy load.
- **Battery & memory** — side effects of the same repeated work.
- **The upcoming 5.1.0 graphs (blood pressure + baby growth)** — ⚠️ these will be **worse** than today's weight graph unless we fix the engine first, because they draw more lines. The baby growth chart in particular draws 5–10 lines at once on the slowest part of the code.

---

## Why it happens (no jargon)

Think of it like a chef who **re-chops every vegetable in the fridge from scratch every single time someone orders a salad** — even if nothing in the fridge changed since the last order. It works, but it's exhausting and slow once the fridge is full.

That's what the app does with your history: every time the graph appears, it pulls **every entry** out of storage and rebuilds the summary from zero — even when you just looked at it a second ago. With a small fridge (few entries) you don't notice. With a full one (thousands of entries) it freezes.

Three smaller versions of the same "redo it every time" habit show up in: scrolling the graph, saving synced data one-item-at-a-time, and re-syncing your full health history even when only one entry changed.

---

## How we'll fix it (and why your experience won't change)

The fixes are about **doing the same work less often and smarter — never about removing features.**

1. **Remember the result.** Compute the graph summary once, and reuse it until your data actually changes. (Like the chef prepping the salad once and keeping it ready until the fridge changes.)
2. **Only draw what's on screen.** Stop re-drawing the whole graph every frame; draw the visible part and find points faster.
3. **Save in bulk.** When syncing many entries, save them together instead of one at a time.
4. **Sync only what changed.** Don't re-process your entire health history for a single new entry.

**Everything you see stays identical** — same graphs, same numbers, same scrolling, same averages, same week/month/year/total views. The averages still update when you finish scrolling, the y-axis still adjusts to the visible data, new entries and deletions still update the graph, unit and "weightless" changes still apply. It just stops freezing.

---

## Will the fixes cause new bugs?

That's the right question to ask. Our honest answer:

- **No feature is being removed or changed** — the goal is "same behavior, faster."
- The risk is **ordinary implementation risk** (a fix done slightly wrong), not a feature trade-off. We control that with **automated tests** and by **measuring before and after** on a large account to prove the freeze is gone and nothing else broke.
- We will **measure first, then build** — we already captured proof of the #1 cause, and we'll re-measure after each change.

---

## What we're asking for

Treat the graph/data-load fix as the **top priority and as a prerequisite for the 5.1.0 blood-pressure and baby graphs.** If we ship those new graphs on the current engine, the freezing gets worse for brand-new users. If we fix the engine first, 5.1.0 launches on a fast app.

> A detailed engineering version of this (with the exact code areas, the epic, and the task breakdown) is in **`performance-remediation-plan.md`**. The full investigation and measurements are in **`performance-analysis-5.1.0.md`**.
