//
//  DashboardChartLayout.swift
//  meApp
//
//  MOB-1591: single source of truth for the dashboard chart *container* heights.
//  Shared by the live chart (TrendChartHost / TrendChartView) and the loading skeleton
//  (GraphView.skeletonHeight) so both render at the same height for a given product.
//  The taller baby height applies ONLY to a POPULATED baby graph — it exists to fit the
//  percentile band + dual axis. An empty baby graph (no reading for the selected metric,
//  or no baby profile yet) has neither, so it uses the standard weight/BPM height; only a
//  baby with real readings gets the taller adaptive height (see TrendChartHost.chartHeight
//  / GraphView.skeletonHeight, both gated on `hasBabyReadings` / `chartModel.hasReadings`).
//
//  (This is the container height only; the `265` defaults in YAxisCalculator / ChartPrep
//  are a separate y-axis pixel-mapping concern and are intentionally NOT sourced here.)
//

import SwiftUI

enum DashboardChartLayout {
    /// Weight / BPM chart container height.
    static let standardHeight: CGFloat = 265

    /// Baby growth chart container height — taller to fit the percentile band + dual axis.
    /// This is the DESIGN height (≈ Figma node 26501-377606's ~491 pt graph) and the MAX the baby graph
    /// ever uses; see `babyHeight(forAvailableHeight:)` for the adaptive shrink.
    static let babyHeight: CGFloat = 498

    /// MOB-1591: adaptive baby graph height. `babyHeight` (the Figma design height) is the ceiling; on
    /// screens where the full baby stack — product nav header + value/toggle header + graph + the
    /// WEEK/MONTH/YEAR/TOTAL segment control — wouldn't fit the viewport, the graph shrinks so the segment
    /// control stays visible WITHOUT scrolling (the empty-state "connect device" footer is supplementary and
    /// may still scroll). Floored so the graph never collapses on small phones. `availableHeight` is the
    /// dashboard viewport height (DashboardScreen's `GeometryReader` proxy); `nil`/non-positive → the full
    /// design height (no viewport known yet, e.g. previews).
    static func babyHeight(forAvailableHeight availableHeight: CGFloat?) -> CGFloat {
        guard let availableHeight, availableHeight > 0 else { return babyHeight }
        // Everything in the viewport OTHER than the graph: the product nav header, the value/toggle header,
        // the segment control, the bottom tab bar (the viewport extends under it), and inter-element padding.
        // Tuned on device (MOB-1591 follow-ups): 300 left too big a gap under the segments; 250 pushed the
        // segment control partly UNDER the tab bar. 285 keeps the segment control fully visible with only a
        // small gap. On tall phones (viewport ≥ babyHeight + reserve) the full design height is kept unchanged.
        // If the on-device clearance/gap still needs tuning, this is the single dial (higher = shorter graph).
        let reservedChrome: CGFloat = 285
        let minGraph: CGFloat = 320
        return max(minGraph, min(babyHeight, availableHeight - reservedChrome))
    }
}

// MARK: - Dashboard viewport height (MOB-1591)

/// The dashboard's available viewport height, published from `DashboardScreen`'s top-level `GeometryReader`
/// so descendants (the baby graph host + its loading skeleton) can size the baby chart adaptively without
/// threading a height parameter through every intervening view. `nil` until a viewport is measured.
private struct DashboardViewportHeightKey: EnvironmentKey {
    static let defaultValue: CGFloat? = nil
}

extension EnvironmentValues {
    var dashboardViewportHeight: CGFloat? {
        get { self[DashboardViewportHeightKey.self] }
        set { self[DashboardViewportHeightKey.self] = newValue }
    }
}
