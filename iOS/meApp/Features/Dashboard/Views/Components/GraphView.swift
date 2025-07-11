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
    
    // Check if there are any entries to display
    private var hasEntries: Bool {
        !dashboardStore.continuousOperations.isEmpty
    }
    
    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        if dashboardStore.hasEntriesButNoneInCurrentPeriod {
            return DashboardStrings.noEntriesInPeriodMessage(dashboardStore.selectedPeriod.rawValue)
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

            if hasEntries {
                // Show chart when there are entries
                chartView
            } else {
                // Show empty state message when there are no entries
                emptyStateView
            }
        }
        .onChange(of: dashboardStore.isScrolling) { _, isScrolling in
            // When scrolling stops, recalculate Y-axis based on visible operations
            if !isScrolling {
                // Use a timer to delay the recalculation
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    if !dashboardStore.isScrolling {
                        // Chart will automatically refresh when data changes
                        // No need to manually trigger Y-axis updates
                    }
                }
            }
        }
        .onChange(of: dashboardStore.selectedPeriod) { _, _ in
            // Clear crosshair and selection when time period changes
            dashboardStore.showCrosshair = false
            dashboardStore.selectedPoint = nil
            dashboardStore.selectedXValue = nil
            dashboardStore.selectedEntry = nil
            dashboardStore.selectedWeight = nil
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
            .chartXVisibleDomain(length: dashboardStore.visibleDomainLength(for: dashboardStore.selectedPeriod))
            .chartScrollableAxes(.horizontal)
            .chartScrollTargetBehavior(.paging)
            .chartScrollTargetBehavior(.valueAligned(unit: dashboardStore.timeSnapUnit(for: dashboardStore.selectedPeriod)))
            .chartYScale(domain: {
                let yAxisScale = dashboardStore.getYAxisScale()
                return yAxisScale.domain
            }())
            .chartScrollPosition(x: Binding(
                get: { dashboardStore.xScrollPosition },
                set: { dashboardStore.xScrollPosition = $0 }
            ))
            .id(dashboardStore.dataChangeTrigger) // Force chart re-render when data changes
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
            .id(dashboardStore.dataChangeTrigger) // Force chart re-render when data changes
            .id(dashboardStore.currentUnit) // Force chart re-render when unit changes
            .chartXSelection(value: Binding(
                get: { dashboardStore.selectedXValue },
                set: { newValue in
                    dashboardStore.selectedXValue = newValue
                    if let selectedDate = newValue {
                        Task { @MainActor in
                            dashboardStore.handleChartSelection(at: selectedDate)
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
                            dashboardStore.chartHeight = geo.size.height 
                        }
                }
            )
            .onPreferenceChange(AnnotationHeightKey.self) { height in
                dashboardStore.annotationHeight = height
            }
            .accessibilityLabel(Text("Weight chart"))
            .onAppear {
                // Set initial scroll position to latest data
                if let latestDate = dashboardStore.continuousOperations.map(\.date).max() {
                    dashboardStore.xScrollPosition = latestDate
                    print("Hello: GraphView - onAppear - Set scroll position to latest date: \(latestDate)")
                }
            }
            .onChange(of: dashboardStore.continuousOperations.count) { _, _ in
                // When data changes, scroll to latest position
                if let latestDate = dashboardStore.continuousOperations.map(\.date).max() {
                    dashboardStore.xScrollPosition = latestDate
                    print("Hello: GraphView - onChange - Updated scroll position to latest date: \(latestDate)")
                }
            }
            // Add scroll gesture detection for dynamic Y-axis
            .simultaneousGesture(
                DragGesture(minimumDistance: 3)
                    .onChanged { value in
                        // Only detect horizontal scrolling gestures
                        let isHorizontalScroll = abs(value.translation.width) > abs(value.translation.height) * 1.5
                        let isSignificantMovement = abs(value.translation.width) > 8
                        
                        if isHorizontalScroll && isSignificantMovement && !dashboardStore.hasDetectedScrollInCurrentGesture {
                            dashboardStore.hasDetectedScrollInCurrentGesture = true
                            dashboardStore.isScrolling = true
                            
                            // Clear selection when scrolling starts
                            dashboardStore.selectedXValue = nil
                            dashboardStore.selectedPoint = nil
                            dashboardStore.showCrosshair = false
                            dashboardStore.selectEntry(nil)
                        }
                    }
                    .onEnded { value in
                        // Reset for next gesture
                        dashboardStore.hasDetectedScrollInCurrentGesture = false
                        
                        // Only handle if it was a horizontal scroll
                        let isHorizontalScroll = abs(value.translation.width) > abs(value.translation.height) * 1.5
                        let isSignificantMovement = abs(value.translation.width) > 8
                        
                        if isHorizontalScroll && isSignificantMovement {
                            // Set a timer to detect when scrolling has truly ended
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                                dashboardStore.isScrolling = false
                                // Reset metrics to latest entry when scrolling ends
                                dashboardStore.resetMetricsToLatestEntry()
                                // Update visible data after scroll ends
                                dashboardStore.updateVisibleDataAfterScroll()
                            }
                        }
                    }
            )
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
        let yAxisScale = dashboardStore.getYAxisScale()
        
        ForEach(yAxisScale.ticks, id: \.self) { tick in
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
            .symbolSize(series.date == dashboardStore.selectedPoint?.date ? 200 : 64)
            .foregroundStyle(by: .value("Series", series.series))
        }
    }
    
    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedPoint = dashboardStore.selectedPoint, dashboardStore.showCrosshair {
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
        let yAxisScale = dashboardStore.getYAxisScale()
        
        return AxisMarks(values: yAxisScale.ticks) { value in
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
        AxisMarks(values: dashboardStore.xAxisValuesWithBuffer(for: dashboardStore.selectedPeriod)) { value in
            AxisGridLine()
            AxisTick()
            AxisValueLabel {
                if let date = value.as(Date.self),
                   let labelString = dashboardStore.xLabelString(for: date, period: dashboardStore.selectedPeriod) {
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
            .id(dashboardStore.currentUnit) // Force goal weight bubble to recalculate when unit changes
    }
}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
