import Foundation
@testable import meApp
import SwiftUI
import Testing

/// Deterministic unit tests for `BaseGraphViewCacheManager` — the pure signature/hash and
/// decision helpers behind the Dashboard graph cache. Hash-based assertions only check
/// relative invariants (same input => same signature, changed field => different signature),
/// which are stable within a single process run; exact hash values are never asserted.
@Suite(.serialized)
@MainActor
struct BaseGraphViewCacheManagerTests {

    private func date(_ interval: TimeInterval) -> Date {
        Date(timeIntervalSince1970: interval)
    }

    // MARK: - dataChangeSignature

    @Test("dataChangeSignature is stable for identical inputs and varies per field")
    func dataChangeSignatureRelative() {
        let base = BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        )
        let same = BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        )
        #expect(base == same)

        #expect(base != BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 2,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myWeight
        ))
        #expect(base != BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "bmi",
            productType: .scale,
            selectedProductItem: .myWeight
        ))
        #expect(base != BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .bpm,
            selectedProductItem: .myWeight
        ))
        #expect(base != BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: 1,
            selectedMetricLabel: "weight",
            productType: .scale,
            selectedProductItem: .myBloodPressure
        ))
    }

    // MARK: - settingsChangeSignature

    @Test("settingsChangeSignature is stable for identical inputs and varies per field")
    func settingsChangeSignatureRelative() {
        let base = BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: false
        )
        #expect(base == BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: false
        ))
        #expect(base != BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: "kg",
            isWeightlessModeEnabled: false
        ))
        #expect(base != BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: "lb",
            isWeightlessModeEnabled: true
        ))
    }

    // MARK: - yAxisCacheSignature

    @Test("yAxisCacheSignature is stable and varies with domain, ticks, and nil-ness")
    func yAxisCacheSignatureRelative() {
        let base = BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: 0...100,
            cachedTicks: [0, 50, 100]
        )
        #expect(base == BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: 0...100,
            cachedTicks: [0, 50, 100]
        ))
        #expect(base != BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: 0...200,
            cachedTicks: [0, 50, 100]
        ))
        #expect(base != BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: 0...100,
            cachedTicks: [0, 25, 50, 100]
        ))
        #expect(base != BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: nil,
            cachedTicks: nil
        ))
    }

    // MARK: - viewHash

    @Test("viewHash is stable for identical inputs and varies per field")
    func viewHashRelative() {
        let selected = date(1_000_000)
        let base = BaseGraphViewCacheManager.viewHash(
            yAxisTicks: [0, 50, 100],
            yAxisDomain: 0...100,
            timePeriod: .week,
            goalWeight: 80,
            showCrosshair: false,
            selectedDate: selected,
            selectedMetricLabel: "weight"
        )
        #expect(base == BaseGraphViewCacheManager.viewHash(
            yAxisTicks: [0, 50, 100],
            yAxisDomain: 0...100,
            timePeriod: .week,
            goalWeight: 80,
            showCrosshair: false,
            selectedDate: selected,
            selectedMetricLabel: "weight"
        ))
        #expect(base != BaseGraphViewCacheManager.viewHash(
            yAxisTicks: [0, 50, 100],
            yAxisDomain: 0...100,
            timePeriod: .month,
            goalWeight: 80,
            showCrosshair: false,
            selectedDate: selected,
            selectedMetricLabel: "weight"
        ))
        #expect(base != BaseGraphViewCacheManager.viewHash(
            yAxisTicks: [0, 50, 100],
            yAxisDomain: 0...100,
            timePeriod: .week,
            goalWeight: 80,
            showCrosshair: true,
            selectedDate: selected,
            selectedMetricLabel: "weight"
        ))
        #expect(base != BaseGraphViewCacheManager.viewHash(
            yAxisTicks: [0, 50, 100],
            yAxisDomain: 0...100,
            timePeriod: .week,
            goalWeight: nil,
            showCrosshair: false,
            selectedDate: selected,
            selectedMetricLabel: "weight"
        ))
    }

    // MARK: - coordinatedChartAnimation

    @Test("coordinatedChartAnimation suppresses animation while scrolling or on domain-only change")
    func coordinatedAnimationSuppressed() {
        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: true,
            isInScrollEndTransition: false,
            isDomainChangeOnly: false,
            enableYAxisAnimation: true,
            shouldAnimateChartData: true
        ) == nil)

        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: false,
            isInScrollEndTransition: true,
            isDomainChangeOnly: false,
            enableYAxisAnimation: true,
            shouldAnimateChartData: true
        ) == nil)

        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: false,
            isInScrollEndTransition: false,
            isDomainChangeOnly: true,
            enableYAxisAnimation: true,
            shouldAnimateChartData: true
        ) == nil)
    }

    @Test("coordinatedChartAnimation returns the faster data animation when both flags are set")
    func coordinatedAnimationData() {
        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: false,
            isInScrollEndTransition: false,
            isDomainChangeOnly: false,
            enableYAxisAnimation: true,
            shouldAnimateChartData: true
        ) == .easeInOut(duration: 0.25))
    }

    @Test("coordinatedChartAnimation returns the y-axis animation when only that flag is set")
    func coordinatedAnimationYAxisOnly() {
        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: false,
            isInScrollEndTransition: false,
            isDomainChangeOnly: false,
            enableYAxisAnimation: true,
            shouldAnimateChartData: false
        ) == .easeInOut(duration: 0.3))
    }

    @Test("coordinatedChartAnimation returns nil when y-axis animation is disabled")
    func coordinatedAnimationDisabled() {
        #expect(BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: false,
            isInScrollEndTransition: false,
            isDomainChangeOnly: false,
            enableYAxisAnimation: false,
            shouldAnimateChartData: true
        ) == nil)
    }

    // MARK: - isDomainOnlyChange

    @Test("isDomainOnlyChange is false when there is no previous domain")
    func domainOnlyChangeNoPrevious() {
        #expect(BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: nil,
            newDomain: 0...100,
            lastDataHash: 5,
            previousDataHash: 5
        ) == false)
    }

    @Test("isDomainOnlyChange is false when the domain is unchanged")
    func domainOnlyChangeSameDomain() {
        #expect(BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: 0...100,
            newDomain: 0...100,
            lastDataHash: 5,
            previousDataHash: 5
        ) == false)
    }

    @Test("isDomainOnlyChange is true when only the domain changed and data hashes match")
    func domainOnlyChangeTrue() {
        #expect(BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: 0...100,
            newDomain: 0...200,
            lastDataHash: 5,
            previousDataHash: 5
        ) == true)
    }

    @Test("isDomainOnlyChange is false when the underlying data also changed")
    func domainOnlyChangeDataChanged() {
        #expect(BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: 0...100,
            newDomain: 0...200,
            lastDataHash: 6,
            previousDataHash: 5
        ) == false)
    }

    @Test("isDomainOnlyChange treats a nil previous data hash as zero")
    func domainOnlyChangeNilHashDefaultsToZero() {
        #expect(BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: 0...100,
            newDomain: 0...200,
            lastDataHash: 0,
            previousDataHash: nil
        ) == true)
    }

    // MARK: - throttleAction

    @Test("throttleAction updates now when enough time has elapsed")
    func throttleActionUpdatesNow() {
        let last = date(1_000_000)
        let now = date(1_000_010) // 10s later, interval 2s
        let action = BaseGraphViewCacheManager.throttleAction(
            now: now,
            lastCacheUpdateTime: last,
            throttleInterval: 2
        )
        if case let .updateNow(updatedAt) = action {
            #expect(updatedAt == now)
        } else {
            Issue.record("Expected .updateNow, got \(action)")
        }
    }

    @Test("throttleAction schedules when the throttle interval has not elapsed")
    func throttleActionSchedules() {
        let last = date(1_000_000)
        let now = date(1_000_001) // 1s later, interval 2s
        let action = BaseGraphViewCacheManager.throttleAction(
            now: now,
            lastCacheUpdateTime: last,
            throttleInterval: 2
        )
        if case let .schedule(delay) = action {
            #expect(delay == 2)
        } else {
            Issue.record("Expected .schedule, got \(action)")
        }
    }

    // MARK: - seriesAnimationToken

    @Test("seriesAnimationToken returns zero while scrolling and the data hash otherwise")
    func seriesAnimationToken() {
        #expect(BaseGraphViewCacheManager.seriesAnimationToken(isScrolling: true, lastDataHash: 99) == 0)
        #expect(BaseGraphViewCacheManager.seriesAnimationToken(isScrolling: false, lastDataHash: 99) == 99)
    }
}
