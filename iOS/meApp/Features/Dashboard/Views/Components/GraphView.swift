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
            Text(dashboardStore.weightLabel)
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
            .padding(.horizontal)
            .background(
                GeometryReader { geo in
                    theme.textInverse
                        .onAppear {
                            dashboardStore.state.graph.chartHeight = geo.size.height
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
            // Restore animations for data changes and scrolling, but keep period switching instant
            .animation(.easeOut(duration: 0.6), value: dashboardStore.yAxisTicks)
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
        }

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

        ForEach(seriesData) { series in

            LineMark(
                x: .value("Date", series.date),
                y: .value(series.series, series.value)
            )
            .foregroundStyle(by: .value("Series", series.series))
            .interpolationMethod(.catmullRom)
            .lineStyle(StrokeStyle(lineWidth: 3))

            PointMark(
                x: .value("Date", series.date),
                y: .value(series.series, series.value)
            )
            .symbolSize(series.date == dashboardStore.state.graph.selectedPoint?.date ? 200 : getPointSizeForPeriod())
            .foregroundStyle(by: .value("Series", series.series))
        }
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

// MARK: - Scroll Position Modifier

/// ViewModifier that conditionally applies scroll position based on period
struct ScrollPositionModifier: ViewModifier {
    let dashboardStore: DashboardStore

    func body(content: Content) -> some View {
        if dashboardStore.state.graph.selectedPeriod != .total {
            content.chartScrollPosition(x: Binding(
                get: { dashboardStore.state.graph.xScrollPosition },
                set: { newPosition in
                    dashboardStore.handleScrollPositionChange(newPosition)
                }
            ))
        } else {
            content
        }
    }
}



#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
