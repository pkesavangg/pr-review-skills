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
                let domain = getOptimalYScaleDomain()
                return domain
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
        let ticks = getOptimalYAxisTicks()
        
        ForEach(ticks, id: \.self) { tick in
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
        let ticks = getOptimalYAxisTicks()
        
        return AxisMarks(values: ticks) { value in
            if let doubleValue = value.as(Double.self) {
                if abs(doubleValue - dashboardStore.goalWeightForDisplay) < 0.01 {
                    AxisValueLabel {
                        goalWeightBubbleLabel(doubleValue)
                    }
                } else {
                    AxisValueLabel {
                        Text(dashboardStore.formatWeightDisplayText(doubleValue))
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
    
    // MARK: - Dynamic Y-Axis Calculation Methods
    
    /// Get the current Y-axis scale domain based on all data points in current time period
    private func getCurrentYScaleDomain() -> ClosedRange<Double> {
        let domain = dashboardStore.getCurrentYScaleDomain()
        return domain
    }
    
    /// Get the optimal Y-axis domain that makes full use of chart height
    private func getOptimalYScaleDomain() -> ClosedRange<Double> {
        let scale = dashboardStore.getCurrentYAxisScale()
        let domain = scale.domain
        
        // Ensure the domain spans the full chart height with even spacing
        let allOps = dashboardStore.continuousOperations
        let allWeightValues = allOps.map { summary -> Double in
            if dashboardStore.isWeightlessModeEnabled {
                guard let anchorWeight = dashboardStore.weightlessAnchorWeight else { return 0 }
                let currentWeight = dashboardStore.convertStoredWeightToDisplay(Int(summary.weight))
                return currentWeight - anchorWeight
            } else {
                return dashboardStore.convertStoredWeightToDisplay(Int(summary.weight))
            }
        }
        
        if let minWeight = allWeightValues.min(),
           let maxWeight = allWeightValues.max() {
            // Calculate the optimal range that makes full use of chart height
            let dataRange = maxWeight - minWeight
            let goalWeight = dashboardStore.goalWeightForDisplay
            
            // Include goal weight in the range calculation
            let effectiveMin = min(minWeight, goalWeight)
            let effectiveMax = max(maxWeight, goalWeight)
            let effectiveRange = effectiveMax - effectiveMin
            
            // Add padding to ensure all data points are visible
            let padding = max(effectiveRange * 0.1, 5.0) // At least 5 units of padding
            let paddedMin = effectiveMin - padding
            let paddedMax = effectiveMax + padding
            
            print("Hello: getOptimalYScaleDomain - Data range: \(dataRange), Goal weight: \(goalWeight)")
            print("Hello: getOptimalYScaleDomain - Effective range: \(effectiveRange), Padding: \(padding)")
            print("Hello: getOptimalYScaleDomain - Optimal domain: \(paddedMin)...\(paddedMax)")
            
            return paddedMin...paddedMax
        }
        
        return domain
    }
    
    /// Get the current Y-axis ticks based on all data points in current time period
    private func getCurrentYAxisTicks() -> [Double] {
        let ticks = dashboardStore.getCurrentYAxisTicks()
        return ticks
    }
    
    /// Get optimal Y-axis ticks that make full use of chart height
    private func getOptimalYAxisTicks() -> [Double] {
        let scale = dashboardStore.getCurrentYAxisScale()
        let ticks = scale.labels.map { Double($0) }
        
        print("Hello: getOptimalYAxisTicks - Chart height: \(dashboardStore.chartHeight)")
        print("Hello: getOptimalYAxisTicks - Optimal ticks: \(ticks)")
        
        return ticks
    }

    // MARK: - Axis Label Helpers
    @ViewBuilder
    private func goalWeightBubbleLabel(_ value: Double) -> some View {
        Text(dashboardStore.formatWeightDisplayText(value))
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

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
