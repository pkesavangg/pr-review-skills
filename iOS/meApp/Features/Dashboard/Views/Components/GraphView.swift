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

    // Animation trigger for smooth transitions
    @State private var animationTrigger = UUID()

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
            // Hide weight label when there is a selection

            Text(dashboardStore.state.graph.selectedPoint == nil ? dashboardStore.weightLabel : "")
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
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

        .onChange(of: dashboardStore.chartSeriesData) { _, _ in
            // Ensure chart data changes are animated smoothly
            withAnimation(.easeOut(duration: 0.6)) {
            }
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
                .accessibilityLabel(Text("Weight chart"))
                .onAppear {
                    dashboardStore.initializeChart()
                }
                // Synchronized animations for chart components
                .animation(dashboardStore.state.graph.isScrolling ? .none : .easeInOut(duration: 0.3), value: dashboardStore.yAxisDomain)
                .animation(dashboardStore.state.graph.isScrolling ? .none : .easeInOut(duration: 0.3), value: dashboardStore.yAxisTicks)
                .animation(.none, value: dashboardStore.state.graph.xScrollPosition) // Never animate scroll position
                .animation(.none, value: dashboardStore.state.graph.isScrolling) // Never animate scrolling state changes
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
            }
        }

    }

                                 // MARK: - Selection Callout
    @ViewBuilder
    private func selectionCallout(for selectedPoint: BathScaleWeightSummary) -> some View {
        if let displayWeight = dashboardStore.displayWeight,
           let chartPosition = getChartPosition(for: selectedPoint.date, value: displayWeight) {

            let isOnLeftSide = chartPosition.x < chartFrame.width / 2
            let textOffset: CGFloat = isOnLeftSide ? 0 : -25 // Offset from the line
            let finalXPosition = chartPosition.x + textOffset
            Text(dashboardStore.weightLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .position(
                    x: max(50, min(chartFrame.width - 50, finalXPosition)), // Prevent cropping with 50pt padding
                    y: -15 // Position above chart boundary
                )
                .animation(.easeInOut(duration: 0.2), value: finalXPosition)
        }
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
                xPosition = chartFrame.width / 2 // Single point, center it
            }
        } else {
            // For other periods, use scroll-based calculation
            let xScrollPosition = dashboardStore.state.graph.xScrollPosition
            let visibleDomainLength = dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod)

            let timeFromScrollPosition = date.timeIntervalSince(xScrollPosition)
            let xRatio = timeFromScrollPosition / visibleDomainLength
            xPosition = chartFrame.width * xRatio
        }

        // Calculate y position relative to y-axis domain
        let yRatio = (value - yAxisDomain.lowerBound) / (yAxisDomain.upperBound - yAxisDomain.lowerBound)
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
            .lineStyle(StrokeStyle(lineWidth: 2))
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
                if abs(doubleValue - dashboardStore.goalWeightForDisplay) < 0.01 {
                    AxisValueLabel {
                        goalWeightBubbleLabel(doubleValue)
                    }
                } else {
                    AxisValueLabel {
                        Text(dashboardStore.formatYAxisTickLabel(doubleValue))
                            .font(.body)
                            .fontWeight(.medium)
                            .foregroundColor(theme.textSubheading)
                    }
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
            return nil // Show all data points without domain restriction
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



    // MARK: - Axis Label Helpers
    @ViewBuilder
    private func goalWeightBubbleLabel(_ value: Double) -> some View {
        Text(dashboardStore.formatYAxisTickLabel(value))
            .fontWeight(.bold)
            .font(.body)
            .foregroundColor(.white)
            .padding(.horizontal, 5)
            .padding(.vertical, 1)
            .background(Capsule().fill(theme.statusSuccess))
            .background(
                GeometryReader { bubbleGeo in
                    Color.clear
                        .preference(key: AnnotationHeightKey.self, value: bubbleGeo.size.height)
                }
            )
            .zIndex(100)
    }
}

// MARK: - Touch Interaction Mode

/// Enum to track the current touch interaction mode
enum TouchInteractionMode {
    case none
    case deciding
    case scrubbing
    case scrolling
}

// MARK: - Decision Window Modifier

/// ViewModifier that implements a decision window to determine touch interaction mode
struct DecisionWindowModifier: ViewModifier {
    @Binding var touchInteractionMode: TouchInteractionMode
    @Binding var initialTouchPoint: CGPoint
    @Binding var decisionTimer: Timer?
    @Binding var selectedXValue: Date?
    let dashboardStore: DashboardStore

