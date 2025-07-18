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

    // Check if there are any entries to display
    private var hasEntries: Bool {
        return !dashboardStore.continuousOperations.isEmpty
    }

    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        if dashboardStore.hasEntriesButNoneInCurrentPeriod {
            return DashboardStrings.noEntriesInPeriodMessage(dashboardStore.state.graph.selectedPeriod.rawValue)
        } else {
            return DashboardStrings.noEntriesMessage
        }
    }

    var body: some View {
        VStack(alignment: .leading){
            Text(dashboardStore.weightLabel)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.leading, .spacingSM)
                    .padding(.vertical, .spacingXS)

            chartView
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            // Clear crosshair and selection when time period changes
            dashboardStore.clearSelection()
            // Trigger animation for period change
            withAnimation(.easeInOut(duration: 0.6)) {
                animationTrigger = UUID()
            }
        }
        .onChange(of: dashboardStore.state.graph.dataChangeTrigger) { _, _ in
            // Trigger animation for data changes
            withAnimation(.easeInOut(duration: 0.4)) {
                animationTrigger = UUID()
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
            .chartXVisibleDomain(length: dashboardStore.visibleDomainLength(for: dashboardStore.state.graph.selectedPeriod))
            .chartScrollableAxes(.horizontal)
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
            .chartXAxis { xAxisMarks }
            .chartXSelection(value: Binding(
                get: {
                    // Use local state for selection (like WeightGraph)
                    selectedXValue
                },
                set: { newValue in
                    // Only handle selection if not scrolling
                    if !dashboardStore.state.graph.isScrolling {
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
            // Add animation modifier for smooth chart transitions
            .animation(.easeInOut(duration: 0.5), value: dashboardStore.yAxisDomain)
            .animation(.easeInOut(duration: 0.3), value: dashboardStore.yAxisTicks)
            .animation(.spring(response: 0.6, dampingFraction: 0.8), value: animationTrigger)
            // Use iOS 18+ scroll phase change when available, fallback to drag gesture for older iOS
            .modifier(ScrollDetectionModifier(dashboardStore: dashboardStore, hasDetectedScrollInCurrentGesture: $hasDetectedScrollInCurrentGesture, selectedXValue: $selectedXValue))
        }
        // Single chart refresh trigger for better performance
        .id("\(dashboardStore.state.graph.selectedPeriod.rawValue)-\(dashboardStore.currentUnit.rawValue)-\(dashboardStore.state.graph.dataChangeTrigger)")
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
            .symbolSize(series.date == dashboardStore.state.graph.selectedPoint?.date ? 200 : 64)
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

    private var xAxisMarks: some AxisContent {
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
