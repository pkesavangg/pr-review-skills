import Foundation
@testable import meApp
import SwiftUI
import Testing

/// MOB-1516: unit tests for `ChartRebuildSignature` — the pure change tokens that tell `TrendChartHost` when
/// to rebuild its `ChartModel`. Relocated from the deleted `BaseGraphViewCacheManagerTests` (only the two
/// signature helpers survived the legacy-engine delete; the rest exercised now-removed cache/animation
/// helpers). Hash assertions check relative invariants only (same input => same signature, changed field =>
/// different signature), which are stable within a single process run; exact hash values are never asserted.
@Suite(.serialized)
@MainActor
struct ChartRebuildSignatureTests {

    // MARK: - dataChangeSignature

    @Test("dataChangeSignature is stable for identical inputs and varies per field")
    func dataChangeSignatureRelative() {
        let base = ChartRebuildSignature.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        )
        let same = ChartRebuildSignature.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        )
        #expect(base == same)

        #expect(base != ChartRebuildSignature.dataChangeSignature(
            dataRevision: 2,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        ))
        #expect(base != ChartRebuildSignature.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "bmi",
            productType: .scale,
            selectedProductItem: .myWeight
        ))
        #expect(base != ChartRebuildSignature.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .bpm,
            selectedProductItem: .myWeight
        ))
        #expect(base != ChartRebuildSignature.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myBloodPressure
        ))
    }

    // MARK: - settingsChangeSignature

    @Test("settingsChangeSignature is stable for identical inputs and varies per field")
    func settingsChangeSignatureRelative() {
        let base = ChartRebuildSignature.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: false
        )
        #expect(base == ChartRebuildSignature.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: false
        ))
        #expect(base != ChartRebuildSignature.settingsChangeSignature(
            currentUnitRawValue: "kg",
            isWeightlessModeEnabled: false
        ))
        #expect(base != ChartRebuildSignature.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: true
        ))
    }
}