    // Constants for decision logic
    private let decisionWindowDuration: TimeInterval = 0.15 // 150ms
    private let movementThreshold: CGFloat = 12 // 10-12 points
    private let scrollThreshold: CGFloat = 10 // 8-10 points
    private let horizontalScrollRatio: CGFloat = 1.5 // abs(dx) > 1.5 * abs(dy)

    func body(content: Content) -> some View {
        content
            .simultaneousGesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        handleTouchChange(value)
                    }
                    .onEnded { value in
                        handleTouchEnd(value)
                    }
            )
    }

    private func handleTouchChange(_ value: DragGesture.Value) {
        let translation = value.translation

        switch touchInteractionMode {
        case .none:
            // Start decision window
            touchInteractionMode = .deciding
            initialTouchPoint = value.location
            startDecisionTimer()

        case .deciding:
            // Check if we should enter scroll mode early
            let dx = abs(translation.width)
            let dy = abs(translation.height)
            let isHorizontalMovement = dx > horizontalScrollRatio * dy && dx > scrollThreshold

            if isHorizontalMovement {
                enterScrollMode()
            }
            // If not horizontal scrolling, let timer decide

        case .scrubbing:
            // Continue scrubbing - chartXSelection will handle this
            break

        case .scrolling:
            // Let existing scroll detection handle it
            break
        }
    }

    private func handleTouchEnd(_ value: DragGesture.Value) {
        cancelDecisionTimer()

        switch touchInteractionMode {
        case .scrubbing:
            // Clear selection when user lifts finger
            selectedXValue = nil

        case .scrolling:
            // Let existing scroll detection handle scroll end
            break

        case .deciding, .none:
            // For quick taps, let the chart's natural selection work
            break
        }

        // Reset interaction mode
        touchInteractionMode = .none
    }

    private func startDecisionTimer() {
        cancelDecisionTimer()

        decisionTimer = Timer.scheduledTimer(withTimeInterval: decisionWindowDuration, repeats: false) { _ in
            Task { @MainActor in
                // Timer fired - if still in deciding mode, enter scrub mode
                if touchInteractionMode == .deciding {
                    enterScrubMode()
                }
            }
        }
    }

    private func cancelDecisionTimer() {
        decisionTimer?.invalidate()
        decisionTimer = nil
    }

    private func enterScrubMode() {
        guard touchInteractionMode == .deciding else { return }

        touchInteractionMode = .scrubbing
        cancelDecisionTimer()
    }

        private func enterScrollMode() {
        guard touchInteractionMode == .deciding else { return }

        touchInteractionMode = .scrolling
        cancelDecisionTimer()

        // Clear active selection so crosshair disappears
        selectedXValue = nil
    }
}

// MARK: - Scroll Detection Modifier

/// ViewModifier that handles scroll detection using iOS 18+ onScrollPhaseChange when available,
/// with fallback to simultaneousGesture for older iOS versions
struct ScrollDetectionModifier: ViewModifier {
    let dashboardStore: DashboardStore
    @Binding var hasDetectedScrollInCurrentGesture: Bool
    @Binding var selectedXValue: Date?

    func body(content: Content) -> some View {
        if #available(iOS 18.0, *) {
            content
                .onScrollPhaseChange { oldPhase, newPhase in
                    Task { @MainActor in
                        await dashboardStore.handleScrollPhaseChange(to: newPhase)

                        // Clear local selection state when scrolling starts
                        if newPhase == .interacting {
                            selectedXValue = nil
                        }
                    }
                }
        } else {
            content
                .simultaneousGesture(
                    DragGesture(minimumDistance: 3)
                        .onChanged { value in
                            let isHorizontalScroll = abs(value.translation.width) > abs(value.translation.height) * 1.5
                            let isSignificantMovement = abs(value.translation.width) > 8

                            if isHorizontalScroll && isSignificantMovement && !hasDetectedScrollInCurrentGesture {
                                hasDetectedScrollInCurrentGesture = true
                                dashboardStore.handleScrollStart()

                                // Clear local selection state when scrolling starts
                                selectedXValue = nil
                            }
                        }
                        .onEnded { value in
                            hasDetectedScrollInCurrentGesture = false
                            dashboardStore.handleScrollEndOptimized()
                        }
                )
        }
    }
}




#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
