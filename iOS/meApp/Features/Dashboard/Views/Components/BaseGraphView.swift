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
    private let yAxisLabelWidth: CGFloat = 40
    private var isScrollable: Bool {
        viewModel.hasXAxis
    }
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                // Main Chart
                Chart {
                    yAxisGridLines
                    xAxisGridLinesSolid
                    yAxisBaseline
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
                    dashboardStore: dashboardStore,
                    theme: theme
                )
                .frame(height: 265)
                .frame(maxWidth: .infinity, minHeight: 240)
                .padding(.leading, 0)
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
                // Animate only when Y-axis domain changes to smoothly resize content
                .animation(.easeInOut(duration: 0.25), value: viewModel.yAxisDomain)
                
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
            // If this is the lowest tick and X-axis is visible, nudge it up by ~1pt
            // so it doesn't overlap with the axis baseline (which makes it look thicker).
            let effectiveTick: Double = {
                guard viewModel.hasXAxis else { return tick }
                let lower = viewModel.yAxisDomain.lowerBound
                let upper = viewModel.yAxisDomain.upperBound
                let epsilon: Double = 1e-6
                let domainRange = upper - lower
                let availableHeight = max(1, viewModel.chartFrame.height -  (viewModel.hasXAxis ? 18 : 0))
                let onePointValue = domainRange / Double(availableHeight)
                if abs(tick - lower) <= epsilon { return tick + onePointValue }
                if abs(tick - upper) <= epsilon { return tick - onePointValue }
                return tick
            }()
            RuleMark(y: .value("YGrid", effectiveTick))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusIconSecondaryDisabled)
                .zIndex(-1)
        }
    }
    
    @ChartContentBuilder
    private var xAxisGridLinesSolid: some ChartContent {
        let referenceDate = viewModel.hasXAxis ?
        viewModel.xAxisValues.last
        : viewModel.xAxisValues.first
        if let referenceDate = referenceDate {
            let domainLength = viewModel.visibleDomainLength
            let width = max(1, viewModel.chartFrame.width)
            let secondsPerPoint = domainLength / Double(width)
            let halfPointOffset = secondsPerPoint * 0.5
            let effectiveDate = referenceDate.addingTimeInterval(-halfPointOffset)
            
            RuleMark(x: .value("XGrid", effectiveDate))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusIconSecondaryDisabled)
        }
    }
    
    @ChartContentBuilder
    private var yAxisBaseline: some ChartContent {
        // Show baseline only for Total view (no X-axis)
        if !viewModel.hasXAxis {
            // Draw both leading (start) and trailing (end) vertical boundaries.
            // Nudge them inward by half a point in time to avoid edge clipping.
            let domain = viewModel.dateRange
            let domainLength = domain.upperBound.timeIntervalSince(domain.lowerBound)
            let width = max(1, viewModel.chartFrame.width)
            let secondsPerPoint = domainLength / Double(width)
            let halfPointOffset = secondsPerPoint * 0.5
            
            let leadingX = domain.lowerBound.addingTimeInterval(halfPointOffset)
            let trailingX = domain.upperBound.addingTimeInterval(-halfPointOffset)
            
            RuleMark(x: .value("YBaselineLeading", leadingX))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusIconSecondaryDisabled)
                .zIndex(-1)
            RuleMark(x: .value("YBaselineTrailing", trailingX))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusIconSecondaryDisabled)
                .zIndex(-1)
        }
    }
    
    @ChartContentBuilder
    private var chartSeries: some ChartContent {
        // Cache series data to prevent recalculation during scroll
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
            let xDate = viewModel.plotXDate(for: point.date)
            // Invisible tap target
            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, point.value)
            )
            .symbolSize(point.date == viewModel.selectedPoint?.date ? 200 : viewModel.pointSize)
            .foregroundStyle(.clear)
            
            // Line mark
            LineMark(
                x: .value("Date", xDate),
                y: .value(point.series, point.value),
                series: .value("Series", "\(point.series)-\(segmentIndex)")
            )
            .foregroundStyle(by: .value("Series", point.series))
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: viewModel.lineWidth))
            
            // Visible point mark
            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, point.value)
            )
            .symbolSize(viewModel.pointArea(isSelected: point.date == viewModel.selectedPoint?.date))
            .foregroundStyle(by: .value("Series", point.series))
        }
    }
    
    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedPoint = viewModel.selectedPoint, viewModel.showCrosshair {
            let xDate = viewModel.plotXDate(for: selectedPoint.date)
            RuleMark(x: .value("Date", xDate))
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
                        .monospacedDigit()
                        .foregroundColor(theme.textSubheading)
                        .frame(width: yAxisLabelWidth, alignment: .center)
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
            
            Text((viewModel.formatSelectedXAxisLabel() ?? "").lowercased())
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .position(
                    x: max(40, min(viewModel.chartFrame.width - (isScrollable ? 100 : 85), finalXPosition)), // Prevent cropping
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
        dashboardStore: DashboardStore,
        theme: AppColors.Palette
    ) -> some View {
        if isScrollable {
            self
                .chartXVisibleDomain(length: viewModel.visibleDomainLength * 1.05) // Add 5% extra length for trailing padding
                .chartScrollableAxes(.horizontal)
                .chartScrollPosition(x: Binding(
                    get: { viewModel.scrollPosition },
                    set: { newPosition in
                        // Debounce scroll position updates to prevent multiple updates per frame
                        DispatchQueue.main.async {
                            viewModel.handleScrollPositionChange(newPosition)
                        }
                    }
                ))
                .chartXAxis {
                    let allTicks = viewModel.xAxisValues
                    let nonLastTicks = Array(allTicks.dropLast())
                    // Use ticks as-is; we keep Saturday visible via a phantom extra tick in data
                    let adjustedLabelTicks: [Date] = allTicks
                    
                    // Grid lines and ticks for all but the last value (to avoid the trailing thick edge)
                    AxisMarks(values: nonLastTicks) { value in
                        if let date = value.as(Date.self), viewModel.shouldShowSolidLine(for: date) {
                            // Solid line for start of week/month/year
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                .foregroundStyle(theme.statusIconSecondaryDisabled)
                            AxisTick(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                .foregroundStyle(theme.statusIconSecondaryDisabled)
                        } else {
                            // Default dotted line for other grid lines
                            AxisGridLine()
                            AxisTick()
                        }
                    }
                    
                    // Labels for all tick values
                    AxisMarks(values: adjustedLabelTicks) { value in
                        AxisValueLabel {
                            if let date = value.as(Date.self),
                               let labelString = viewModel.formatXAxisLabel(for: date) {
                                Text(labelString)
                                    .font(.caption)
                                    .foregroundColor(theme.textSubheading)
                            }
                        }
                    }
                }
                // Add padding to left side of chart area
                .chartPlotStyle { plot in
                    if viewModel.isAtLeftBoundary {
                        plot.padding(.leading, .spacingXS)
                    } else {
                        plot
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
                    // Debounce to prevent multiple updates per frame
                    DispatchQueue.main.async {
                        viewModel.updateScrollPosition(to: newPosition)
                    }
                }
                .onChange(of: dashboardStore.state.graph.isScrolling) { _, isScrolling in
                    // Debounce to prevent multiple updates per frame
                    DispatchQueue.main.async {
                        viewModel.isScrolling = isScrolling
                        // Immediately clear local selection when scrolling starts to remove crosshair and label
                        if isScrolling {
                            localSelectedXValue.wrappedValue = nil
                            // Also clear the view model's selection state immediately
                            viewModel.clearSelection()
                        }
                    }
                }
                .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
                    // Clear local selection when period changes (similar to scrolling behavior)
                    DispatchQueue.main.async {
                        localSelectedXValue.wrappedValue = nil
                        viewModel.clearSelection()
                    }
                }
            // CRITICAL: Sync Y-axis domain and ticks from dashboard store cache
                .onChange(of: dashboardStore.state.graph.cachedYAxisDomain) { _, _ in
                    DispatchQueue.main.async {
                        viewModel.syncYAxisFromStore()
                    }
                }
                .onChange(of: dashboardStore.state.graph.cachedYAxisTicks) { _, _ in
                    DispatchQueue.main.async {
                        viewModel.syncYAxisFromStore()
                    }
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
