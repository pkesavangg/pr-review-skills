//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI
import Charts

struct GraphView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    // Local state variables for chart selection (like WeightGraph)
    @State private var selectedXValue: Date?

    // Scroll detection state
    @State private var hasDetectedScrollInCurrentGesture = false

    // Decision window state
    @State private var touchInteractionMode: TouchInteractionMode = .none
    @State private var initialTouchPoint: CGPoint = .zero
    @State private var decisionTimer: Timer?

    // Callout positioning
    @State private var chartFrame: CGRect = .zero

    // Check if there are any entries to display
    private var hasEntries: Bool {
        return !dashboardStore.continuousOperations.isEmpty
    }

    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        return DashboardStrings.noEntriesMessage
    }

    var body: some View {
        VStack(alignment: .leading){
            // Preserve layout height: fade the label out instead of removing it to avoid jump
            Text(dashboardStore.weightLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .opacity(dashboardStore.state.graph.selectedPoint == nil ? 1 : 0)
                    .padding(.leading, .spacingSM)
                    .padding(.vertical, .spacingXS)
            if hasEntries {
                chartView
            } else {
                emptyStateView
            }
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            // Clear crosshair and selection when time period changes
            dashboardStore.clearSelection()

        }

    }

    // MARK: - Chart View
    private var chartView: some View {
        return HStack(spacing: 0) {
            ZStack {
                Chart {
                    yAxisGridLines
                    chartSeries
                    crosshairContent
                }
                .chartXVisibleDomain(length: getVisibleDomainLength() ?? 0)
                .chartScrollableAxes(getScrollableAxes())
                .chartYScale(domain: dashboardStore.yAxisDomain)
                .chartScrollPosition(x: Binding(
                    get: { dashboardStore.state.graph.xScrollPosition },
                    set: { newPosition in
                        dashboardStore.handleScrollPositionChange(newPosition)
                    }
                ))
                .chartForegroundStyleScale([
                    DashboardStrings.weight: theme.actionPrimary,
                    DashboardStrings.bmi: theme.actionSecondary,
                    DashboardStrings.bodyFat: theme.actionSecondary,
                    DashboardStrings.muscle: theme.actionSecondary,
                    DashboardStrings.water: theme.actionSecondary,
                    DashboardStrings.heartBpm: theme.actionSecondary,
                    DashboardStrings.bone: theme.actionSecondary,
                    DashboardStrings.visceralFat: theme.actionSecondary,
                    DashboardStrings.subFat: theme.actionSecondary,
                    DashboardStrings.protein: theme.actionSecondary,
                    DashboardStrings.skelMuscle: theme.actionSecondary,
                    DashboardStrings.bmrKcal: theme.actionSecondary,
                    DashboardStrings.metAge: theme.actionSecondary
                ])
                .chartYAxis { yAxisMarks }
                .chartLegend(.hidden)
                .chartXAxis {
                    if dashboardStore.state.graph.selectedPeriod != .total {
                        AxisMarks(values: dashboardStore.xAxisValuesWithBuffer(for: dashboardStore.state.graph.selectedPeriod)) { value in
                            AxisGridLine()
                            AxisTick()
                            AxisValueLabel {
                                if let date = value.as(Date.self),
                                   let labelString = dashboardStore.xLabelString(for: date, period: dashboardStore.state.graph.selectedPeriod) {
                                    Text(labelString)
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            }
                        }
                    }
                }
                .chartXSelection(value: Binding(
                    get: {
                        // Use local state for selection (like WeightGraph)
                        selectedXValue
                    },
                    set: { newValue in
                        // Only handle selection if not in scroll mode and not actively scrolling
                        if touchInteractionMode != .scrolling && !dashboardStore.state.graph.isScrolling {
                            selectedXValue = newValue
                            if let selectedDate = newValue {
                                // Update the global store for metrics
                                Task {
                                    await dashboardStore.handleChartSelection(at: selectedDate)
                                }
                            }
                        }
                    }
                ))
                .frame(height: 265)
                .frame(maxWidth: .infinity, minHeight: 240)
                .padding(.leading, isAtLeftBoundary ? .spacingXS : 0)
                .padding(.trailing, .spacingXS)
                .background(
                    GeometryReader { geo in
                        theme.textInverse
                            .onAppear {
                                dashboardStore.state.graph.chartHeight = geo.size.height
                                chartFrame = geo.frame(in: .local)
                            }
                            .onChange(of: geo.frame(in: .local)) { _, newFrame in
                                chartFrame = newFrame
                            }
                    }
                )
                .onPreferenceChange(AnnotationHeightKey.self) { height in
                    dashboardStore.state.graph.annotationHeight = height
                }
                .onAppear {
                    dashboardStore.initializeChart()
                }
                // Synchronized animations for chart components
                .animation(dashboardStore.state.graph.isScrolling ? .none : .easeInOut(duration: 0.3), value: dashboardStore.yAxisDomain)
                .animation(dashboardStore.state.graph.isScrolling ? .none : .easeInOut(duration: 0.3), value: dashboardStore.yAxisTicks)

                // Apply decision window modifier first, then scroll detection
                .modifier(DecisionWindowModifier(
                    touchInteractionMode: $touchInteractionMode,
                    initialTouchPoint: $initialTouchPoint,
                    decisionTimer: $decisionTimer,
                    selectedXValue: $selectedXValue,
                    dashboardStore: dashboardStore
                ))
                // Keep existing scroll detection modifier
                .modifier(ScrollDetectionModifier(dashboardStore: dashboardStore, hasDetectedScrollInCurrentGesture: $hasDetectedScrollInCurrentGesture, selectedXValue: $selectedXValue))

                // Selection callout overlay - shows when a point is selected
                if let selectedPoint = dashboardStore.state.graph.selectedPoint,
                   dashboardStore.state.graph.showCrosshair {
                    selectionCallout(for: selectedPoint)
                }

                // Goal chip overlay - shows goal weight chip positioned based on ticks
                if dashboardStore.goalWeightForDisplay != 0 {
                    goalChipCallout()
                }
            }
        }

    }

    // MARK: - Selection Callout
    @ViewBuilder
    private func selectionCallout(for selectedPoint: BathScaleWeightSummary) -> some View {
        if let displayWeight = dashboardStore.displayWeight,
           let chartPosition = getChartPosition(for: selectedPoint.date, value: displayWeight),
           chartFrame.width > 0 {

            // Base positioning relative to the selected point
            let isOnLeftSide = chartPosition.x < chartFrame.width / 2
            let baseOffset: CGFloat = isOnLeftSide ? 0 : -40
            let finalXPosition = chartPosition.x + baseOffset
            Text(dashboardStore.weightLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .position(
                    //for year and total subract 60 else 100
                    x: max(50, min(chartFrame.width - (dashboardStore.state.graph.selectedPeriod == .year || dashboardStore.state.graph.selectedPeriod == .total ? 85 : 100), finalXPosition)), // Prevent cropping with 50pt padding
                    y: -15 // Position above chart boundary
                )
        }
      }

            // MARK: - Goal Chip Callout
    @ViewBuilder
    private func goalChipCallout() -> some View {
        let goalWeight = dashboardStore.goalWeightForDisplay
        let yAxisTicks = dashboardStore.yAxisTicks
        let yAxisDomain = dashboardStore.yAxisDomain

                                // Always show goal chip - it has special significance even if it matches a tick
        let goalPosition = getGoalChipPosition(goalWeight: goalWeight, ticks: yAxisTicks, domain: yAxisDomain)

                let xOffset = getGoalChipXOffset(for: goalWeight)

        goalWeightChip(goalWeight)
            .position(
                x: chartFrame.width > 0 ? chartFrame.width - xOffset : 320,
                y: goalPosition.yPosition
            )
            .animation(.easeInOut(duration: 0.2), value: goalPosition.yPosition)
    }

    // MARK: - Goal Chip Positioning
    private func getGoalChipXOffset(for goalWeight: Double) -> CGFloat {
        let formattedText = dashboardStore.formatYAxisTickLabel(goalWeight)

        // Check if it's a 3-digit value or longer
        if formattedText.count >= 3 {
            return 32 // More space for 3+ digit values
        } else {
            return 28 // Less space for 1-2 digit values
        }
    }

    private func getGoalChipPosition(goalWeight: Double, ticks: [Double], domain: ClosedRange<Double>) -> (yPosition: CGFloat, placement: GoalPlacement) {

        // If goal weight is higher than all ticks, show at top
        if let maxTick = ticks.max(), goalWeight > maxTick {
            return (yPosition: -25, placement: .top)
        }

        // If goal weight is lower than all ticks, show at bottom
        if let minTick = ticks.min(), goalWeight < minTick {
            return (yPosition: chartFrame.height + 20, placement: .bottom)
        }

        // Goal weight is within tick range, calculate proportional position
        let domainRange = domain.upperBound - domain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }

        let yRatio = (goalWeight - domain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return (yPosition: chartFrame.height / 2, placement: .middle)
        }

        let yPosition = chartFrame.height * (1 - yRatio) // Invert because chart y grows downward

        return (yPosition: yPosition, placement: .middle)
    }

    // MARK: - Goal Chip UI
    @ViewBuilder
    private func goalWeightChip(_ value: Double) -> some View {
        Text(dashboardStore.formatYAxisTickLabel(value))
            .fontWeight(.bold)
            .font(.body)
            .foregroundColor(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Capsule().fill(theme.statusSuccess))
    }

    // MARK: - Helper Methods for Callout

        /// Calculate the chart position for a given date and value
    private func getChartPosition(for date: Date, value: Double) -> CGPoint? {
        let yAxisDomain = dashboardStore.yAxisDomain

        let xPosition: CGFloat

        if dashboardStore.state.graph.selectedPeriod == .total {
            // For TOTAL period, calculate position based on actual data range
            let allOperations = dashboardStore.continuousOperations
            guard !allOperations.isEmpty else { return nil }

            let allDates = allOperations.map { $0.date }
            guard let minDate = allDates.min(), let maxDate = allDates.max() else { return nil }

                        let totalTimeRange = maxDate.timeIntervalSince(minDate)
            if totalTimeRange > 0 {
                let timeFromStart = date.timeIntervalSince(minDate)
                let xRatio = timeFromStart / totalTimeRange
                xPosition = chartFrame.width * xRatio
            } else {
                xPosition = chartFrame.width > 0 ? chartFrame.width / 2 : 0 // Single point, center it
            }
        } else {
            // For other periods, use scroll-based calculation
            let xScrollPosition = dashboardStore.state.graph.xScrollPosition
            let visibleDomainLength = dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod)
            guard visibleDomainLength.isFinite && visibleDomainLength > 0 else { return nil }
            let timeFromScrollPosition = date.timeIntervalSince(xScrollPosition)
            let xRatio = timeFromScrollPosition / visibleDomainLength
            xPosition = chartFrame.width * xRatio
        }

        // Calculate y position relative to y-axis domain
        let domainRange = yAxisDomain.upperBound - yAxisDomain.lowerBound
        guard domainRange > 0, chartFrame.height > 0 else {
            return CGPoint(x: 0, y: chartFrame.height / 2)
        }

        let yRatio = (value - yAxisDomain.lowerBound) / domainRange
        guard yRatio.isFinite else {
            return CGPoint(x: 0, y: chartFrame.height / 2)
        }

        let yPosition = chartFrame.height * (1 - yRatio) // Invert because chart y grows downward

        // Add padding offsets
        let adjustedX = xPosition + (isAtLeftBoundary ? .spacingXS : 0)
        let adjustedY = yPosition

        return CGPoint(x: adjustedX, y: adjustedY)
    }
    // MARK: - Computed Properties

        /// Determines if the chart is scrolled to the leftmost boundary of the data
    private var isAtLeftBoundary: Bool {
        //if total period, return true
        if dashboardStore.state.graph.selectedPeriod == .total { return true }

        guard !dashboardStore.continuousOperations.isEmpty else { return true }

        let operations = dashboardStore.continuousOperations
        let allDates = operations.map { $0.date }
        guard let minDate = allDates.min() else { return true }

        let currentScrollPosition = dashboardStore.state.graph.xScrollPosition
        let domainLength = dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod)
        let visibleStart = currentScrollPosition.addingTimeInterval(-domainLength / 2)

        // Consider at boundary if visible start is at or before the minimum data date
        // Add small buffer (1 day) to account for minor scroll position variations
        let boundaryThreshold: TimeInterval = 24 * 60 * 60 // 1 day
        return visibleStart <= minDate.addingTimeInterval(boundaryThreshold)
    }

    // MARK: - Empty State View
    private var emptyStateView: some View {
        VStack(spacing: .spacingMD) {
            Spacer()

            Text(emptyStateMessage)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.textHeading)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)

            Spacer()
        }
        .frame(height: 265)
        .frame(maxWidth: .infinity, minHeight: 240)
        .padding(.horizontal)
        .background(theme.textInverse)
    }

    // MARK: - Chart Content Builders
    @ChartContentBuilder
    private var yAxisGridLines: some ChartContent {
        // Use cached Y-axis ticks instead of calling getYAxisScale() to prevent publishing errors
        let yAxisTicks = dashboardStore.yAxisTicks

        ForEach(yAxisTicks, id: \.self) { tick in
            // Only show grid lines for non-goal weight ticks
            if abs(tick - dashboardStore.goalWeightForDisplay) > 0.01 {
                RuleMark(y: .value("YGrid", tick))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(theme.statusUtilityPrimary.opacity(0.3))
                    .zIndex(-1)
            }
        }
    }

    @ChartContentBuilder
    private var chartSeries: some ChartContent {
        let seriesData = dashboardStore.chartSeriesData
        let groupedSeries = Dictionary(grouping: seriesData) { $0.series }

        ForEach(Array(groupedSeries.keys.sorted()), id: \.self) { seriesName in
            if let seriesPoints = groupedSeries[seriesName] {
                chartContentForSeries(seriesName: seriesName, seriesPoints: seriesPoints)
            }
        }
    }

    @ChartContentBuilder
    private func chartContentForSeries(seriesName: String, seriesPoints: [GraphSeries]) -> some ChartContent {
        let segments = getConnectedSegments(from: seriesPoints, for: dashboardStore.state.graph.selectedPeriod)

        ForEach(Array(segments.enumerated()), id: \.offset) { segmentIndex, segment in
            chartContentForSegment(segment: segment, seriesName: seriesName, segmentIndex: segmentIndex)
        }
    }

    @ChartContentBuilder
    private func chartContentForSegment(segment: [GraphSeries], seriesName: String, segmentIndex: Int) -> some ChartContent {
        ForEach(segment) { point in
            invisibleTapTarget(for: point)
            lineMarkForPoint(point: point, seriesName: seriesName, segmentIndex: segmentIndex)
            visiblePointMark(for: point)
        }
    }

    @ChartContentBuilder
    private func invisibleTapTarget(for point: GraphSeries) -> some ChartContent {
        PointMark(
            x: .value("Date", point.date),
            y: .value(point.series, point.value)
        )
        .symbolSize(point.date == dashboardStore.state.graph.selectedPoint?.date ? 200 : getPointSizeForPeriod())
        .foregroundStyle(.clear)
    }

    @ChartContentBuilder
    private func lineMarkForPoint(point: GraphSeries, seriesName: String, segmentIndex: Int) -> some ChartContent {
        LineMark(
            x: .value("Date", point.date),
            y: .value(point.series, point.value),
            series: .value("Series", "\(point.series)-\(segmentIndex)")
        )
        .foregroundStyle(by: .value("Series", point.series))
        .interpolationMethod(.catmullRom)
        .lineStyle(StrokeStyle(lineWidth: 3))
    }

    @ChartContentBuilder
    private func visiblePointMark(for point: GraphSeries) -> some ChartContent {
        PointMark(
            x: .value("Date", point.date),
            y: .value(point.series, point.value)
        )
        .symbolSize(point.date == dashboardStore.state.graph.selectedPoint?.date ? 200 : getPointSizeForPeriod())
        .foregroundStyle(by: .value("Series", point.series))
    }

    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedPoint = dashboardStore.state.graph.selectedPoint,
           dashboardStore.state.graph.showCrosshair {
            // Dotted vertical line
            RuleMark(
                x: .value("Date", selectedPoint.date)
            )
            .zIndex(-100)
            .foregroundStyle(theme.actionSecondary)
            .lineStyle(StrokeStyle(lineWidth: 1))
        }
    }

    // MARK: - Smart Line Connection Logic

    /// Groups chart data points into connected segments based on time gaps
    /// Prevents lines from connecting across missing data periods
    private func getConnectedSegments(from dataPoints: [GraphSeries], for period: TimePeriod) -> [[GraphSeries]] {
        guard !dataPoints.isEmpty else { return [] }

        var segments: [[GraphSeries]] = []
        var currentSegment: [GraphSeries] = []

        let sortedPoints = dataPoints.sorted { $0.date < $1.date }

        for point in sortedPoints {
            if currentSegment.isEmpty {
                currentSegment.append(point)
            } else {
                let lastPoint = currentSegment.last!
                let timeDifference = point.date.timeIntervalSince(lastPoint.date)

                // Define maximum gap based on time period
                let maxGap: TimeInterval = getMaximumGap(for: period)

                if timeDifference <= maxGap {
                    // Continue current segment
                    currentSegment.append(point)
                } else {
                    // Start new segment due to gap
                    if !currentSegment.isEmpty {
                        segments.append(currentSegment)
                    }
                    currentSegment = [point]
                }
            }
        }

        // Add the last segment
        if !currentSegment.isEmpty {
            segments.append(currentSegment)
        }

        return segments
    }

    /// Determines the maximum time gap allowed before starting a new segment
    private func getMaximumGap(for period: TimePeriod) -> TimeInterval {
        switch period {
        case .week:
            return 14 * DashboardConstants.TimeInterval.day  // 14 days - don't connect if more than 3 days gap
        case .month:
            return 60 * DashboardConstants.TimeInterval.day  // 60 days - don't connect if more than 1 week gap
        case .year:
            return 365 * DashboardConstants.TimeInterval.day // 1 year - don't connect if more than 1 year gap
        case .total:
            return 365 * DashboardConstants.TimeInterval.day // 1 year - don't connect if more than 1 year gap
        }
    }

    // MARK: - Axis Marks Builders
    private var yAxisMarks: some AxisContent {
        // Use cached Y-axis ticks instead of calling getYAxisScale() to prevent publishing errors
        let yAxisTicks = dashboardStore.yAxisTicks

        return AxisMarks(values: yAxisTicks) { value in
            if let doubleValue = value.as(Double.self) {
                AxisValueLabel {
                  Text(dashboardStore.formatYAxisTickLabel(doubleValue))
                      .font(.body)
                      .fontWeight(.medium)
                      .foregroundColor(theme.textSubheading)
                      .padding(.horizontal, .spacingXS)
                }
            }
        }
    }

    // MARK: - Helper Functions

    /// Returns appropriate point size based on the selected period
    private func getPointSizeForPeriod() -> CGFloat {
        switch dashboardStore.state.graph.selectedPeriod {
        case .week, .month, .year:
            return 64  // Larger points for week view (fewer data points)
        case .total:
            return 16  // Very small points for total view (many data points)
        }
    }

    /// Returns visible domain length - for TOTAL, show all data without domain restriction
    private func getVisibleDomainLength() -> TimeInterval? {
        switch dashboardStore.state.graph.selectedPeriod {
        case .week, .month, .year:
            return dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod)
        case .total:
            // Use full data range to avoid zero-length domain which collapses axes
            let operations = dashboardStore.continuousOperations
            let dates = operations.map { $0.date }
            if let minDate = dates.min(), let maxDate = dates.max() {
                let length = maxDate.timeIntervalSince(minDate)
                return length > 0 ? length : nil
            }
            return nil
        }
    }

    /// Returns scrollable axes - disable scrolling for TOTAL
    private func getScrollableAxes() -> Axis.Set {
        switch dashboardStore.state.graph.selectedPeriod {
        case .week, .month, .year:
            return .horizontal
        case .total:
            return [] // No scrolling for total view
        }
    }

}

// MARK: - Goal Placement Enum
enum GoalPlacement {
    case top
    case bottom
    case middle
}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
