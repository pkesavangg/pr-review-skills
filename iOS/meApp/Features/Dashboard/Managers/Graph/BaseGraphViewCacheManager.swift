import Foundation
import SwiftUI

enum BaseGraphViewCacheThrottleAction {
    case updateNow(updatedAt: Date)
    case schedule(delay: TimeInterval)
}

struct BaseGraphViewCacheState {
    let chartPoints: [GraphSeries]
    let groupedPoints: [String: [GraphSeries]]
    let plottedPoints: [String: [PlottedGraphSeries]]
    let orderedSeriesNames: [String]
    let allPlottedPoints: [PlottedGraphSeries]
    let previousDataHash: Int?
    let lastDataHash: Int
}

struct BaseGraphViewLabelCache {
    let yAxisLabels: [Double: String]
    let xAxisLabels: [Date: String]
}

@MainActor
enum BaseGraphViewCacheManager {

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

    static func settingsChangeSignature(
        currentUnitRawValue: String,
        isWeightlessModeEnabled: Bool
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(currentUnitRawValue)
        hasher.combine(isWeightlessModeEnabled)
        return hasher.finalize()
    }

    static func yAxisCacheSignature(
        cachedDomain: ClosedRange<Double>?,
        cachedTicks: [Double]?
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(cachedDomain?.lowerBound.bitPattern ?? 0)
        hasher.combine(cachedDomain?.upperBound.bitPattern ?? 0)

        let ticks = cachedTicks ?? []
        hasher.combine(ticks.count)
        if let firstTick = ticks.first {
            hasher.combine(firstTick.bitPattern)
        }
        if let lastTick = ticks.last {
            hasher.combine(lastTick.bitPattern)
        }

        return hasher.finalize()
    }

    // swiftlint:disable:next function_parameter_count
    static func viewHash(
        yAxisTicks: [Double],
        yAxisDomain: ClosedRange<Double>,
        timePeriod: TimePeriod,
        goalWeight: Double?,
        showCrosshair: Bool,
        selectedDate: Date?,
        selectedMetricLabel: String?
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(yAxisTicks)
        hasher.combine(yAxisDomain.lowerBound)
        hasher.combine(yAxisDomain.upperBound)
        hasher.combine(timePeriod.rawValue)
        hasher.combine(goalWeight)
        hasher.combine(showCrosshair)
        hasher.combine(selectedDate?.timeIntervalSince1970 ?? 0)
        hasher.combine(selectedMetricLabel)
        return hasher.finalize()
    }

    static func coordinatedChartAnimation(
        isScrolling: Bool,
        isInScrollEndTransition: Bool,
        isDomainChangeOnly: Bool,
        enableYAxisAnimation: Bool,
        shouldAnimateChartData: Bool
    ) -> Animation? {
        if isScrolling || isInScrollEndTransition || isDomainChangeOnly {
            return nil
        }

        if enableYAxisAnimation && shouldAnimateChartData {
            return .easeInOut(duration: 0.25)
        }

        if enableYAxisAnimation {
            return .easeInOut(duration: 0.3)
        }

        return nil
    }

    static func isDomainOnlyChange(
        previousYAxisDomain: ClosedRange<Double>?,
        newDomain: ClosedRange<Double>,
        lastDataHash: Int,
        previousDataHash: Int?
    ) -> Bool {
        previousYAxisDomain != nil &&
            previousYAxisDomain != newDomain &&
            lastDataHash == (previousDataHash ?? 0)
    }

    static func throttleAction(
        now: Date,
        lastCacheUpdateTime: Date,
        throttleInterval: TimeInterval
    ) -> BaseGraphViewCacheThrottleAction {
        guard now.timeIntervalSince(lastCacheUpdateTime) > throttleInterval else {
            return .schedule(delay: throttleInterval)
        }

        return .updateNow(updatedAt: now)
    }

    static func seriesAnimationToken(
        isScrolling: Bool,
        lastDataHash: Int
    ) -> Int {
        isScrolling ? 0 : lastDataHash
    }

    static func cacheState(
        snapshot: BaseGraphViewCacheSnapshot,
        previousHash: Int,
        isCacheEmpty: Bool,
        plotXDate: (Date) -> Date
    ) -> BaseGraphViewCacheState? {
        guard let cacheUpdate = BaseGraphViewCacheSupport.makeCacheUpdate(
            snapshot: snapshot,
            previousHash: previousHash,
            isCacheEmpty: isCacheEmpty,
            plotXDate: plotXDate
        ) else {
            return nil
        }

        return BaseGraphViewCacheState(
            chartPoints: cacheUpdate.chartPoints,
            groupedPoints: cacheUpdate.groupedPoints,
            plottedPoints: cacheUpdate.plottedPoints,
            orderedSeriesNames: cacheUpdate.orderedSeriesNames,
            allPlottedPoints: cacheUpdate.allPlottedPoints,
            previousDataHash: previousHash,
            lastDataHash: cacheUpdate.dataHash
        )
    }

    static func invalidatedCacheState() -> BaseGraphViewCacheState {
        BaseGraphViewCacheState(
            chartPoints: [],
            groupedPoints: [:],
            plottedPoints: [:],
            orderedSeriesNames: [],
            allPlottedPoints: [],
            previousDataHash: nil,
            lastDataHash: 0
        )
    }

    // swiftlint:disable:next function_parameter_count
    static func precomputeLabels(
        yAxisTicks: [Double],
        goalWeight: Double?,
        existingYAxisLabels: [Double: String],
        yAxisFormatter: (Double) -> String,
        isScrollable: Bool,
        xAxisValues: [Date],
        existingXAxisLabels: [Date: String],
        xAxisFormatter: (Date) -> String?
    ) -> BaseGraphViewLabelCache {
        let yAxisLabels = BaseGraphViewCacheSupport.precomputedYAxisLabels(
            ticks: yAxisTicks,
            goalWeight: goalWeight,
            existingLabels: existingYAxisLabels,
            formatter: yAxisFormatter
        )

        let xAxisLabels: [Date: String]
        if isScrollable {
            xAxisLabels = BaseGraphViewCacheSupport.precomputedXAxisLabels(
                dates: xAxisValues,
                existingLabels: existingXAxisLabels,
                formatter: xAxisFormatter
            )
        } else {
            xAxisLabels = existingXAxisLabels
        }

        return BaseGraphViewLabelCache(
            yAxisLabels: yAxisLabels,
            xAxisLabels: xAxisLabels
        )
    }

    static func yAxisLabel(
        for value: Double,
        cachedLabels: [Double: String],
        formatter: (Double) -> String
    ) -> String {
        cachedLabels[value] ?? formatter(value)
    }
}
