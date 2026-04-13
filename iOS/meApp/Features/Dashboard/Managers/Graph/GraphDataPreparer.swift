// swiftlint:disable file_length
import CoreGraphics
import Foundation

/// Pure data transformation layer for graph rendering.
///
/// All functions are stateless — inputs in, results out.
/// No `@MainActor`, no side effects, fully unit-testable.
struct GraphDataPreparer { // swiftlint:disable:this type_body_length

    // MARK: - Chart Series Entry Point

    /// Builds weight + optional metric series. Pass `yAxisDomain` for scroll-consistent normalization.
    func buildChartSeries( // swiftlint:disable:this function_parameter_count
        from operations: [BathScaleWeightSummary],
        selectedMetric: String?,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double,
        yAxisDomain: ClosedRange<Double>?,
        visibleOperations: [BathScaleWeightSummary] = [],
        operationsForYAxis: [BathScaleWeightSummary] = [],
        period: TimePeriod
    ) -> [GraphSeries] {
        guard !operations.isEmpty else { return [] }

        var series = buildWeightSeries(
            from: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight
        )

        guard let metric = selectedMetric, metric != DashboardStrings.weight else {
            return series
        }

        let metricSeries: [GraphSeries]
        if let domain = yAxisDomain {
            let opsForAxis = operationsForYAxis.isEmpty ? operations : operationsForYAxis
            metricSeries = buildNormalizedMetricSeriesWithDomain(
                for: metric,
                from: operations,
                visibleOperations: visibleOperations,
                operationsForYAxis: opsForAxis,
                toWeightDomain: domain,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertWeight: convertWeight
            )
        } else {
            let range = weightRange(from: series) ?? (0...1)
            metricSeries = buildNormalizedMetricSeries(for: metric, from: operations, toWeightRange: range)
        }

        series.append(contentsOf: metricSeries)
        return series
    }

    // MARK: - Weight Series

