# Dashboard (Week / Month): Hybrid Latest-Day + Daily-Average

**Status:** Planned — follow-up to PR [#1948](https://github.com/dmdbrands/meApp/pull/1948) (already on TestFlight as 5.0.2)
**Driver:** UX (Matt), 2026-05-XX Slack thread — see §0 for full quote.
**Scope:** iOS Week and Month graph tabs. Year and Total tabs unchanged.

---

## 0. Decision (verbatim, per Matt)

> **Matt:** "we should show the daily average for any point that is selected besides the latest entry."
>
> **Kesavan, to confirm:** "you'd like us to show the daily average for all graph points (similar to 5.0.0), with only the most recent day's point showing the latest entry value instead. For example, in the Week tab: if the latest entry is on Friday, the Friday point would show the latest weight recorded that day, while Sunday–Thursday would show the daily average of each day's entries. One question on the label: when a user taps the most recent point (Friday in this example), should the label read "latest entry"? And for the other days, "day average"?"
>
> **Matt:** "Yes I think that's how it should work. And yes those labels work too."

---

## 1. Behavioural change

### What 5.0.2 currently does (PR #1948)
Every day on Week / Month plots its **latest non-null positive** entry. Header label is *hidden* on selection. Metric info sheet reads `Measurement taken Feb 1, 2025` for any selected Week/Month day.

### What Matt wants

Per day on Week / Month:

| Day | Graph point value | Tile values when selected | Header label when selected | Metric Info label when selected |
| --- | --- | --- | --- | --- |
| **Most recent day with entries** (call it *D*) | latest entry's weight (current 5.0.2 logic) | latest non-null positive per metric (current 5.0.2 logic) | `latest entry` | `latest entry · MMM d, yyyy` |
| Any other day | **daily average** of weight (5.0.0 logic) | **daily average** per metric (5.0.0 logic) | `day average` | `day average · MMM d, yyyy` |

Year and Total tabs are unchanged — still plot monthly averages, still read `month average` on selection.

### Worked example

Week ending Friday 2026-05-15. Friday (today) has multiple weigh-ins; earlier days each have one or more.

```
Sun  Mon  Tue  Wed  Thu  Fri (today)
 ●    ●    ●    ●    ●    ●
 ↑    ↑    ↑    ↑    ↑    ↑
 avg  avg  avg  avg  avg  latest entry
```

Tap Friday → header reads `latest entry`, tile values come from the latest weigh-in.
Tap Tuesday → header reads `day average`, tile values are the average of Tuesday's weigh-ins.
Tap nothing → header reads `week average · may 10 – may 16, 2026` (unchanged from today).

---

## 2. Why "most recent day" — not "today"

We define D as **the most recent day that has entries**, *not* "today's calendar date". This matters when the user hasn't weighed in for several days:

- Last entry was Wednesday, user opens app on Friday → Wednesday is D and uses latest-entry semantics. Thursday/Friday have no points to plot at all.
- User adds an entry on Friday → cache invalidates → Friday becomes D, Wednesday flips back to daily-average semantics.

Implementation drives off the data, not the calendar, so the behaviour is correct regardless of streaks / gaps.

---

## 3. Architecture — single fix point holds

The data flow from PR #1948 is unchanged:

```
EntryService.aggregateByDay(...)            ← SwiftData entries
EntryService.aggregateByDayFromDTOs(...)    ← Background-thread DTOs
        │
        ▼
   BathScaleWeightSummary  (one row per day)
        │
        ▼
DashboardDataManager.cachedSortedDailySummaries
        │
        ▼
DashboardStore.continuousOperations  →  chart points & selected-point tiles
```

The hybrid rule still lives entirely in the daily aggregator. We add one branch:

```
for each day in grouped:
    if day == latestDayKey:
        produce summary using latestPositive helpers   ← current 5.0.2 logic
    else:
        produce summary using avgWeight / avgNonZero   ← restored 5.0.0 logic
```

Both helper families are already in `EntryService.swift` — we kept `avgWeight` and `avgNonZero` because the monthly aggregator (Year/Total) still uses them. Nothing new to write at the data layer beyond the branch.

Year and Total tabs route through `aggregateByMonth` / `aggregateByMonthFromDTOs`, which are completely untouched. So the hybrid behaviour cannot leak into those tabs.

---

## 4. File-by-file plan

### 4.1 `iOS/meApp/Data/Services/EntryService.swift`

Inside `aggregateByDay(entries:accountId:)` (currently lines 998–1046, post-#1948):

Before the `grouped.compactMap` block, capture the latest day key:

```swift
// Hybrid rule: only the most-recent day with entries surfaces latest-entry semantics;
// every other day shows the daily average (5.0.0 behaviour). See docs/dashboard-hybrid-latest-vs-average.md
let latestDayKey = grouped.keys.max() ?? ""
```

Inside the compactMap closure, branch on `day == latestDayKey`:

```swift
return grouped.compactMap { (day, dayEntries) -> BathScaleWeightSummary? in
    let validEntries = dayEntries.filter { /* unchanged weight > 0 filter */ }
    guard !validEntries.isEmpty else { return nil }
    guard !day.isEmpty else { return nil }
    let date = DateTimeTools.getDateFromDateString(day, format: "yyyy-MM-dd")
    let count = validEntries.count

    if day == latestDayKey {
        // Latest-day branch — same as PR #1948
        let sortedDesc = validEntries.sorted { $0.entryTimestamp > $1.entryTimestamp }
        let latestTimestamp = sortedDesc.first?.entryTimestamp ?? ""
        let latestWeight = Double(sortedDesc.first?.scaleEntry?.weight ?? 0)
        return BathScaleWeightSummary(
            accountId: accountId,
            period: day,
            entryTimestamp: latestTimestamp,
            date: date,
            count: count,
            weight: latestWeight,
            bodyFat:               latestPositive(sortedDesc) { $0.scaleEntry?.bodyFat },
            // ... (full list as in #1948)
        )
    } else {
        // Pre-#1948 daily-average branch — restored verbatim
        let latestTimestamp = validEntries.map { $0.entryTimestamp }.max() ?? ""
        return BathScaleWeightSummary(
            accountId: accountId,
            period: day,
            entryTimestamp: latestTimestamp,
            date: date,
            count: count,
            weight: avgWeight(validEntries.compactMap { $0.scaleEntry?.weight }),
            bodyFat: avgNonZero(validEntries.compactMap { $0.scaleEntry?.bodyFat.map(Double.init) }),
            muscleMass: avgNonZero(validEntries.compactMap { $0.scaleEntry?.muscleMass.map(Double.init) }),
            // ... (full list — copy from pre-#1948 git history)
        )
    }
}.sorted { $0.period < $1.period }
```

Apply the same `latestDayKey` + branch in `aggregateByDayFromDTOs(_:accountId:)` using `latestPositiveDTO` for the latest day and `avgWeight` / `avgNonZero` for the rest.

> **Note** the `latestDayKey` is the lexicographic max of `YYYY-MM-DD` strings, which is equivalent to chronological max — same trick the existing `latestTimestamp` computation already relies on.

Helpers stay as-is. No new helpers required.

### 4.2 `iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift`

The store needs to know **whether the currently selected point is the latest day** so it can pick the right label. Add a single computed flag:

```swift
/// True when the currently selected graph point is the most recent day in the data set.
/// Used to pick "latest entry" vs "day average" labels on Week/Month tabs.
private var isLatestDaySelected: Bool {
    guard graph.selectedPeriod == .week || graph.selectedPeriod == .month else {
        return false
    }
    guard let selectedDate = graph.selectedPoint?.date ?? graph.selectedXValue,
          let latestDate = continuousOperations.last?.date else {
        return false
    }
    return Calendar.current.isDate(selectedDate, inSameDayAs: latestDate)
}
```

> `continuousOperations` is already sorted ascending by period, so `.last?.date` is the latest day.

Rewrite `selectionPrefix(for:)` to consume that flag. The function loses its purely-period-based signature — it now needs selection context too:

```swift
/// Prefix shown alongside a selected graph point in the trend-view header and (lowercased)
/// in the metric-info sheet. Week/Month differentiate the latest day from earlier days
/// per UX direction (Matt, 2026-05-XX).
private func selectionPrefix(for period: TimePeriod) -> String {
    switch period {
    case .week, .month:
        return isLatestDaySelected ? "latest entry" : "day average"
    case .year, .total:
        return "month average"
    }
}
```

**Remove** the `hidesWeightDisplayLabel` flag and the related opacity wiring — labels are now always shown:

- `DashboardStore.swift` — delete `var hidesWeightDisplayLabel: Bool` and the space-substitution branch inside `weightDisplayLabel`. `weightDisplayLabel` returns the result of `selectionPrefix(for:)` directly.
- `WeightTrendView.swift` — delete the `.opacity(dashboardStore.hidesWeightDisplayLabel ? 0 : 1)` and `.animation(.none, value:)` modifiers added in PR #1948.

`composeMetricInfoLabel`'s whitespace-trim added in #1948 stays — it's harmless and protects against accidental empty prefixes from future code.

### 4.3 Metric Info sheet labels

Update `metricInfoSelectionLabel(date:period:)` (added in the last commit) to use the new period-aware prefix instead of the unconditional `Measurement taken` for Week/Month:

```swift
private func metricInfoSelectionLabel(date: Date, period: TimePeriod) -> String {
    let dateText = formatMetricInfoSingleDate(date, period: period)
    return composeMetricInfoLabel(prefix: selectionPrefix(for: period), dateText: dateText)
}
```

Result by case (all lowercased by `composeMetricInfoLabel`):

| Selection state | Label |
| --- | --- |
| Week/Month, latest day selected | `latest entry feb 1, 2025` |
| Week/Month, earlier day selected | `day average jan 28, 2025` |
| Year/Total, point selected | `month average feb 2025` |
| No graph selection | `<period> average · <date range>` (unchanged) |
| Opened from History list | `Measurement taken February 1, 2025` (unchanged) |

> History entries keep the distinct `Measurement taken …` wording so users can tell at a glance that the sheet was opened from history vs from a dashboard selection. The `measurementTakenLabel(for:)` helper added in the last commit stays in place but is only called from the history branch.

### 4.4 `iOS/meApp/Features/Dashboard/Views/Screens/WeightTrendView.swift`

Revert the opacity / animation modifiers added in #1948. The label is now always shown:

```swift
Text(dashboardStore.weightDisplayLabel)
    .fontOpenSans(.subHeading2)
    .foregroundColor(theme.textSubheading)
    .padding(.leading, .spacingSM)
```

### 4.5 Anything else?
- `GraphView`, the four section view models, `DashboardMetricsManager`, `DashboardDataManager` — no changes. They consume `BathScaleWeightSummary` fields directly, so the per-day branching at the aggregator layer flows through automatically.
- Cache invalidation — `BathScaleWeightSummary` rebuilds whenever entries change (same as today), so adding a new entry that flips which day is D triggers correct re-render automatically. No new cache work.

---

## 5. Test plan

### Unit tests (`iOS/meAppTests/`)

Extend `EntryServiceAggregationTests.swift` (or create it if PR #1948 hasn't shipped that yet) with the hybrid cases:

1. **Three days, latest has multiple entries** — older two days return averages, newest day returns latest-non-null-positive per metric. Spot-check weight and at least one secondary metric per day.
2. **Three days, only the latest has multiple entries** — older two are single-entry, so they "look like" the latest path's single-entry case but use the average branch internally. Output must still match the single entry's values.
3. **Single day total** — single-day data set; that day IS the latest day, uses latest-entry branch. Equivalent to single-entry baseline.
4. **Future-dated entry** — user manually backdates / forward-dates an entry; whichever day key sorts max becomes D. Confirm the branch picks correctly.
5. **DTO parity** — repeat #1 against `aggregateByDayFromDTOs` to prove DTO code path produces identical summaries.
6. **Month aggregation untouched** — smoke test that `aggregateByMonth` still averages. No regression to Year/Total.

### Manual / device tests

1. **Today has multiple entries; earlier days each have multiple entries.** Tap Sun → Sat in order. Header alternates between `day average` (Sun–Thu, if Friday is the latest day) and `latest entry` (Fri). Tile values change to match each day's rule.
2. **Add a new entry on today** while on Week tab. Today's point shifts to the new latest value. Previous "today" stays where it is (still the latest day, since `latestDayKey` is data-driven).
3. **Add a backdated entry** (e.g. user records yesterday's weigh-in today). The backdated day is *not* the latest day; it should plot the daily average of all entries on that backdated day. Header on selection reads `day average`.
4. **Today has zero entries** (skipped weigh-in). Latest day is yesterday — yesterday's point uses latest-entry semantics, day before that uses average.
5. **Week tab with the latest entry NOT in the visible window** (e.g. scroll to a week from a month ago). Every visible point in that older week uses daily-average semantics — `latest entry` label should not appear for any of them.
6. **Switch tab: Week → Month** with the latest entry visible in both. The same day should keep the `latest entry` label across both tabs.
7. **Year and Total tabs unchanged** — every selected point reads `month average`; tile values are monthly averages.
8. **Metric info sheet parity** — open from each of: today/Week (`latest entry feb 1, 2025`), older day/Week (`day average jan 28, 2025`), Year point (`month average feb 2025`), no selection (`<period> average · <range>`), History list (`Measurement taken February 1, 2025`).
9. **Layout** — toggling between selected and non-selected points must not cause a vertical jump in the headline weight (labels are now always present, so this should be inherent — verify anyway).
10. **Weightless mode toggle** on a day with multiple entries — confirm the latest-day-only branch still respects the weightless-mode delta calculation.

### Regression watchlist
- **Goal chip placement** — depends on the latest day's weight, which is the latest-entry value (correct).
- **CSV export** — uses raw entries, unaffected.
- **Streak grid** — uses raw entry presence, unaffected.

---

## 6. Edge cases & risks

| Risk | Mitigation |
| --- | --- |
| `latestDayKey` computed once per `aggregateByDay` call; if entries arrive split across calls (e.g. paginated sync), each call computes its own max and could mis-label a day. | `aggregateByDay` is always called with the full per-account entry set today. Verify by tracing callers (we already did this for #1948). If pagination is ever added, the hybrid rule needs to move one layer up — the data manager that owns the full per-account view. |
| **TestFlight users on 5.0.2 will see graph values shift again** when this update lands (after they just got used to the all-latest behaviour from #1948). | Flagged to Matt in Slack on TestFlight rollout. Not blocking; surface in release notes. |
| Selecting *today's* point shows `latest entry` even on days with only one entry (where average == latest). Semantically fine but might confuse users who think `latest entry` only appears on multi-weigh-in days. | Acceptable per Matt's spec — the label describes the *rule*, not the *number of underlying entries*. |
| Y-axis range shifts when daily-average values replace latest-entry values for non-latest days. | Existing Y-axis recompute on data change handles this — see PR #1948's existing test plan for the same concern. |
| `isLatestDaySelected` depends on `continuousOperations.last?.date` reflecting the same day key the aggregator picked. Both derive from `latestTimestamp`-style logic, so they agree by construction. If they ever drift (e.g. a future refactor changes one but not the other), labels will go wrong silently. | Worth one unit test asserting that `DashboardStore.isLatestDaySelected` returns true exactly when `selectedDate` matches the max key produced by the aggregator. |

---

## 7. Rollout

- New PR against `release/5.0.2` (sibling to #1948) or against `main`, depending on release-manager call. Treat as a follow-up bug fix — `MA-3937` Jira ticket continues to track it; no new ticket required.
- Bundle the unit-test additions in the same PR.
- PR description should explicitly call out:
  - This **reverses one direction** of the original 5.0.2 fix (most days revert to daily-average).
  - This **keeps the other direction** (latest day shows latest entry).
  - Why: per Matt, Slack 2026-05-XX (quote in §0).
- Update `MA-3938` (Android) description to reflect the hybrid rule — Android is still implementing, so the spec change lands before they ship.
- TestFlight rollout: flag in release notes that graph values shift again, since users just saw a shift in 5.0.2.

---

## 8. Out-of-band follow-ups (do not bundle)

- Add an architectural test that pins the per-day branch contract (e.g. property-based test) so a future contributor cannot accidentally remove the hybrid rule.
- Audit other surfaces that surface "today's value" (streak grid, dashboard tiles outside the graph, sharing/export) — confirm they all read from the same `BathScaleWeightSummary.entryTimestamp` and thus pick up the hybrid behaviour for free.
- Long-term: consider whether the `latest entry` label should appear in tile-only views (e.g. "today's weight" elsewhere in the app). Out of scope until UX raises it.
