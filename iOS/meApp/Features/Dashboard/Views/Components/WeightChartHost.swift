//
//  WeightChartHost.swift
//  meApp
//
//  MOB-518 — v2 weight-graph engine (greenfield strangler rebuild).
//
//  Period-aware host for the new `WeightChartView`. Owns the local `@State` scroll (Apple's canonical
//  `.chartScrollPosition($state)` pattern) and rebuilds the immutable `ChartModel` ONLY when a
//  rebuild-relevant input changes — data / period / unit / goal / scroll-SETTLE — never per scroll frame.
//  During a drag, Swift Charts scrolls natively over the prepared model and nothing recomputes; the
//  adaptive y-axis resettles once, 150 ms after the finger lifts (Y-B).
//
//  Wired behind a DEBUG A/B toggle in `GraphView` for weight (`EntryType.scale`) only. Baby/BPM keep the
//  legacy engine until weight is signed off. Selection/crosshair, header average, and the goal chip are
//  V4 — this host renders the read-only line/points/axis for evaluating render + scroll quality.
//

import SwiftUI

struct WeightChartHost: View {

    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    @State private var model: ChartModel?
    @State private var scrollX = Date()
    @State private var committedScroll = Date()
    @State private var settleTask: Task<Void, Never>?

    var body: some View {
        Group {
            if let model {
                WeightChartView(
                    model: model,
                    scrollX: $scrollX,
                    yLabel: { dashboardStore.displayManager.formatYAxisTickLabel($0) },
                    xLabel: formatXAxisLabel,
                    theme: theme
                )
            } else {
                Color.clear.frame(height: 265)
            }
        }
        .onAppear {
            let anchor = dashboardStore.state.graph.xScrollPosition
            scrollX = anchor
            committedScroll = anchor
            rebuild()
        }
        // External data / settings changes (new entry, unit, goal, weightless) → rebuild in place.
        .onChange(of: dataSettingsKey) { _, _ in rebuild() }
        // Period switch → reseed to the new period's committed anchor, rebuild.
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            let anchor = dashboardStore.state.graph.xScrollPosition
            scrollX = anchor
            committedScroll = anchor
            rebuild()
        }
        // Native scroll: debounce so the adaptive y-axis resettles ONCE after the drag stops — never
        // per frame. Charts handles the visual scroll itself; only the y-axis window needs recompute.
        .onChange(of: scrollX) { _, newValue in
            settleTask?.cancel()
            settleTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 150_000_000)
                guard !Task.isCancelled else { return }
                committedScroll = newValue
                rebuild()
            }
        }
    }

    private func rebuild() {
        model = dashboardStore.makeWeightChartModel(scrollPosition: committedScroll)
    }

    private func formatXAxisLabel(_ date: Date) -> String {
        GraphRenderingConfiguration().formatXAxisLabel(
            for: date,
            period: dashboardStore.state.graph.selectedPeriod,
            operations: dashboardStore.continuousOperations
        ) ?? ""
    }

    /// Changes only on real data/settings edits (NOT live scroll, NOT period — those are handled above),
    /// so `onChange` rebuilds the model when the underlying data or display settings actually change.
    private var dataSettingsKey: Int {
        var hasher = Hasher()
        let operations = dashboardStore.continuousOperations
        hasher.combine(operations.count)
        if let first = operations.first { hasher.combine(first.date); hasher.combine(first.weight) }
        if let last = operations.last { hasher.combine(last.date); hasher.combine(last.weight) }
        hasher.combine(dashboardStore.goalWeightForDisplay ?? -1)
        hasher.combine(dashboardStore.isWeightlessModeEnabled)
        return hasher.finalize()
    }
}
