//
//  WeightChartHost.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine (greenfield strangler rebuild).
//
//  Period-aware host for the new `WeightChartView`. Owns the local `@State` scroll (Apple's canonical
//  `.chartScrollPosition($state)` pattern) and observes the store's published `ChartModel` (A3). The model
//  is rebuilt ONLY when a rebuild-relevant input actually changes — data / period / unit / goal / weightless
//  (via the store's canonical change signals) or a real scroll-END (the native `.onScrollPhaseChange`
//  `.idle` event) — never per scroll frame. During a drag, Swift Charts scrolls natively over the prepared
//  model and nothing recomputes.
//
//  The weight (`EntryType.scale`) renderer (V6 — flipped on for weight). Baby/BPM keep the legacy
//  `BaseGraphView` engine (the two share it) until they migrate too. Selection/crosshair, header value,
//  goal chip, weightless, metric co-plot, and active-month greying are all wired (V4).
//

import SwiftUI

struct WeightChartHost: View {

    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    // Scroll offset is local @State (Apple's canonical `.chartScrollPosition` pattern). The store owns the
    // published model + the scroll lifecycle; this view reports gestures and observes the model.
    @State private var scrollX = Date()
    @State private var isAdopting = false   // suppress the buffer path when WE move scrollX (init/period)
    @State private var selectedX: Date?     // V4 (6a): raw x from `.chartXSelection`; snapped + reported below

