import CoreGraphics
import Foundation

struct BaseGraphViewCacheUpdate {
    let dataHash: Int
    let chartPoints: [GraphSeries]
    let groupedPoints: [String: [GraphSeries]]
    let plottedPoints: [String: [PlottedGraphSeries]]
    let orderedSeriesNames: [String]
    let allPlottedPoints: [PlottedGraphSeries]
}

struct BaseGraphViewCacheSnapshot {
    let seriesData: [GraphSeries]
    let yAxisDomain: ClosedRange<Double>
    let yAxisTicks: [Double]
}

@MainActor
enum BaseGraphViewCacheSupport {

    static func makeCacheUpdate(
        snapshot: BaseGraphViewCacheSnapshot,
        previousHash: Int,
        isCacheEmpty: Bool,
        plotXDate: (Date) -> Date
    ) -> BaseGraphViewCacheUpdate? {
        let newData = snapshot.seriesData
        let newHash = cacheHash(for: snapshot, data: newData)

        guard newHash != previousHash || isCacheEmpty else { return nil }

        var groupedPoints: [String: [GraphSeries]] = [:]
        groupedPoints.reserveCapacity(newData.count)
        for point in newData {
            groupedPoints[point.series, default: []].append(point)
        }
        for key in groupedPoints.keys {
            groupedPoints[key]?.sort { $0.date < $1.date }
        }

        let orderedSeriesNames = groupedPoints.keys.sorted { lhs, rhs in
            seriesRenderPriority(lhs) < seriesRenderPriority(rhs)
        }

        var plottedPoints: [String: [PlottedGraphSeries]] = [:]
        plottedPoints.reserveCapacity(groupedPoints.count)
        var allPlottedPoints: [PlottedGraphSeries] = []
        allPlottedPoints.reserveCapacity(newData.count)

        for seriesName in orderedSeriesNames {
            guard let points = groupedPoints[seriesName] else { continue }
            let plottedSeries = points.map { point in
                PlottedGraphSeries(original: point, xDate: plotXDate(point.date))
            }
            plottedPoints[seriesName] = plottedSeries
            allPlottedPoints.append(contentsOf: plottedSeries)
        }

        return BaseGraphViewCacheUpdate(
            dataHash: newHash,
            chartPoints: newData,
            groupedPoints: groupedPoints,
            plottedPoints: plottedPoints,
            orderedSeriesNames: orderedSeriesNames,
            allPlottedPoints: allPlottedPoints
        )
    }

    static func pointsToRender(
        from points: [PlottedGraphSeries],
        visibleStart: Date,
        visibleEnd: Date
    ) -> [PlottedGraphSeries] {
        guard points.count > 200 else { return points }

        var visible: [PlottedGraphSeries] = []
        var leftBuffer: [PlottedGraphSeries] = []
        var rightBuffer: [PlottedGraphSeries] = []
        visible.reserveCapacity(points.count)
        leftBuffer.reserveCapacity(points.count / 2)
        rightBuffer.reserveCapacity(points.count / 2)

        for point in points {
            let date = point.xDate
            if date >= visibleStart && date <= visibleEnd {
                visible.append(point)
            } else if date < visibleStart {
                leftBuffer.append(point)
            } else {
                rightBuffer.append(point)
            }
        }

        let sampledLeft = sampledBufferPoints(from: leftBuffer, keepFirstVisibleNeighbor: false)
        let sampledRight = sampledBufferPoints(from: rightBuffer, keepFirstVisibleNeighbor: true)

        var result: [PlottedGraphSeries] = []
        result.reserveCapacity(sampledLeft.count + visible.count + sampledRight.count)
        result.append(contentsOf: sampledLeft)
        result.append(contentsOf: visible)
        result.append(contentsOf: sampledRight)
        return result
    }

    static func precomputedYAxisLabels(
        ticks: [Double],
        goalWeight: Double?,
        existingLabels: [Double: String],
        formatter: (Double) -> String
    ) -> [Double: String] {
        var labels = existingLabels

        for tick in ticks where labels[tick] == nil {
            labels[tick] = formatter(tick)
        }

        if let goalWeight, labels[goalWeight] == nil {
            labels[goalWeight] = formatter(goalWeight)
        }

        return labels
    }

