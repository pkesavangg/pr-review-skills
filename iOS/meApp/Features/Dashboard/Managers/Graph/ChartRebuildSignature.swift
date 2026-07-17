//
//  ChartRebuildSignature.swift
//  meApp
//
//  MOB-1516 — v2 chart engine. Cheap change tokens that tell `TrendChartHost` when to rebuild its
//  `ChartModel`. Relocated verbatim from the (now-deleted) legacy `BaseGraphViewCacheManager` so the v2
//  engine no longer depends on any legacy type. `dataChangeRevision` bumps on every real data mutation, so
//  the data signature can't go stale like the old view-side endpoint hash could.
//

import Foundation

enum ChartRebuildSignature {

    /// Data-shape token: data revision + co-plot metric + product + selected product item.
    static func dataChangeSignature(
        dataRevision: Int,
        selectedMetricLabel: String?,
        productType: EntryType,
        selectedProductItem: ProductSelection
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(dataRevision)
        hasher.combine(selectedMetricLabel)
        hasher.combine(productType)
        hasher.combine(selectedProductItem)
        return hasher.finalize()
    }

    /// Settings token: display unit + weightless mode.
    static func settingsChangeSignature(
        currentUnitRawValue: String,
        isWeightlessModeEnabled: Bool
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(currentUnitRawValue)
        hasher.combine(isWeightlessModeEnabled)
        return hasher.finalize()
    }
}