    var body: some View {
        chartContent
        .onAppear { adopt(dashboardStore.state.graph.xScrollPosition) }
        // V-A5b — start-at-latest safety net: if the host mounted before `initializeChart` set the latest
        // window, `onAppear` adopted the default `Date()`. When the chart becomes ready (init done →
        // `xScrollPosition` is the snapped latest window) adopt it, so cold-open / tab-back / post-reset
        // land on the latest window. One-shot per ready transition; the user hasn't scrolled at that point.
        .onChange(of: dashboardStore.state.graph.isGraphReady) { wasReady, isReady in
            guard !wasReady, isReady else { return }
            adopt(dashboardStore.state.graph.xScrollPosition)
            selectLatestIfNeeded()
        }
        // A2 — data / metric / unit / goal / weightless changes → rebuild from the store's canonical change
        // signals (not a view-side endpoint hash that can go stale). See `rebuildSignal`.
        .onChange(of: rebuildSignal) { _, _ in rebuild(at: dashboardStore.state.graph.xScrollPosition) }
        // Period switch → adopt the new period's committed anchor, then ensure the latest point is selected.
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            adopt(dashboardStore.state.graph.xScrollPosition)
            selectLatestIfNeeded()
        }
        // A2/V-A4 — buffer live scroll positions (same owner as the phase handler above) so the store's
        // `.idle` handler commits the right landed window.
        .onChange(of: scrollX) { _, newValue in
            if isAdopting { isAdopting = false; return }   // WE moved scrollX (init/period), not the user
            dashboardStore.graphManager.handleScrollPositionChange(newValue)
        }
        // V4 (6a) — tap/drag selection: snap the raw x to the nearest real entry and report it to the store
        // (which resolves `selectedPoint`/`showCrosshair` per period). Ignored mid-scroll. The crosshair
        // itself is derived from the store's validated selection via `crosshairDate`.
        .onChange(of: selectedX) { _, raw in
            handleSelectionChange(raw)
        }
        // Scroll-END: commit + settle at the ONE landed value (`scrollX`, where the native value-aligned
        // scroll rested). `.chartScrollTargetBehavior` already decelerates onto the fine grid — a fling onto
        // the period boundary (Sunday / 1st / Jan 1), a slow drag onto any day / month — so `scrollX` is
        // exactly where the window should stay. `commitWeightScroll` records that position VERBATIM (no
        // re-snap, no animated reflect) and settles the y-axis + windowed ticks IN PLACE (no scroll-view
        // rebuild → no "~1 s can't scroll" hitch, #3). Not moving the window after release is what fixes the
        // one-unit drift on scroll-end / leave-and-return: the stored position == the visible position, so
        // the header / active-month greying / y-axis all match what's on screen, and re-adopting the stored
        // position on return never jumps the window by a day/month.
        .onChange(of: dashboardStore.state.graph.isScrolling) { _, isScrolling in
            // Scroll START clears any selection (the store also clears its own on `.interacting`); drop the
            // local raw value so the next tap re-triggers `onChange(selectedX)`.
            guard !isScrolling else { selectedX = nil; return }
            dashboardStore.commitWeightScroll(landedAt: scrollX)
        }
    }

    /// The chart (or an empty placeholder) — extracted from `body` to keep the modifier chain type-checkable.
    @ViewBuilder
    private var chartContent: some View {
        if let model = dashboardStore.chartModel {
            WeightChartView(
                model: model,
                scrollX: $scrollX,
                selectedX: $selectedX,
                crosshairDate: crosshairDate,
                selectionDateLabel: selectionDateLabel,
                goalLabel: goalLabel,
                activeMonthInterval: dashboardStore.displayManager.activeMonthInterval,
                isScrolling: dashboardStore.state.graph.isScrolling,
                yLabel: { dashboardStore.displayManager.formatYAxisTickLabel($0) },
                xLabel: formatXAxisLabel,
                theme: theme
            )
            // A2 — native scroll phase is the REAL start/commit/end signal (same path the legacy graph
            // uses via `ScrollDetectionModifier`), so there is no view-side timer to approximate it.
            // V-A4 — routed straight to `graphManager` (not `chartManager`): we want only the commit +
            // `isScrolling` flip + selection-clear, NOT the chartManager's `.idle` 50 ms legacy settle
            // (`updateYAxisCache`/`updateWeightDisplay`/metrics) — the new engine gets its y-axis from
            // `settleWeightChart` and ignores that legacy work. `graphManager.handleScrollPhaseChange`
            // commits the landed window (with month snapping) into `state.graph.xScrollPosition`.
            .onScrollPhaseChange { _, newPhase in
                Task { @MainActor in
                    await dashboardStore.graphManager.handleScrollPhaseChange(newPhase)
                }
            }
            // Issue #1 — remount the chart on a PERIOD switch (id keyed ONLY on period, never on scroll /
            // y-settle → safe from S1). A fresh instance lands directly at the new period's latest window with
            // no cross-period scroll/y animation — killing the "feels like it scrolls to the recent window" on
            // a section switch — the same per-period view identity the legacy engine had (distinct generic
            // types). Within a period the id is stable, so a y-settle still animates in place (no teardown).
            .id(model.period)
        } else {
            Color.clear.frame(height: 265)
        }
    }

    // MARK: - Model rebuild / scroll adoption

    /// Programmatically move the chart to `pos` (init / period switch) without tripping the buffer path,
    /// then rebuild the model for that window.
    private func adopt(_ pos: Date) {
        if abs(pos.timeIntervalSince(scrollX)) > 0.5 {
            isAdopting = true
            scrollX = pos
        }
        rebuild(at: pos)
    }

    private func rebuild(at position: Date) {
        dashboardStore.rebuildWeightChartModel(scrollPosition: position)
    }

    // MARK: - V4 (6a) selection / crosshair

    /// Plotted x-date of the store's currently-selected entry — drives the crosshair rule + point highlight.
    /// Derived from the store's validated selection so it reflects both taps and programmatic auto-select,
    /// and clears automatically when the store clears selection (e.g. on scroll-start). `nil` when unselected.
    private var crosshairDate: Date? {
        guard dashboardStore.state.graph.showCrosshair,
              let selectedDate = dashboardStore.state.graph.selectedXValue,
              let model = dashboardStore.chartModel else { return nil }
        let calendar = Calendar.current
        let points = model.fullResolution[DashboardStrings.weight] ?? []
        let match = points.first { point in
            switch model.period {
            case .week, .month: return calendar.isDate(point.original.date, inSameDayAs: selectedDate)
            case .year, .total: return calendar.isDate(point.original.date, equalTo: selectedDate, toGranularity: .month)
            }
        }
        return match?.xDate
    }

    /// Issue #2 — formatted date of the store's selected point, for the callout above the crosshair line.
    /// Mirrors the legacy `formatSelectedDate` (week/month → "MMM d, yyyy", year/total → "MMM yyyy"),
    /// lowercased. `nil` when there is no active selection (no crosshair), so the callout hides with it.
    private var selectionDateLabel: String? {
        guard dashboardStore.state.graph.showCrosshair,
              let date = dashboardStore.state.graph.selectedPoint?.date
                  ?? dashboardStore.state.graph.selectedXValue else { return nil }
        return GraphRenderingConfiguration()
            .formatSelectedDate(date, for: dashboardStore.state.graph.selectedPeriod)
            .lowercased()
    }

    /// Snap the raw selected x to the nearest real (undecimated) entry and report it to the store. A `nil`
    /// raw value (finger-lift / empty chart) is IGNORED so the selection persists (issue #3) — Swift Charts
    /// resets `.chartXSelection` to nil when the scrub gesture ends, but Apple Health keeps the last-tapped
    /// point highlighted until the next scroll. The only place a selection should drop is scroll-start, where
    /// the store clears its own on `.interacting` (and this view drops the raw value in the `isScrolling`
    /// handler so the next tap re-triggers `onChange(selectedX)`).
    private func handleSelectionChange(_ raw: Date?) {
        guard !dashboardStore.state.graph.isScrolling else { return }
        guard let raw,
              let model = dashboardStore.chartModel,
              let nearest = nearestEntry(to: raw, in: model) else {
            return
        }
        dashboardStore.selectWeightPoint(at: nearest.original.date)
    }

    /// Issue #2 — after a period switch / cold start, make sure the latest plotted point is selected so the
    /// header + crosshair land on the most recent reading. Derives the latest from the MODEL the chart plots
    /// (`fullResolution`), so it can't diverge from what's on screen — the Year/Total gap came from the
    /// period-switch auto-select (`DashboardChartManager.updateSelectedPeriod`) reading a *different*
    /// operations source (`dataManager`, which lacks the store's monthly-summary fallback) than the model.
    /// No-op once the current selection actually resolves to a crosshair (so it never overrides a working
    /// week/month selection or the user's own tap). Called only on period switch / graph-ready — NOT on
    /// scroll-end, where an empty selection is intended.
    private func selectLatestIfNeeded() {
        guard crosshairDate == nil, let model = dashboardStore.chartModel else { return }
        let points = model.fullResolution[DashboardStrings.weight] ?? []
        guard let latest = points.max(by: { $0.original.date < $1.original.date }) else { return }
        dashboardStore.selectWeightPoint(at: latest.original.date)
    }

    /// V4 (6c): formatted goal-weight chip label (nil → no chip), matching the legacy
    /// `formatWeightDisplayText(roundedGoalWeight(goal))`.
    private var goalLabel: String? {
        guard let goal = dashboardStore.goalWeightForDisplay else { return nil }
        let rounded = dashboardStore.displayManager.roundedGoalWeight(goal)
        return dashboardStore.displayManager.formatWeightDisplayText(rounded)
    }

    private func nearestEntry(to date: Date, in model: ChartModel) -> PlottedGraphSeries? {
        let points = model.fullResolution[DashboardStrings.weight] ?? []
        return points.min {
            abs($0.xDate.timeIntervalSince(date)) < abs($1.xDate.timeIntervalSince(date))
        }
    }

    private func formatXAxisLabel(_ date: Date) -> String {
        GraphRenderingConfiguration().formatXAxisLabel(
            for: date,
            period: dashboardStore.state.graph.selectedPeriod,
            operations: dashboardStore.continuousOperations
        ) ?? ""
    }

    /// A2 — the store's canonical data + settings change signals. Mirrors `BaseGraphView`'s
    /// `dataChangeSignature` + `settingsChangeSignature` (so the new engine can't miss a change the legacy
    /// graph catches), plus goal weight (baked into the model's y-axis + goal line). `dataChangeRevision`
    /// increments on every real data mutation, so unlike the old view-side endpoint hash this can't go stale.
    private var rebuildSignal: Int {
        var hasher = Hasher()
        hasher.combine(BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: dashboardStore.dataChangeRevision,
            selectedMetricLabel: dashboardStore.state.ui.selectedMetricLabel,
            productType: dashboardStore.productType,
            selectedProductItem: dashboardStore.selectedProductItem
        ))
        hasher.combine(BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: dashboardStore.currentUnit.rawValue,
            isWeightlessModeEnabled: dashboardStore.isWeightlessModeEnabled
        ))
        hasher.combine(dashboardStore.goalWeightForDisplay ?? -1)
        return hasher.finalize()
    }
}
