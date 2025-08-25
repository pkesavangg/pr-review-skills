//
//  BaseGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import SwiftUI
import Charts

/// Base graph view that provides common chart rendering functionality for all time periods
/// Eliminates code duplication across WeekGraphView, MonthGraphView, YearGraphView, and TotalGraphView
struct BaseGraphView<ViewModel: SectionViewModelProtocol>: View {
    
    // MARK: - Dependencies
    @ObservedObject var viewModel: ViewModel
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme
    
    // MARK: - Local State
    @State private var localSelectedXValue: Date?
    @State private var hasDetectedScrollInCurrentGesture = false
    @State private var touchInteractionMode: TouchInteractionMode = .none
    @State private var initialTouchPoint: CGPoint = .zero
    @State private var decisionTimer: Timer?
    
    // MARK: - Configuration
    private var isScrollable: Bool {
        viewModel.hasXAxis
    }
    
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
                .chartForegroundStyleScale(mapping: { (seriesName: String) in
                    switch seriesName {
                    case DashboardStrings.weight:
                        return theme.actionPrimary
                    default:
                        return theme.actionSecondary
                    }
                })
                // Conditional chart modifiers based on scrollability
                .conditionalModifiers(
                    isScrollable: isScrollable,
                    viewModel: viewModel,
                    localSelectedXValue: $localSelectedXValue,
                    touchInteractionMode: touchInteractionMode,
                    dashboardStore: dashboardStore
                )
                .frame(height: 265)
                .frame(maxWidth: .infinity, minHeight: 240)
                .padding(.leading, viewModel.isAtLeftBoundary ? .spacingXS : 0)
                .padding(.trailing, isScrollable ? .spacingXS : 0)
                .background(
                    GeometryReader { geo in
                        theme.textInverse
                            .onAppear {
                                if isScrollable {
                                    dashboardStore.state.graph.chartHeight = geo.size.height
                                }
                                viewModel.updateChartFrame(geo.frame(in: .local))
                            }
                            .onChange(of: geo.frame(in: .local)) { _, newFrame in
                                viewModel.updateChartFrame(newFrame)
                            }
                    }
                )
                // Conditional preference change handling
                .conditionalPreferenceChange(isScrollable: isScrollable, dashboardStore: dashboardStore)
                .onAppear {
                    viewModel.initializeChart()
                }
                .animation(.none, value: viewModel.scrollPosition) // Never animate scroll position
                .animation(.none, value: viewModel.isScrolling) // Never animate scrolling state changes
                
                // Apply touch interaction modifiers only for scrollable charts
                .conditionalTouchModifiers(
                    isScrollable: isScrollable,
                    touchInteractionMode: $touchInteractionMode,
                    initialTouchPoint: $initialTouchPoint,
                    decisionTimer: $decisionTimer,
                    localSelectedXValue: $localSelectedXValue,
                    hasDetectedScrollInCurrentGesture: $hasDetectedScrollInCurrentGesture,
                    dashboardStore: dashboardStore
                )
                
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
        // Conditional scroll position syncing
        .conditionalScrollSyncing(
            isScrollable: isScrollable,
            viewModel: viewModel,
            dashboardStore: dashboardStore,
            localSelectedXValue: $localSelectedXValue
        )
        .graphViewStyle(isAtLeftBoundary: viewModel.isAtLeftBoundary)
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
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.pointSize)
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
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.pointSize)
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
                    x: max(50, min(viewModel.chartFrame.width - (isScrollable ? 100 : 85), finalXPosition)), // Prevent cropping
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
            .animation(
                viewModel.shouldAnimateChartData ? .easeOut(duration: 0.3) : .none,
                value: goalPosition.yPosition
            )
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

// MARK: - View Extensions for Conditional Modifiers

extension View {
    
