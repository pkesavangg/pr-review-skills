# Graph Label Display

How the dashboard weight/metric graph decides **what text and value to show** — the label above the chart, what changes when you tap a point, and the label inside the Metric Info sheet.

This is a behavioural map of the current branch. Sibling doc: [dashboard-hybrid-latest-vs-average.md](dashboard-hybrid-latest-vs-average.md) covers the "latest entry vs day average" UX rule in depth.

---

## TL;DR

There are **two label surfaces**, both driven by the same graph selection state:

| Surface | Source | Shown where |
|---------|--------|-------------|
| **Trend-view header label** | `DashboardStore.weightLabel` | The `Text(...)` above the chart in [GraphView.swift:77](../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift#L77) |
| **Metric Info sheet label** | `DashboardStore.metricInfoDateLabel(for:)` | Date row in `ScaleMetricsView` |

Both answer the same question — *"what does the number on screen represent?"* — and both react to:

1. **Is anything selected?** (a tapped point / crosshair date)
2. **Which period is active?** (week / month / year / total)
3. **Is the selected day the most recent day with data?** (drives "latest entry" vs "day average")

---

## 1. The header label above the graph

`GraphView` renders a single label and fades it out while a selection callout is on screen:

```swift
// GraphView.swift
Text(dashboardStore.weightLabel.lowercased())
    .opacity(isShowingSelectionCallout ? 0 : 1)   // hidden while crosshair callout shows
```

`isShowingSelectionCallout` just forwards each period's `showCrosshair` flag
([GraphView.swift:46](../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift#L46)).

### `weightLabel` decision order

[`DashboardStore.weightLabel`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L810) resolves in priority order:

```swift
var weightLabel: String {
    // 1. No entries at all → period-shaped empty-state label from today's date
    if !data.hasAnyEntries { return emptyStatePeriodLabel(for: graph.selectedPeriod) }

    // 2. Something is selected → show the selected date
    if let label = selectionLabel() { return label }

    // 3. Nothing selected → show the visible date RANGE for the period
    switch graph.selectedPeriod {
    case .total: return labelForTotalPeriod()
    case .year:  return labelForYearGridlines()
    case .month: return labelForMonthGridlines()
    case .week:  return labelForWeekGridlines()
    }
}
```

#### a. Empty state (`emptyStatePeriodLabel`)
No account entries → derive the current period window from *today*:

| Period | Example |
|--------|---------|
| Week | `feb 15 - feb 21, 2026` |
| Month | `feb, 2026` |
| Year | `2026` |
| Total | `2026` (current year per spec) |

#### b. Default — no selection (the visible range)
The label describes **what window you are scrolled to**:

| Period | Format | Example |
|--------|--------|---------|
| Week | 7-day window | `feb 15 - feb 21, 2026` |
| Month | single month, or cross-month range | `feb 2026` / `feb 2 – mar 1, 2026` |
| Year | single year, or cross-year range | `2026` / `2025 – 2026` |
| Total | full dataset bounds | `sep 2022 – sep 2026` |

#### c. Selection — a point/crosshair is active (`selectionLabel`)
[`selectionLabel()`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L2540) reads `graph.selectedXValue` (crosshair date) or `graph.selectedPoint` and formats **just the date**, no prefix:

```swift
func formatSelectedDate(_ date: Date, for period: TimePeriod) -> String {
    switch period {
    case .week, .month: return "MMM d, yyyy"   // Feb 15, 2026
    case .year, .total: return "MMM yyyy"        // Feb 2026
    }
}
```

So tapping a point swaps `feb 15 - feb 21, 2026` (range) → `feb 15, 2026` (the picked day).

---

## 2. What the value (number) represents

The big weight number next to the label is `displayWeight`, computed by
[`computeDisplayWeight()`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L744):

1. **Point selected** → that point's exact weight.
2. **Crosshair on an empty day** → interpolated weight at that date.
3. **No selection** → **average of the visible operations** for the period
   (interpolated if the window has no real entries, except `.total`).

The accompanying word for the *no-selection* case comes from
[`weightDisplayLabel`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L921) →
`goalManager.getWeightDisplayLabel(for:)` = `"<period> average"`:

| Period | No-selection label |
|--------|--------------------|
| Week | `week average` |
| Month | `month average` |
| Year | `year average` |
| Total | `total average` |

When a point **is** selected, `weightDisplayLabel` overrides this with `selectionPrefix(...)` (see §3).

> Which operations feed the average is period-dependent — see
> [`getOperationsForLabelDateRange()`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L2729).
> Week/Month/Year average over their **visible window**; **Total** averages **all** operations.

---

## 3. The hybrid rule: "latest entry" vs "day average"

When a point is selected, the **prefix** is decided by
[`selectionPrefix(for:)`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L3065):

```swift
private func selectionPrefix(for period: TimePeriod) -> String {
    switch period {
    case .week, .month:
        return isLatestDaySelected ? "latest entry" : "day average"
    case .year, .total:
        return "month average"
    }
}
```

- **Week / Month**
  - Selected day **is the most recent day with data** → `latest entry`
  - Any **other** day → `day average` (a day can hold several weigh-ins, so it's an average)
- **Year / Total** → always `month average` (each plotted point genuinely is a monthly average)

`isLatestDaySelected`
([DashboardStore.swift:3076](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L3076)) is true only on
Week/Month and only when the selected date is the same calendar day as
`continuousOperations.last?.date` (the newest entry).

---

## 4. The Metric Info sheet label

`ScaleMetricsView` shows a date row via
[`metricInfoDateLabel(for:)`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L2983), which mirrors the
header but adds a **prefix** and one special case for history entries.

[`formatMetricInfoDateLabel`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L2996) order:

```swift
// 1. History entry → its own date, sentence-cased, distinct styling
if isFromHistory { return measurementTakenLabel(for: entryDate) }   // "Measurement taken February 1, 2025"

// 2. Point selected     → prefix + date, lowercased
if let p = graph.selectedPoint   { return metricInfoSelectionLabel(date: p.date, period: period) }
// 3. Crosshair selected → prefix + date, lowercased
if let d = graph.selectedXValue  { return metricInfoSelectionLabel(date: d,      period: period) }

// 4. No selection → "<period> average <visible range>" (reuses weightLabel for the range)
return composeMetricInfoLabel(prefix: "\(period.rawValue) average", dateText: weightLabel)
```

Selection prefixes reuse the same `selectionPrefix(for:)` as the header, so the
latest-entry/day-average split stays in sync between the two surfaces.

### Metric Info examples

| Situation | Label |
|-----------|-------|
| History entry (opened from history) | `Measurement taken February 1, 2025` |
| Week/Month, **latest** day selected | `latest entry feb 15, 2026` |
| Week/Month, other day selected | `day average feb 14, 2026` |
| Year/Total, point selected | `month average feb 2026` |
| No selection (week) | `week average feb 15 - feb 21, 2026` |
| No selection (month) | `month average feb 2026` |

Date formats inside the sheet
([`formatMetricInfoSingleDate`](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift#L3090)):
Week/Month → `MMM d, yyyy`; Year/Total → `MMM yyyy`. Everything except the history
case is routed through `composeMetricInfoLabel`, which trims and lowercases.

---

## 5. Per-period summary

| | Week | Month | Year | Total |
|---|------|-------|------|-------|
| **Default header** | `feb 15 - feb 21, 2026` | `feb 2026` / range | `2026` / range | `sep 2022 – sep 2026` |
| **Selected-date format** | `feb 15, 2026` | `feb 15, 2026` | `feb 2026` | `feb 2026` |
| **No-selection prefix** | `week average` | `month average` | `year average` | `total average` |
| **Selection prefix** | `latest entry` / `day average` | `latest entry` / `day average` | `month average` | `month average` |
| **Value when selected** | point / interpolated | point / interpolated | monthly avg | monthly avg |
| **Value, no selection** | avg of visible 7 days | avg of visible month | avg of visible 12 months | avg of **all** data |
| **Snap target on tap** | nearest day | nearest Sunday tick | nearest month tick | nearest data point |

---

## Key files

- [GraphView.swift](../iOS/meApp/Features/Dashboard/Views/Components/GraphView.swift) — renders the header label, fades it for the callout.
- [DashboardStore.swift](../iOS/meApp/Features/Dashboard/Stores/DashboardStore.swift) — `weightLabel`, `selectionLabel`, `computeDisplayWeight`, `weightDisplayLabel`, `selectionPrefix`, `isLatestDaySelected`, `metricInfoDateLabel`.
- `DashboardGraphManager.swift` — `formatSelectedDate`, interpolation, scroll snapping.
- Section VMs (`WeekSectionViewModel`, `MonthSectionViewModel`, `YearSectionViewModel`, `TotalSectionViewModel`) — own `showCrosshair` and the per-period snap rules.
- [dashboard-hybrid-latest-vs-average.md](dashboard-hybrid-latest-vs-average.md) — deep dive on the hybrid rule.
