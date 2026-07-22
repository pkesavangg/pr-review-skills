//
//  TrendChartSelectionSnapper.swift
//  meApp
//
//  MOB-1515 — pure, testable selection-snap logic extracted from `TrendChartHost` (the MOB-518/1516 v2
//  chart engine). The host owns gestures/rendering (UI, coverage-excluded); the per-period snap math is a
//  side-effect-free value transform over the `ChartModel`, so it lives here where it can be unit-tested
//  without instantiating a View.
//
//  A tap's raw x-date is snapped to the period's selection grid so the crosshair/callout always lands on a
//  drawn gridline or a real entry — and never past the first/last reading:
//    • week  → nearest day (midnight)
//    • month → nearest shown line (Sunday / month-1st) or real entry day
//    • year  → nearest 1st-of-month
//    • total → nearest real entry (no continuous grid)
//

import Foundation

enum TrendChartSelectionSnapper {

    /// Period-aware gridline snapping over `model.fullResolution[primarySeriesName]`. Returns `nil` when the
    /// primary series has no points. Behaviour is identical to the pre-MOB-1515 `TrendChartHost` private path;
    /// a tap on an empty day/month is an in-between selection whose value the caller Hermite-interpolates.
    static func snappedDate(
        for raw: Date,
        in model: ChartModel,
        primarySeriesName: String,
        calendar: Calendar = .current
    ) -> Date? {
        let points = model.fullResolution[primarySeriesName] ?? []
        guard let firstDate = points.first?.original.date,
              let lastDate = points.last?.original.date else { return nil }
        switch model.period {
        case .week:
            // Round to the nearest midnight (day gridline), then clamp into the data's day range.
            let nearestDay = calendar.startOfDay(for: raw.addingTimeInterval(43_200))
            let lo = calendar.startOfDay(for: firstDate)
            let hi = calendar.startOfDay(for: lastDate)
            return min(max(nearestDay, lo), hi)
        case .month:
            let lo = calendar.startOfDay(for: firstDate)
            let hi = calendar.startOfDay(for: lastDate)
            // Candidates = the shown vertical lines (every Sunday + each month's 1st) ∪ real entry days,
            // restricted to the data range. Snap to whichever is nearest the tap.
            var candidates = monthLineCandidates(in: model, calendar: calendar)
            candidates.append(contentsOf: points.map { calendar.startOfDay(for: $0.original.date) })
            let inRange = candidates.filter { $0 >= lo && $0 <= hi }
            if let nearest = inRange.min(by: { abs($0.timeIntervalSince(raw)) < abs($1.timeIntervalSince(raw)) }) {
                return nearest
            }
            // Fallback (no ticks generated yet): nearest day, clamped.
            return min(max(calendar.startOfDay(for: raw.addingTimeInterval(43_200)), lo), hi)
        case .year:
            // Round to the nearest 1st-of-month, then clamp into the data's month range.
            let nearestMonth = nearestMonthStart(to: raw, calendar: calendar)
            let lo = monthStart(of: firstDate, calendar: calendar)
            let hi = monthStart(of: lastDate, calendar: calendar)
            return min(max(nearestMonth, lo), hi)
        case .total:
            return nearestEntry(to: raw, in: model, primarySeriesName: primarySeriesName)?.original.date
        }
    }

    /// Nearest real entry (by plotted `xDate`) in the primary series, or `nil` if empty. This is the `.total`
    /// snap target and backs the "a crosshair always lands on a real reading" guarantee.
    static func nearestEntry(
        to date: Date,
        in model: ChartModel,
        primarySeriesName: String
    ) -> PlottedGraphSeries? {
        let points = model.fullResolution[primarySeriesName] ?? []
        return points.min {
            abs($0.xDate.timeIntervalSince(date)) < abs($1.xDate.timeIntervalSince(date))
        }
    }

    // MARK: - Month / year grid helpers

    /// The vertical gridlines the MONTH view draws — every Sunday (the windowed weekly ticks) plus each
    /// month's 1st (the solid dividers) — snapped to midnight. These are the "shown lines" the user selects
    /// in month view (in addition to real entry days). Mirrors `TrendChartView`'s weekly grid + month-boundary
    /// dividers so a selection lands exactly on a drawn rule.
    static func monthLineCandidates(in model: ChartModel, calendar: Calendar) -> [Date] {
        guard let lo = model.xAxisTicks.first, let hi = model.xAxisTicks.last else { return [] }
        // Weekly Sunday ticks (drop the phantom trailing tick), at midnight — the light rules.
        var candidates = model.xAxisTicks.dropLast().map { calendar.startOfDay(for: $0) }
        // Each month's 1st within the tick span — the solid divider rules.
        guard var monthStart = calendar.dateInterval(of: .month, for: lo)?.start else { return candidates }
        while monthStart <= hi {
            candidates.append(monthStart)
            guard let next = calendar.date(byAdding: .month, value: 1, to: monthStart) else { break }
            monthStart = next
        }
        return candidates
    }

    /// Nearest 1st-of-month (midnight) to `raw` — the year view's selection grid.
    static func nearestMonthStart(to raw: Date, calendar: Calendar) -> Date {
        let thisStart = monthStart(of: raw, calendar: calendar)
        let nextStart = calendar.date(byAdding: .month, value: 1, to: thisStart) ?? thisStart
        return abs(raw.timeIntervalSince(thisStart)) <= abs(nextStart.timeIntervalSince(raw)) ? thisStart : nextStart
    }

    static func monthStart(of date: Date, calendar: Calendar) -> Date {
        calendar.date(from: calendar.dateComponents([.year, .month], from: date)) ?? calendar.startOfDay(for: date)
    }
}
