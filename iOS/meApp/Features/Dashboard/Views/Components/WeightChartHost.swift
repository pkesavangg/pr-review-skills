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
//  Wired behind a DEBUG A/B toggle in `GraphView` for weight (`EntryType.scale`) only. Baby/BPM keep the
//  legacy engine until weight is signed off. Selection/crosshair, header average, and the goal chip are
//  V4 — this host renders the read-only line/points/axis for evaluating render + scroll quality.
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
        Group {
            if let model = dashboardStore.chartModel {
                WeightChartView(
                    model: model,
                    scrollX: $scrollX,
                    selectedX: $selectedX,
                    crosshairDate: crosshairDate,
                    goalLabel: goalLabel,
                    yLabel: { dashboardStore.displayManager.formatYAxisTickLabel($0) },
                    xLabel: formatXAxisLabel,
                    theme: theme
                )
                // A2 — native scroll phase is the REAL start/commit/end signal (same path the legacy graph
                // uses via `ScrollDetectionModifier`), so there is no view-side timer to approximate it.
                // V-A4 — routed straight to `graphManager` (not `chartManager`): we want only the commit +
                // `isScrolling` flip + selection-clear, NOT the chartManager's `.idle` 50 ms legacy settle
                // (`updateYAxisCache`/`updateWeightDisplay`/metrics) — the new engine gets its y-axis from
                // `resettleWeightYAxis` and ignores that legacy work. `graphManager.handleScrollPhaseChange`
                // commits the landed window (with month snapping) into `state.graph.xScrollPosition`.
                .onScrollPhaseChange { _, newPhase in
                    Task { @MainActor in
                        await dashboardStore.graphManager.handleScrollPhaseChange(newPhase)
                    }
                }
            } else {
                Color.clear.frame(height: 265)
            }
        }
        .onAppear { adopt(dashboardStore.state.graph.xScrollPosition) }
        // V-A5b — start-at-latest safety net: if the host mounted before `initializeChart` set the latest
        // window, `onAppear` adopted the default `Date()`. When the chart becomes ready (init done →
        // `xScrollPosition` is the snapped latest window) adopt it, so cold-open / tab-back / post-reset
        // land on the latest window. One-shot per ready transition; the user hasn't scrolled at that point.
        .onChange(of: dashboardStore.state.graph.isGraphReady) { wasReady, isReady in
            guard !wasReady, isReady else { return }
            adopt(dashboardStore.state.graph.xScrollPosition)
        }
        // A2 — data / metric / unit / goal / weightless changes → rebuild from the store's canonical change
        // signals (not a view-side endpoint hash that can go stale). See `rebuildSignal`.
        .onChange(of: rebuildSignal) { _, _ in rebuild(at: dashboardStore.state.graph.xScrollPosition) }
        // Period switch → adopt the new period's committed anchor.
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            adopt(dashboardStore.state.graph.xScrollPosition)
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
        // Phase 4 — on the real scroll-end (isScrolling→false) resettle ONLY the adaptive y-axis in place.
        // A full rebuild here re-emitted scroll-dependent x-geometry (per-month `visibleDomainLength`,
        // windowed `xAxisTicks`), which made Swift Charts rebuild its scroll view → the "can't scroll for
        // ~1 s after it stops" hitch. Resettling only `yAxis` keeps the scroll region stable (one animation).
        .onChange(of: dashboardStore.state.graph.isScrolling) { _, isScrolling in
            // Scroll START clears any selection (the store also clears its own on `.interacting`); drop the
            // local raw value so the next tap re-triggers `onChange(selectedX)`.
            guard !isScrolling else { selectedX = nil; return }
            let committed = dashboardStore.state.graph.xScrollPosition
            dashboardStore.resettleWeightYAxis(scrollPosition: committed)
            // V-A5b — the store committed the snapped window (month → the 1st) into `xScrollPosition`, but
            // the native scroll rested wherever the finger lifted. Reflect the snap in the visual scroll so
            // the window aligns to the month, exactly as the legacy graph writes the committed position back
            // to its scroll binding. Guarded by `isAdopting` so this write doesn't re-enter the buffer path.
            if abs(committed.timeIntervalSince(scrollX)) > 0.5 {
                isAdopting = true
                scrollX = committed
            }
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

    /// Snap the raw selected x to the nearest real (undecimated) entry and report it to the store; a `nil`
    /// selection (or a tap while scrolling / on an empty chart) clears it.
    private func handleSelectionChange(_ raw: Date?) {
        guard !dashboardStore.state.graph.isScrolling else { return }
        guard let raw,
              let model = dashboardStore.chartModel,
              let nearest = nearestEntry(to: raw, in: model) else {
            dashboardStore.selectWeightPoint(at: nil)
            return
        }
        dashboardStore.selectWeightPoint(at: nearest.original.date)
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
