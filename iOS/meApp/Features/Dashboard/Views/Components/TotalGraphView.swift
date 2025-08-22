//
//  TotalGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import SwiftUI
import Charts

/// Dedicated view for rendering the Total time period chart
/// Handles all total-specific chart rendering and interactions
struct TotalGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: TotalSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    
    // MARK: - Local State
    @State private var localSelectedXValue: Date?
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Main Chart
                Chart {
                    yAxisGridLines
                    chartSeries
                    crosshairContent
                }
                .chartYScale(domain: viewModel.yAxisDomain)
                .chartYAxis { yAxisMarks }
                .chartLegend(.hidden)
                .chartXAxis {
                    // No X-axis for total view - empty
                }
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
                // Set X-axis domain to show all data
                .chartXScale(domain: viewModel.dateRange)
                .chartXSelection(value: Binding(
                    get: { localSelectedXValue },
                    set: { newValue in
                        localSelectedXValue = newValue
                        viewModel.handleChartSelection(at: newValue)
                        
                        // Update dashboard store selection
                        if let selectedDate = newValue {
                            Task {
                                await dashboardStore.handleChartSelection(at: selectedDate)
                            }
                        }
                    }
                ))
                .frame(height: 265)
                .background(
                    GeometryReader { geo in
                        theme.textInverse
                            .onAppear {
                                viewModel.updateChartFrame(geo.frame(in: .local))
                            }
                            .onChange(of: geo.frame(in: .local)) { _, newFrame in
                                viewModel.updateChartFrame(newFrame)
                            }
                    }
                )
                .animation(.none, value: dashboardStore.state.graph.isScrolling)
                
                // Selection callout overlay
                if let selectedPoint = viewModel.selectedPoint,
                   let displayWeight = viewModel.displayWeight,
                   viewModel.showCrosshair {
                    selectionCallout(for: selectedPoint, weight: displayWeight)
                }
                
                // Goal chip overlay
                if viewModel.goalWeight > 0 {
                    goalChipCallout()
                }
            }
        }
        .onAppear {
            viewModel.configure(with: dashboardStore)
        }
        .onChange(of: dashboardStore.continuousOperations) { _, _ in
            viewModel.refreshData()
        }
        .onChange(of: dashboardStore.currentUnit) { _, _ in
            viewModel.handleSettingsChange()
        }
        .onChange(of: dashboardStore.isWeightlessModeEnabled) { _, _ in
            viewModel.handleSettingsChange()
        }
    }
    
    // MARK: - Chart Content Builders
    
    @ChartContentBuilder
    private var yAxisGridLines: some ChartContent {
        ForEach(viewModel.yAxisTicks, id: \.self) { tick in
            RuleMark(y: .value("YGrid", tick))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusUtilityPrimary.opacity(0.3))
                .zIndex(-1)
        }
    }
    
    @ChartContentBuilder
    private var chartSeries: some ChartContent {
        let seriesData = viewModel.chartSeriesData
        let groupedSeries = Dictionary(grouping: seriesData) { $0.series }
        
        ForEach(Array(groupedSeries.keys.sorted()), id: \.self) { seriesName in
            if let seriesPoints = groupedSeries[seriesName] {
                chartContentForSeries(seriesName: seriesName, seriesPoints: seriesPoints)
            }
        }
    }
    
    @ChartContentBuilder
    private func chartContentForSeries(seriesName: String, seriesPoints: [GraphSeries]) -> some ChartContent {
        let segments = viewModel.getConnectedSegments(from: seriesPoints)
        
        ForEach(Array(segments.enumerated()), id: \.offset) { segmentIndex, segment in
            chartContentForSegment(segment: segment, seriesName: seriesName, segmentIndex: segmentIndex)
        }
    }
    
    @ChartContentBuilder
    private func chartContentForSegment(segment: [GraphSeries], seriesName: String, segmentIndex: Int) -> some ChartContent {
        ForEach(segment) { point in
            // Invisible tap target
            PointMark(
                x: .value("Date", point.date),
                y: .value(point.series, point.value)
            )
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.getPointSizeForTotal())
            .foregroundStyle(.clear)
            
            // Line mark
            LineMark(
                x: .value("Date", point.date),
                y: .value(point.series, point.value),
                series: .value("Series", "\(point.series)-\(segmentIndex)")
            )
            .foregroundStyle(by: .value("Series", point.series))
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: 3))
            
            // Visible point mark
            PointMark(
                x: .value("Date", point.date),
                y: .value(point.series, point.value)
            )
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.getPointSizeForTotal())
            .foregroundStyle(by: .value("Series", point.series))
        }
    }
    
    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedPoint = viewModel.selectedPoint, viewModel.showCrosshair {
            RuleMark(x: .value("Date", selectedPoint.date))
                .zIndex(-100)
                .foregroundStyle(theme.actionSecondary)
                .lineStyle(StrokeStyle(lineWidth: 1))
        }
    }
    
    // MARK: - Y-Axis Marks
    
    private var yAxisMarks: some AxisContent {
        AxisMarks(values: viewModel.yAxisTicks) { value in
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
    
    // MARK: - Selection Callout
    
    @ViewBuilder
    private func selectionCallout(for selectedPoint: BathScaleWeightSummary, weight: Double) -> some View {
        if let chartPosition = viewModel.getChartPosition(for: selectedPoint.date, value: weight) {
            // Base positioning relative to the selected point
            let isOnLeftSide = chartPosition.x < viewModel.chartFrame.width / 2
            let baseOffset: CGFloat = isOnLeftSide ? -10 : -40
            let finalXPosition = chartPosition.x + baseOffset
            
            Text(viewModel.weightLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .position(
                    x: max(50, min(viewModel.chartFrame.width - 85, finalXPosition)), // Prevent cropping
                    y: -15 // Position above chart boundary
                )
        }
    }
    
    // MARK: - Goal Chip Callout
    
    @ViewBuilder
    private func goalChipCallout() -> some View {
        let goalPosition = viewModel.getGoalChipPosition()
        let xOffset = viewModel.getGoalChipXOffset()
        
        goalWeightChip(viewModel.goalWeight)
            .position(
                x: viewModel.chartFrame.width > 0 ? viewModel.chartFrame.width - xOffset : 320,
                y: goalPosition.yPosition
            )
            .animation(.easeOut(duration: 0.3), value: goalPosition.yPosition)
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
}

#Preview {
    TotalGraphView(
        viewModel: TotalSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
