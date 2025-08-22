//
//  YearGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import SwiftUI
import Charts

/// Dedicated view for rendering the Year time period chart
/// Handles all year-specific chart rendering including scrolling and month-based interactions
struct YearGraphView: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: YearSectionViewModel
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    
    // MARK: - Local State
    @State private var localSelectedXValue: Date?
    @State private var hasDetectedScrollInCurrentGesture = false
    @State private var touchInteractionMode: TouchInteractionMode = .none
    @State private var initialTouchPoint: CGPoint = .zero
    @State private var decisionTimer: Timer?
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Main Chart with scrolling support
                Chart {
                    yAxisGridLines
                    chartSeries
                    crosshairContent
                }
                .chartXVisibleDomain(length: viewModel.visibleDomainLength)
                .chartScrollableAxes(.horizontal)
                .chartYScale(domain: viewModel.yAxisDomain)
                .chartScrollPosition(x: Binding(
                    get: { viewModel.scrollPosition },
                    set: { newPosition in
                        viewModel.handleScrollPositionChange(newPosition)
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
                    AxisMarks(values: viewModel.xAxisValues) { value in
                        AxisGridLine()
                        AxisTick()
                        AxisValueLabel {
                            if let date = value.as(Date.self),
                               let labelString = viewModel.formatXAxisLabel(for: date) {
                                Text(labelString)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        }
                    }
                }
                .chartXSelection(value: Binding(
                    get: { localSelectedXValue },
                    set: { newValue in
                        // Only handle selection if not in scroll mode and not actively scrolling
                        if touchInteractionMode != .scrolling && !viewModel.isScrolling {
                            localSelectedXValue = newValue
                            viewModel.handleChartSelection(at: newValue)
                            
                            // Update dashboard store selection
                            if let selectedDate = newValue {
                                Task {
                                    await dashboardStore.handleChartSelection(at: selectedDate)
                                }
                            }
                        }
                    }
                ))
                .frame(height: 265)
                .frame(maxWidth: .infinity, minHeight: 240)
                .padding(.leading, viewModel.isAtLeftBoundary ? .spacingXS : 0)
                .padding(.trailing, .spacingXS)
                .background(
                    GeometryReader { geo in
                        theme.textInverse
                            .onAppear {
                                dashboardStore.state.graph.chartHeight = geo.size.height
                                viewModel.updateChartFrame(geo.frame(in: .local))
                            }
                            .onChange(of: geo.frame(in: .local)) { _, newFrame in
                                viewModel.updateChartFrame(newFrame)
                            }
                    }
                )
                .onPreferenceChange(AnnotationHeightKey.self) { height in
                    dashboardStore.state.graph.annotationHeight = height
                }
                .onAppear {
                    viewModel.initializeChart()
                }
                .animation(.none, value: viewModel.scrollPosition) // Never animate scroll position
                .animation(.none, value: viewModel.isScrolling) // Never animate scrolling state changes
                
                // Apply decision window modifier for touch interaction
                .modifier(DecisionWindowModifier(
                    touchInteractionMode: $touchInteractionMode,
                    initialTouchPoint: $initialTouchPoint,
                    decisionTimer: $decisionTimer,
                    selectedXValue: $localSelectedXValue,
                    dashboardStore: dashboardStore
                ))
                // Apply scroll detection modifier
                .modifier(ScrollDetectionModifier(
                    dashboardStore: dashboardStore,
                    hasDetectedScrollInCurrentGesture: $hasDetectedScrollInCurrentGesture,
                    selectedXValue: $localSelectedXValue
                ))
                
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
        .onChange(of: dashboardStore.state.graph.xScrollPosition) { _, newPosition in
            viewModel.updateScrollPosition(to: newPosition)
        }
        .onChange(of: dashboardStore.state.graph.isScrolling) { _, isScrolling in
            viewModel.isScrolling = isScrolling
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
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.getPointSizeForYear())
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
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.getPointSizeForYear())
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
                    x: max(50, min(viewModel.chartFrame.width - 100, finalXPosition)), // Prevent cropping
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
            .animation(viewModel.shouldAnimateChartData ? .easeOut(duration: 0.3) : .none, value: goalPosition.yPosition)
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
    YearGraphView(
        viewModel: YearSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