    func buildWeightSeries(
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: (Int) -> Double
    ) -> [GraphSeries] {
        operations.compactMap { summary in
            let displayWeight: Double
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return nil }
                displayWeight = convertWeight(Int(summary.weight)) - anchor
            } else {
                displayWeight = convertWeight(Int(summary.weight))
            }
            return GraphSeries(date: summary.date, value: displayWeight, series: DashboardStrings.weight)
        }
    }

    // MARK: - Baby Weight Series

    /// Convenience entry point for baby weight charts. Delegates to `buildWeightSeries`
    /// with weightless mode disabled (baby charts never use weightless anchoring).
    func buildBabyWeightSeries(
        from operations: [BathScaleWeightSummary],
        convertWeight: (Int) -> Double
    ) -> [GraphSeries] {
        buildWeightSeries(
            from: operations,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: convertWeight
        )
    }

    // MARK: - BPM Series

    /// Builds three named chart series (systolic, diastolic, pulse) from BP summary data.
    /// Mirrors the weight graph's period granularity:
    /// - week/month use day averages
    /// - year/total use month averages
    func buildBpmChartSeries(
        from operations: [BathScaleWeightSummary],
        period: TimePeriod
    ) -> [GraphSeries] {
        let aggregatedOperations = aggregatedBpmOperationsForPeriod(from: operations, period: period)
        var series: [GraphSeries] = []
        for op in aggregatedOperations {
            if let sys = op.systolic {
                series.append(GraphSeries(date: op.date, value: sys, series: "systolic"))
            }
            if let dia = op.diastolic {
                series.append(GraphSeries(date: op.date, value: dia, series: "diastolic"))
            }
            if let pulse = op.pulse {
                series.append(GraphSeries(date: op.date, value: pulse, series: "pulse"))
            }
        }
        return series
    }

    // MARK: - Normalized Metric Series

    /// Normalizes metric values into the visible weight range for co-plotting.
    func buildNormalizedMetricSeries(
        for metric: String,
        from operations: [BathScaleWeightSummary],
        toWeightRange weightRange: ClosedRange<Double>
    ) -> [GraphSeries] {
        let metricValues = operations.compactMap { metricValue(for: metric, from: $0) }
        guard !metricValues.isEmpty,
              let metricMin = metricValues.min(),
              let metricMax = metricValues.max() else { return [] }

        let (effectiveMin, effectiveMax) = effectiveMetricRange(min: metricMin, max: metricMax, metric: metric)

        return operations.compactMap { summary in
            guard let value = metricValue(for: metric, from: summary) else { return nil }
            let normalized = normalizeValue(value, from: effectiveMin...effectiveMax, to: weightRange)
            return GraphSeries(date: summary.date, value: normalized ?? weightRange.mid, series: metric)
        }
    }

    /// Normalizes metric series using an explicit Y-axis domain for scroll consistency.
    /// Using the same ops set as the Y-axis domain ensures metric lines stay in-bounds.
    func buildNormalizedMetricSeriesWithDomain( // swiftlint:disable:this function_parameter_count
        for metric: String,
        from allOperations: [BathScaleWeightSummary],
        visibleOperations: [BathScaleWeightSummary],
        operationsForYAxis: [BathScaleWeightSummary],
        toWeightDomain domain: ClosedRange<Double>,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: (Int) -> Double
    ) -> [GraphSeries] {
        let allMetricValues = allOperations.compactMap { metricValue(for: metric, from: $0) }
        guard !allMetricValues.isEmpty else { return [] }

        // Use same ops set as Y-axis for consistent normalization
        let rangeValues = operationsForYAxis.compactMap { metricValue(for: metric, from: $0) }
        let metricValues = rangeValues.isEmpty ? allMetricValues : rangeValues

        guard let metricMin = metricValues.min(), let metricMax = metricValues.max() else { return [] }

        let (effectiveMin, effectiveMax) = effectiveMetricRange(min: metricMin, max: metricMax, metric: metric)
        let isSinglePoint = (metricMax - metricMin) < 0.01

        return allOperations.compactMap { summary in
            guard let value = metricValue(for: metric, from: summary) else { return nil }

            let plotValue: Double
            if isSinglePoint {
                // Place single points at 60% height for optimal visibility (not touching edges)
                let span = domain.upperBound - domain.lowerBound
                plotValue = domain.lowerBound + span * 0.6
            } else {
                // Clamp to safe bounds (1.5% inset) to account for Y-axis edge buffers
                let epsilon = (domain.upperBound - domain.lowerBound) * 0.015
                let safeDomain = (domain.lowerBound + epsilon)...(domain.upperBound - epsilon)
                plotValue = normalizeValue(value, from: effectiveMin...effectiveMax, to: safeDomain) ?? domain.mid
            }

            return GraphSeries(date: summary.date, value: plotValue, series: metric)
        }
    }

    // MARK: - Hermite (Fritsch–Carlson) Interpolation

    /// Interpolates display weight at `date` using monotone cubic (Fritsch–Carlson) Hermite spline.
    /// Returns `nil` when operations are empty or result is non-finite.
    func interpolatedDisplayWeight( // swiftlint:disable:this function_parameter_count
        at date: Date,
        from operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double,
        period: TimePeriod
    ) -> Double? {
        guard !operations.isEmpty else { return nil }

        let sorted = operations.sorted { $0.date < $1.date }
        let xs = sorted.map { normalizedPlotDate($0.date, for: period).timeIntervalSinceReferenceDate }
        let rawYs = sorted.map { op -> Double? in
            let weight = convertWeight(Int(op.weight))
            if isWeightlessMode {
                guard let anchor = anchorWeight else { return nil }
                return weight - anchor
            }
            return weight
        }
        guard rawYs.allSatisfy({ $0 != nil }) else { return nil }
        let ys = rawYs.compactMap { $0 }

        let count = xs.count
        if count == 1 { return ys[0] }

        let targetTime = normalizedPlotDate(date, for: period).timeIntervalSinceReferenceDate

        // Clamp to edge values outside data bounds
        if targetTime <= xs[0] { return ys[0] }
        if targetTime >= xs[count - 1] { return ys[count - 1] }

        let tangents = fritschCarlsonTangents(xs: xs, ys: ys)
        let i = segmentIndex(for: targetTime, in: xs)
        let step = xs[i + 1] - xs[i]
        guard step > 0 else { return ys[i] }

        let result = hermiteEval(xVal: targetTime, x0: xs[i], x1: xs[i + 1], y0: ys[i], y1: ys[i + 1], m0: tangents[i], m1: tangents[i + 1])
        guard result.isFinite else { return nil }

        return (result * 100).rounded(.toNearestOrAwayFromZero) / 100
    }

    // MARK: - Point Finding

    func findClosestPoint(to date: Date, in operations: [BathScaleWeightSummary]) -> BathScaleWeightSummary? {
        operations.min { abs($0.date.timeIntervalSince(date)) < abs($1.date.timeIntervalSince(date)) }
    }

    // MARK: - Metric Value Extraction

    // swiftlint:disable:next cyclomatic_complexity
    func metricValue(for label: String, from summary: BathScaleWeightSummary) -> Double? {
        switch label {
        case DashboardStrings.bmi:          return summary.bmi
        case DashboardStrings.bodyFat:      return summary.bodyFat
        case DashboardStrings.muscle:       return summary.muscleMass
        case DashboardStrings.water:        return summary.water
        case DashboardStrings.heartBpm:     return summary.pulse
        case DashboardStrings.bone:         return summary.boneMass
        case DashboardStrings.visceralFat:  return summary.visceralFatLevel.map { $0 / 10.0 }
        case DashboardStrings.subFat:       return summary.subcutaneousFatPercent
        case DashboardStrings.protein:      return summary.proteinPercent
        case DashboardStrings.skelMuscle:   return summary.skeletalMusclePercent
        case DashboardStrings.bmrKcal:      return summary.bmr.map { $0 / 10.0 }
        case DashboardStrings.metAge:       return summary.metabolicAge
        default:                            return nil
        }
    }

    func staticMetricRange(for label: String) -> (min: Double, max: Double) {
        switch label {
        case DashboardStrings.bmi:
            return (DashboardConstants.MetricRanges.bmi.lowerBound, DashboardConstants.MetricRanges.bmi.upperBound)
        case DashboardStrings.heartBpm:
            return (DashboardConstants.MetricRanges.heartRate.lowerBound, DashboardConstants.MetricRanges.heartRate.upperBound)
        case DashboardStrings.visceralFat:
            return (DashboardConstants.MetricRanges.visceralFat.lowerBound, DashboardConstants.MetricRanges.visceralFat.upperBound)
        case DashboardStrings.bmrKcal:
            return (DashboardConstants.MetricRanges.bmr.lowerBound, DashboardConstants.MetricRanges.bmr.upperBound)
        case DashboardStrings.metAge:
            return (DashboardConstants.MetricRanges.metabolicAge.lowerBound, DashboardConstants.MetricRanges.metabolicAge.upperBound)
        default:
            // Percentage-based metrics (bodyFat, muscle, water, bone, subFat, protein, skelMuscle)
            return (DashboardConstants.MetricRanges.percentage.lowerBound, DashboardConstants.MetricRanges.percentage.upperBound)
        }
    }

    // MARK: - Metric Availability

    func canDisplay(_ metric: String, in operations: [BathScaleWeightSummary]) -> Bool {
        let values = operations.compactMap { metricValue(for: metric, from: $0) }
        guard values.count >= 2 else { return false }
        return (values.max() ?? 0) - (values.min() ?? 0) > 0.001
    }

    func availableMetrics(in operations: [BathScaleWeightSummary]) -> [String] {
        [DashboardStrings.bmi, DashboardStrings.bodyFat, DashboardStrings.muscle,
         DashboardStrings.water, DashboardStrings.heartBpm, DashboardStrings.bone,
         DashboardStrings.visceralFat, DashboardStrings.subFat, DashboardStrings.protein,
         DashboardStrings.skelMuscle, DashboardStrings.bmrKcal, DashboardStrings.metAge]
        .filter { canDisplay($0, in: operations) }
    }

    // MARK: - Weight Calculations

    func weightlessDisplay(
        for operations: [BathScaleWeightSummary],
        anchorWeight: Double?,
        period: TimePeriod,
        convertWeight: (Int) -> Double
    ) -> Double? {
        guard let anchor = anchorWeight else { return nil }
        let raw: Double
        switch period {
        case .week, .month:
            guard let last = operations.last else { return nil }
            let latest = convertWeight(Int(last.weight))
            raw = latest - anchor
        case .year, .total:
            let weights = operations.map { convertWeight(Int($0.weight)) }
            guard !weights.isEmpty else { return nil }
            raw = weights.reduce(0, +) / Double(weights.count) - anchor
        }
        return (raw * 100).rounded(.toNearestOrAwayFromZero) / 100
    }

    func averageWeight(
        for operations: [BathScaleWeightSummary],
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: (Int) -> Double
    ) -> Double {
        guard !operations.isEmpty else { return 0 }
        let values = operations.map { op -> Double in
            let weight = convertWeight(Int(op.weight))
            return isWeightlessMode ? weight - (anchorWeight ?? 0) : weight
        }
        let avg = values.reduce(0, +) / Double(values.count)
        return (avg * 100).rounded(.toNearestOrAwayFromZero) / 100
    }

    // swiftlint:disable:next function_parameter_count
    func interpolatedAverageForVisibleRange(
        from allOperations: [BathScaleWeightSummary],
        period: TimePeriod,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Int) -> Double,
        labelRange: DateInterval?,
        sampleDates: [Date]
    ) -> Double? {
        guard period != .total, !allOperations.isEmpty, !sampleDates.isEmpty else { return nil }
        let sorted = allOperations.sorted { $0.date < $1.date }
        guard let first = sorted.first?.date, let last = sorted.last?.date else { return nil }

        let buffer: TimeInterval = 3600
        var validSamples = sampleDates.filter {
            $0 >= first.addingTimeInterval(-buffer) && $0 <= last.addingTimeInterval(buffer)
        }
        if let range = labelRange {
            validSamples = validSamples.filter { $0 >= range.start && $0 <= range.end }
        }
        guard !validSamples.isEmpty else { return nil }

        let weights = validSamples.compactMap {
            interpolatedDisplayWeight(
                at: $0,
                from: allOperations,
                isWeightlessMode: isWeightlessMode,
                anchorWeight: anchorWeight,
                convertWeight: convertWeight,
                period: period
            )
        }
        guard !weights.isEmpty else { return nil }
        let avg = weights.reduce(0, +) / Double(weights.count)
        return (avg * 100).rounded(.toNearestOrAwayFromZero) / 100
    }

    // MARK: - Windowed / Visible Operations (stateless, no cache)

    /// Returns a performance-windowed subset (only applied to datasets > 2000 points).
    func windowedOperations(
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date,
        period: TimePeriod,
        visibleDomainLength: TimeInterval
    ) -> [BathScaleWeightSummary] {
        guard operations.count >= 2000 else { return operations }
        let buffer = visibleDomainLength * 5.0
        let start = scrollPosition.addingTimeInterval(-buffer)
        let end = scrollPosition.addingTimeInterval(visibleDomainLength + buffer)
        guard let si = binarySearchFirst(in: operations, where: { $0.date >= start }),
              let ei = binarySearchLast(in: operations, where: { $0.date <= end }) else { return operations }
        let safe = max(0, si - 5)...min(operations.count - 1, ei + 5)
        let windowed = Array(operations[safe])
        let savingsRatio = Double(operations.count - windowed.count) / Double(operations.count)
        return savingsRatio >= 0.3 ? windowed : operations
    }

    /// Returns operations visible in the current scroll window + 1-day/1-hour buffer for edge accuracy.
    func visibleOperations(
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> [BathScaleWeightSummary] {
        let left = scrollPosition.addingTimeInterval(-86400)         // 1-day left buffer
        let right = scrollPosition.addingTimeInterval(visibleDomainLength + 3600) // 1-hour right buffer
        guard let si = binarySearchFirst(in: operations, where: { $0.date >= left }),
              let ei = binarySearchLast(in: operations, where: { $0.date <= right }),
              si <= ei else { return [] }
        return Array(operations[si...ei])
    }

    /// Returns operations strictly within the visible domain (no buffer).
    func strictlyVisibleOperations(
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> [BathScaleWeightSummary] {
        guard let minDate = operations.first?.date,
              let maxDate = operations.last?.date else { return [] }
        let start = max(scrollPosition, minDate)
        let end = min(scrollPosition.addingTimeInterval(visibleDomainLength), maxDate)
        guard start <= end,
              let si = binarySearchFirst(in: operations, where: { $0.date >= start }),
              let ei = binarySearchLast(in: operations, where: { $0.date <= end }),
              si <= ei else { return [] }
        return Array(operations[si...ei])
    }

    /// Returns the operations immediately bracketing the visible window (one before, one after).
    /// Used to compute a meaningful Y-axis when no entries fall inside the window.
    func bracketingOperations(
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }
        let right = scrollPosition.addingTimeInterval(visibleDomainLength)

        let prev = binarySearchLast(in: operations) { $0.date <= scrollPosition }
                    .map { operations[$0] }
                    ?? binarySearchLast(in: operations) { $0.date < right }.map { operations[$0] }

        let next = binarySearchFirst(in: operations) { $0.date >= right }
                    .map { operations[$0] }
                    ?? binarySearchFirst(in: operations) { $0.date > scrollPosition }.map { operations[$0] }

        var result: [BathScaleWeightSummary] = []
        if let prevOp = prev { result.append(prevOp) }
        if let nextOp = next, result.last?.date != nextOp.date { result.append(nextOp) }
        return result
    }

    // MARK: - Binary Search Helpers (O(log n))

    func binarySearchFirst(
        in operations: [BathScaleWeightSummary],
        where predicate: (BathScaleWeightSummary) -> Bool
    ) -> Int? {
        guard !operations.isEmpty else { return nil }
        var low = 0, high = operations.count
        while low < high {
            let mid = (low + high) / 2
            predicate(operations[mid]) ? (high = mid) : (low = mid + 1)
        }
        return low < operations.count ? low : nil
    }

    func binarySearchLast(
        in operations: [BathScaleWeightSummary],
        where predicate: (BathScaleWeightSummary) -> Bool
    ) -> Int? {
        guard !operations.isEmpty else { return nil }
        var low = 0, high = operations.count
        while low < high {
            let mid = (low + high) / 2
            predicate(operations[mid]) ? (low = mid + 1) : (high = mid)
        }
        return low > 0 ? low - 1 : nil
    }

    // MARK: - Private Helpers

    private func weightRange(from series: [GraphSeries]) -> ClosedRange<Double>? {
        let values = series.filter { $0.series == DashboardStrings.weight }.map(\.value)
        guard let min = values.min(), let max = values.max(), max > min else { return nil }
        return min...max
    }

    private func effectiveMetricRange(min: Double, max: Double, metric: String) -> (Double, Double) {
        let range = max - min
        if range < 0.01 {
            // Use static fallback ranges for near-flat data
            let (staticMin, staticMax) = staticMetricRange(for: metric)
            return (Swift.min(min, staticMin), Swift.max(max, staticMax))
        }
        // Add 5% padding on each side for visual breathing room
        let padding = range * 0.05
        return (min - padding, max + padding)
    }

    /// Maps `value` from `source` range into `target` range, guarded for finite result.
    private func normalizeValue(
        _ value: Double,
        from source: ClosedRange<Double>,
        to target: ClosedRange<Double>
    ) -> Double? {
        let sourceSpan = source.upperBound - source.lowerBound
        guard sourceSpan > 0 else { return target.mid }
        let clamped = Swift.max(source.lowerBound, Swift.min(source.upperBound, value))
        let ratio = (clamped - source.lowerBound) / sourceSpan
        let result = target.lowerBound + ratio * (target.upperBound - target.lowerBound)
        guard result.isFinite else { return nil }
        return result
    }

    /// Week/month views plot points at local noon; interpolation must use the same normalization.
    private func normalizedPlotDate(_ date: Date, for period: TimePeriod) -> Date {
        guard period == .week || period == .month else { return date }
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = Calendar.current.timeZone
        let dayStart = cal.startOfDay(for: date)
        return cal.date(byAdding: .hour, value: 12, to: dayStart) ?? date
    }

    func aggregatedBpmOperationsForPeriod(
        from operations: [BathScaleWeightSummary],
        period: TimePeriod
    ) -> [BathScaleWeightSummary] {
        guard !operations.isEmpty else { return [] }

        switch period {
        case .week, .month:
            return aggregateBpmOperations(operations, by: .day)
        case .year, .total:
            return aggregateBpmOperations(operations, by: .month)
        }
    }

    private enum BpmAggregationUnit {
        case day
        case month
    }

    private func aggregateBpmOperations(
        _ operations: [BathScaleWeightSummary],
        by unit: BpmAggregationUnit
    ) -> [BathScaleWeightSummary] {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = Calendar.current.timeZone

        let grouped = Dictionary(grouping: operations) { summary -> Date in
            switch unit {
            case .day:
                return calendar.startOfDay(for: summary.date)
            case .month:
                let components = calendar.dateComponents([.year, .month], from: summary.date)
                return calendar.date(from: components) ?? calendar.startOfDay(for: summary.date)
            }
        }

        return grouped.keys.sorted().compactMap { groupDate in
            guard let summaries = grouped[groupDate], !summaries.isEmpty else { return nil }

            func average(_ values: [Double?]) -> Double? {
                let validValues = values.compactMap { $0 }
                guard !validValues.isEmpty else { return nil }
                return validValues.reduce(0, +) / Double(validValues.count)
            }

            let latestTimestamp = summaries.map(\.entryTimestamp).max() ?? ""
            let periodKey: String
            switch unit {
            case .day:
                periodKey = calendar.formattedDate(groupDate, format: "yyyy-MM-dd")
            case .month:
                periodKey = calendar.formattedDate(groupDate, format: "yyyy-MM")
            }

            return BathScaleWeightSummary(
                accountId: summaries[0].accountId,
                period: periodKey,
                entryTimestamp: latestTimestamp,
                date: groupDate,
                count: summaries.reduce(0) { $0 + $1.count },
                weight: 0,
                pulse: average(summaries.map(\.pulse)),
                systolic: average(summaries.map(\.systolic)),
                diastolic: average(summaries.map(\.diastolic)),
                meanArterial: average(summaries.map(\.meanArterial)),
                entryType: EntryType.bpm.rawValue
            )
        }
    }

    // MARK: - Hermite Math

    private func segmentIndex(for target: Double, in xs: [Double]) -> Int {
        var low = 0, high = xs.count - 1
        while low <= high {
            let mid = (low + high) >> 1
            xs[mid] <= target ? (low = mid + 1) : (high = mid - 1)
        }
        return Swift.max(0, Swift.min(xs.count - 2, low - 1))
    }

    private func fritschCarlsonTangents(xs: [Double], ys: [Double]) -> [Double] {
        let count = xs.count
        let slopes: [Double] = (0..<(count - 1)).map { k -> Double in
            let step = xs[k + 1] - xs[k]
            return step == 0 ? 0 : (ys[k + 1] - ys[k]) / step
        }

        var tangents = Array(repeating: 0.0, count: count)
        if count == 2 { return [slopes[0], slopes[0]] }

        // Interior tangents
        for k in 1..<(count - 1) {
            let m0 = slopes[k - 1], m1 = slopes[k]
            if m0 == 0 || m1 == 0 || m0.sign != m1.sign { continue }
            let h0 = xs[k] - xs[k - 1], h1 = xs[k + 1] - xs[k]
            let w1 = 2 * h1 + h0, w2 = h1 + 2 * h0
            tangents[k] = (w1 + w2) / (w1 / m0 + w2 / m1)
        }

        // Endpoint tangents (one-sided Fritsch–Carlson)
        tangents[0]         = endpointTangent(h0: xs[1] - xs[0], h1: xs[2] - xs[1], m0: slopes[0], m1: slopes[1])
        tangents[count - 1] = endpointTangent(
            h0: xs[count - 1] - xs[count - 2],
            h1: xs[count - 2] - xs[count - 3],
            m0: slopes[count - 2],
            m1: slopes[count - 3]
        )
        return tangents
    }

    private func endpointTangent(h0: Double, h1: Double, m0: Double, m1: Double) -> Double {
        guard h0 + h1 != 0 else { return 0 }
        var tangent = ((2 * h0 + h1) * m0 - h0 * m1) / (h0 + h1)
        if tangent.sign != m0.sign { tangent = 0 } else if abs(tangent) > 3 * abs(m0) { tangent = 3 * m0 }
        return tangent
    }

    // swiftlint:disable:next function_parameter_count
    private func hermiteEval(xVal: Double, x0: Double, x1: Double, y0: Double, y1: Double, m0: Double, m1: Double) -> Double {
        let step = x1 - x0
        let normalized = (xVal - x0) / step
        let n2 = normalized * normalized, n3 = n2 * normalized
        return (2 * n3 - 3 * n2 + 1) * y0
             + (n3 - 2 * n2 + normalized) * step * m0
             + (-2 * n3 + 3 * n2) * y1
             + (n3 - n2) * step * m1
    }
}

// MARK: - ClosedRange Convenience

private extension ClosedRange where Bound == Double {
    var mid: Double { (lowerBound + upperBound) / 2 }
}

private extension Calendar {
    func formattedDate(_ date: Date, format: String) -> String {
        let formatter = DateFormatter()
        formatter.calendar = self
        formatter.timeZone = timeZone
        formatter.dateFormat = format
        return formatter.string(from: date)
    }
}