    static func precomputedXAxisLabels(
        dates: [Date],
        existingLabels: [Date: String],
        formatter: (Date) -> String?
    ) -> [Date: String] {
        var labels = existingLabels

        for date in dates where labels[date] == nil {
            labels[date] = formatter(date)
        }

        return labels
    }

    static func roundedFrame(_ frame: CGRect) -> CGRect {
        frame.integral
    }

    static func roundedHeight(_ height: CGFloat) -> CGFloat {
        round(height)
    }

    static func boundaryYAxisTicks(from yAxisTicks: [Double]) -> [Double] {
        guard let first = yAxisTicks.first else { return [] }
        guard let last = yAxisTicks.last,
              abs(last - first) > AppConstants.Precision.doubleEqualityEpsilon else {
            return [first]
        }

        return [first, last]
    }

    static func adjustedBoundaryTick(
        _ tick: Double,
        hasXAxis: Bool,
        yAxisDomain: ClosedRange<Double>,
        chartHeight: CGFloat,
        isBabySelection: Bool
    ) -> Double {
        guard hasXAxis else { return tick }

        let lower = yAxisDomain.lowerBound
        let upper = yAxisDomain.upperBound
        let epsilon: Double = 1e-6
        let domainRange = upper - lower
        let xAxisHeight: CGFloat = 18
        let availableHeight = max(1, chartHeight - xAxisHeight)
        let onePointValue = domainRange / Double(availableHeight)
        let boundaryRuleOffset = onePointValue * 2

        if abs(tick - lower) <= epsilon {
            if isBabySelection {
                return min(upper, tick + boundaryRuleOffset)
            }

            return lower < 0 ? (tick + onePointValue) : tick
        }

        if abs(tick - upper) <= epsilon {
            return max(lower, tick - boundaryRuleOffset)
        }

        return tick
    }

    private static func cacheHash(
        for snapshot: BaseGraphViewCacheSnapshot,
        data: [GraphSeries]
    ) -> Int {
        var hasher = Hasher()
        hasher.combine(snapshot.yAxisDomain.lowerBound.bitPattern)
        hasher.combine(snapshot.yAxisDomain.upperBound.bitPattern)
        hasher.combine(snapshot.yAxisTicks.count)

        if let firstTick = snapshot.yAxisTicks.first {
            hasher.combine(firstTick.bitPattern)
        }

        if let lastTick = snapshot.yAxisTicks.last {
            hasher.combine(lastTick.bitPattern)
        }

        hasher.combine(data.count)

        if !data.isEmpty {
            let indices = data.count <= 5
                ? Array(0..<data.count)
                : [0, data.count / 4, data.count / 2, (3 * data.count) / 4, data.count - 1]

            for index in indices {
                let point = data[index]
                hasher.combine(point.date.timeIntervalSince1970.bitPattern)
                hasher.combine(point.value.bitPattern)
                hasher.combine(point.series)
            }
        }

        return hasher.finalize()
    }

    private static func sampledBufferPoints(
        from buffer: [PlottedGraphSeries],
        keepFirstVisibleNeighbor: Bool,
        maxBufferPoints: Int = 30
    ) -> [PlottedGraphSeries] {
        guard buffer.count > maxBufferPoints else { return buffer }

        let step = buffer.count / maxBufferPoints
        var sampled: [PlottedGraphSeries] = []
        sampled.reserveCapacity(maxBufferPoints + 1)

        if keepFirstVisibleNeighbor, let first = buffer.first {
            sampled.append(first)
        }

        let startIndex = keepFirstVisibleNeighbor ? step : 0
        for index in stride(from: startIndex, to: buffer.count, by: step) {
            sampled.append(buffer[index])
        }

        if !keepFirstVisibleNeighbor,
           let last = buffer.last,
           sampled.last?.original.date != last.original.date {
            sampled.append(last)
        }

        return sampled
    }

    private static func seriesRenderPriority(_ name: String) -> Int {
        if BabyDashboardChartSupport.isPercentileSeries(name) { return 0 }
        if name == DashboardStrings.weight || BabyDashboardChartSupport.isHeightSeries(name) { return 1 }
        return 2
    }
}