    @ViewBuilder
    func conditionalModifiers<ViewModel: SectionViewModelProtocol>(
        isScrollable: Bool,
        viewModel: ViewModel,
        localSelectedXValue: Binding<Date?>,
        touchInteractionMode: TouchInteractionMode,
        dashboardStore: DashboardStore
    ) -> some View {
        if isScrollable {
            self
                .chartXVisibleDomain(length: viewModel.visibleDomainLength)
                .chartScrollableAxes(.horizontal)
                .chartScrollPosition(x: Binding(
                    get: { viewModel.scrollPosition },
                    set: { newPosition in
                        viewModel.handleScrollPositionChange(newPosition)
                    }
                ))
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
                    get: { localSelectedXValue.wrappedValue },
                    set: { newValue in
                        // Only handle selection if not in scroll mode and not actively scrolling
                        if touchInteractionMode != .scrolling && !viewModel.isScrolling {
                            // Only update selection if we have a valid value
                            if let selectedDate = newValue {
                                localSelectedXValue.wrappedValue = newValue
                                viewModel.handleChartSelection(at: newValue)
                                
                                // Update dashboard store selection
                                Task {
                                    await dashboardStore.handleChartSelection(at: selectedDate)
                                }
                            }
                        }
                    }
                ))
        } else {
            // For non-scrollable (Total) view
            self
                .chartXScale(domain: viewModel.dateRange)
                .chartXAxis {
                    // No X-axis for total view
                }
                .chartXSelection(value: Binding(
                    get: { localSelectedXValue.wrappedValue },
                    set: { newValue in
                        localSelectedXValue.wrappedValue = newValue
                        viewModel.handleChartSelection(at: newValue)
                        
                        // Update dashboard store selection
                        if let selectedDate = newValue {
                            Task {
                                await dashboardStore.handleChartSelection(at: selectedDate)
                            }
                        }
                    }
                ))
        }
    }
    
    @ViewBuilder
    func conditionalPreferenceChange(isScrollable: Bool, dashboardStore: DashboardStore) -> some View {
        if isScrollable {
            self.onPreferenceChange(AnnotationHeightKey.self) { height in
                dashboardStore.state.graph.annotationHeight = height
            }
        } else {
            self
        }
    }
    
    @ViewBuilder
    func conditionalTouchModifiers(
        isScrollable: Bool,
        touchInteractionMode: Binding<TouchInteractionMode>,
        initialTouchPoint: Binding<CGPoint>,
        decisionTimer: Binding<Timer?>,
        localSelectedXValue: Binding<Date?>,
        hasDetectedScrollInCurrentGesture: Binding<Bool>,
        dashboardStore: DashboardStore
    ) -> some View {
        if isScrollable {
            self
                .modifier(DecisionWindowModifier(
                    touchInteractionMode: touchInteractionMode,
                    initialTouchPoint: initialTouchPoint,
                    decisionTimer: decisionTimer,
                    selectedXValue: localSelectedXValue,
                    dashboardStore: dashboardStore
                ))
                .modifier(ScrollDetectionModifier(
                    dashboardStore: dashboardStore,
                    hasDetectedScrollInCurrentGesture: hasDetectedScrollInCurrentGesture,
                    selectedXValue: localSelectedXValue
                ))
        } else {
            self
        }
    }
    
    @ViewBuilder
    func conditionalScrollSyncing<ViewModel: SectionViewModelProtocol>(
        isScrollable: Bool,
        viewModel: ViewModel,
        dashboardStore: DashboardStore,
        localSelectedXValue: Binding<Date?>
    ) -> some View {
        if isScrollable {
            self
                .onChange(of: dashboardStore.state.graph.xScrollPosition) { _, newPosition in
                    viewModel.updateScrollPosition(to: newPosition)
                }
                        .onChange(of: dashboardStore.state.graph.isScrolling) { _, isScrolling in
            viewModel.isScrolling = isScrolling
            // Immediately clear local selection when scrolling starts to remove crosshair and label
            if isScrolling {
                localSelectedXValue.wrappedValue = nil
                // Also clear the view model's selection state immediately
                viewModel.clearSelection()
            }
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            // Clear local selection when period changes (similar to scrolling behavior)
            localSelectedXValue.wrappedValue = nil
            viewModel.clearSelection()
        }
                // CRITICAL: Sync Y-axis domain and ticks from dashboard store cache
                .onChange(of: dashboardStore.state.graph.cachedYAxisDomain) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
                .onChange(of: dashboardStore.state.graph.cachedYAxisTicks) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
        } else {
            self
        }
    }
}

#Preview {
    BaseGraphView(
        viewModel: WeekSectionViewModel(),
        dashboardStore: DashboardStore()
    )
    .frame(height: 265)
    .padding()
}
