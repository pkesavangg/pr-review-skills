import Foundation

/// Manages all scroll buffering and visible-operations caching for the graph.
///
/// Responsibilities:
/// - Buffers scroll position during active scrolling (updates state only at end).
/// - Caches visible/x-axis operations to avoid recalculation on every frame.
/// - Finds the closest data point to a user tap.
///
/// Timer-based async tasks (scroll-end debouncing) remain in `DashboardGraphManager`
/// because `inout GraphState` cannot be captured across async Timer callbacks.
@MainActor
final class GraphInteractionHandler {

    // MARK: - Scroll Position Buffering

    /// The scroll position captured during active scrolling.
    /// Committed to `GraphState.xScrollPosition` only when scrolling ends.
    private(set) var latestScrollPosition: Date?

    func captureScrollPosition(_ position: Date) {
        latestScrollPosition = position
    }

    /// Consumes the buffered scroll position, returning it and clearing the buffer.
    func consumeBufferedScrollPosition() -> Date? {
        defer { latestScrollPosition = nil }
        return latestScrollPosition
    }

    // MARK: - Visible Operations Cache

    private var lastVisibleOps: [BathScaleWeightSummary] = []
    private var lastVisibleScrollPosition: Date?
    private var lastVisiblePeriod: TimePeriod?

    /// Returns cached visible operations if the scroll position and period haven't changed.
    /// Re-calculates via `dataPreparer` on a cache miss.
    func visibleOperations(
        from allOperations: [BathScaleWeightSummary],
        scrollPosition: Date,
        period: TimePeriod,
        visibleDomainLength: TimeInterval,
        dataPreparer: GraphDataPreparer
    ) -> [BathScaleWeightSummary] {
        if !lastVisibleOps.isEmpty,
           let cachedPosition = lastVisibleScrollPosition,
           let cachedPeriod = lastVisiblePeriod,
           cachedPeriod == period {
            let leftEdge = scrollPosition
            let rightEdge = scrollPosition.addingTimeInterval(visibleDomainLength)
            let lastEntryDate = allOperations.last?.date

            if let lastEntryDate, leftEdge > lastEntryDate {
                invalidateVisibleOpsCache()
            } else {
                let adjustedLeftEdge = leftEdge.addingTimeInterval(-86400)
                let adjustedRightEdge = rightEdge.addingTimeInterval(3600)
                let positionChange = abs(scrollPosition.timeIntervalSince(cachedPosition))
                let cacheThreshold = visibleDomainLength / 10

                if positionChange < cacheThreshold,
                   let cachedMin = lastVisibleOps.first?.date,
                   let cachedMax = lastVisibleOps.last?.date,
                   cachedMin >= adjustedLeftEdge,
                   cachedMax <= adjustedRightEdge {
                    return lastVisibleOps
                }
            }
        }

        let ops = dataPreparer.visibleOperations(
            from: allOperations,
            scrollPosition: scrollPosition,
            visibleDomainLength: visibleDomainLength
        )
        lastVisibleOps = ops
        lastVisibleScrollPosition = scrollPosition
        lastVisiblePeriod = period
        return ops
    }

    func invalidateVisibleOpsCache() {
        lastVisibleOps = []
        lastVisibleScrollPosition = nil
        lastVisiblePeriod = nil
    }

    // MARK: - X-Axis Cache

    private(set) var lastXAxisValues: [Date] = []
    private var lastXAxisScrollPosition: Date?
    private var lastXAxisPeriod: TimePeriod?

    /// Returns cached x-axis tick dates or computes them via `renderConfig` on a cache miss.
    func xAxisValues(
        for period: TimePeriod,
        from operations: [BathScaleWeightSummary],
        scrollPosition: Date,
        renderConfig: GraphRenderingConfiguration
    ) -> [Date] {
        let minDate = operations.first?.date ?? scrollPosition
        let maxDate = operations.last?.date ?? scrollPosition

        if !lastXAxisValues.isEmpty,
           let cached = lastXAxisScrollPosition,
           let cachedPeriod = lastXAxisPeriod,
           cachedPeriod == period {
            let domainLength = renderConfig.visibleDomainLength(for: period, at: scrollPosition)
            let delta = abs(scrollPosition.timeIntervalSince(cached))
            let movedFar = delta > (domainLength / 6.0)
            let leftEdge = scrollPosition
            let rightEdge = scrollPosition.addingTimeInterval(domainLength)
            let nearLeft = leftEdge <= minDate.addingTimeInterval(domainLength / 4.0)
            let nearRight = rightEdge >= maxDate.addingTimeInterval(-domainLength / 4.0)

            if !(movedFar || nearLeft || nearRight) {
                return lastXAxisValues
            }
        }

        let values = renderConfig.xAxisValues(for: period, from: operations, scrollPosition: scrollPosition)
        lastXAxisValues = values
        lastXAxisScrollPosition = scrollPosition
        lastXAxisPeriod = period
        return values
    }

    func invalidateXAxisCache() {
        lastXAxisValues = []
        lastXAxisScrollPosition = nil
        lastXAxisPeriod = nil
    }

    // MARK: - Chart Selection

    /// Returns the data point closest to a tap date and its converted display weight.
    func resolveSelection(
        at selectedDate: Date,
        from operations: [BathScaleWeightSummary],
        dataPreparer: GraphDataPreparer,
        convertWeight: (Double) -> Double
    ) -> (summary: BathScaleWeightSummary, displayWeight: Double)? {
        guard let point = dataPreparer.findClosestPoint(to: selectedDate, in: operations) else { return nil }
        let displayWeight = convertWeight(point.weight)
        return (summary: point, displayWeight: displayWeight)
    }

    /// Resolves a selection including an interpolated weight for smooth crosshair positioning.
    func resolveSelectionWithInterpolation( // swiftlint:disable:this function_parameter_count
        at selectedDate: Date,
        from operations: [BathScaleWeightSummary],
        dataPreparer: GraphDataPreparer,
        period: TimePeriod,
        isWeightlessMode: Bool,
        anchorWeight: Double?,
        convertWeight: @escaping (Double) -> Double
    ) -> (summary: BathScaleWeightSummary, interpolatedWeight: Double?)? {
        guard let point = dataPreparer.findClosestPoint(to: selectedDate, in: operations) else { return nil }
        let interpolated = dataPreparer.interpolatedDisplayWeight(
            at: selectedDate,
            from: operations,
            isWeightlessMode: isWeightlessMode,
            anchorWeight: anchorWeight,
            convertWeight: convertWeight,
            period: period
        )
        return (summary: point, interpolatedWeight: interpolated)
    }

    // MARK: - Ensure Latest Entries Visible

    /// Returns a corrected scroll position if the latest entry would be off-screen.
    /// Returns `nil` when no correction is needed.
    func correctedScrollPosition(
        current scrollPosition: Date,
        from operations: [BathScaleWeightSummary],
        period: TimePeriod,
        visibleDomainLength: TimeInterval,
        renderConfig: GraphRenderingConfiguration
    ) -> Date? {
        guard let lastDate = operations.last?.date else { return nil }
        let viewportEnd = scrollPosition.addingTimeInterval(visibleDomainLength)
        guard lastDate > viewportEnd else { return nil }

        return renderConfig.optimalScrollPosition(
            for: period,
            from: operations,
            showingLatest: true
        )
    }
}
