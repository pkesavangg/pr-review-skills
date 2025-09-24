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
struct BaseGraphView<ViewModel: SectionViewModelProtocol & Equatable>: View, Equatable {
    
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
    // Enable Y-axis animation only after first render to avoid blank-first-frame
    @State private var enableYAxisAnimation: Bool = false
    // Scroll position debouncing
    @State private var scrollUpdateWorkItem: DispatchWorkItem?
    
    // MARK: - Cached Chart Data (Performance Optimization)
    @State private var cachedChartPoints: [GraphSeries] = []
    @State private var cachedGroupedPoints: [String: [GraphSeries]] = [:]
    @State private var lastDataHash: Int = 0
    @State private var cachedPlottedPoints: [String: [PlottedGraphSeries]] = [:]
    
    // MARK: - Configuration
    private let yAxisLabelWidth: CGFloat = 40
    private let goalChipTrailingPadding: CGFloat = 20
    private var isScrollable: Bool {
        viewModel.hasXAxis
    }
    
    // MARK: - Equatable Implementation
    static func == (lhs: BaseGraphView, rhs: BaseGraphView) -> Bool {
        // Only compare essential properties that should trigger re-renders
        let lhsHash = lhs.createViewModelHash()
        let rhsHash = rhs.createViewModelHash()
        return lhsHash == rhsHash
    }
    
    // MARK: - Local State (add these)
    @State private var lastChartFrame: CGRect = .zero
    @State private var lastChartHeight: CGFloat = .zero
    
    // Create a comprehensive hash of properties that affect rendering
    private func createViewModelHash() -> Int {
        var hasher = Hasher()
        hasher.combine(viewModel.yAxisTicks)
        hasher.combine(viewModel.yAxisDomain.lowerBound)
        hasher.combine(viewModel.yAxisDomain.upperBound)
        hasher.combine(viewModel.timePeriod.rawValue)
        hasher.combine(viewModel.goalWeight)
        hasher.combine(viewModel.showCrosshair)
        hasher.combine(viewModel.selectedDate?.timeIntervalSince1970 ?? 0)
        hasher.combine(dashboardStore.state.ui.selectedMetricLabel)
        return hasher.finalize()
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
                .chartScrollTargetBehavior(getChartScrollBehavior(for: viewModel.timePeriod))
                .transaction { t in
                    if viewModel.isScrolling { t.animation = nil }
                }
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
                    // Use a neutral view so we don't trigger style/layout side effects
                    Color.clear
                        .background(
                            GeometryReader { geo in
                                // 1) Do a one-time assignment on first appearance
                                Color.clear
                                    .task {
                                        assignHeightIfChanged(geo.size.height)
                                        assignFrameIfChanged(geo.frame(in: .local))
                                    }
                                // 2) Gate size changes
                                    .onChange(of: geo.size) { _, newSize in
                                        assignHeightIfChanged(newSize.height)
                                    }
                                // 3) Gate frame changes
                                    .onChange(of: geo.frame(in: .local)) { _, newFrame in
                                        assignFrameIfChanged(newFrame)
                                    }
                            }
                        )
                )
                .conditionalPreferenceChange(isScrollable: isScrollable, dashboardStore: dashboardStore)
                // Re-enable Chart-level animation after first frame to animate subsequent domain changes
                .animation(enableYAxisAnimation ? .easeInOut(duration: 0.3) : .none, value: viewModel.yAxisDomain)
                // Animate when series data changes (even if Y-axis domain/ticks stay the same)
                .animation((enableYAxisAnimation && viewModel.shouldAnimateChartData) ? .easeInOut(duration: 0.25) : .none, value: seriesAnimationToken)
                // Animate when switching the selected metric (weight vs other metric)
                .animation((enableYAxisAnimation && viewModel.shouldAnimateChartData) ? .easeInOut(duration: 0.25) : .none, value: dashboardStore.state.ui.selectedMetricLabel)
                .animation(.none, value: viewModel.scrollPosition) // Never animate scroll position
                .animation(.none, value: viewModel.isScrolling) // Never animate scrolling state changes
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
                if let selectedDate = (viewModel.selectedDate ?? viewModel.dashboardStore?.state.graph.selectedXValue),
                   let displayWeight = viewModel.displayWeight,
                   viewModel.showCrosshair {
                    selectionCallout(for: selectedDate, weight: displayWeight)
                }
                
                // Goal chip overlay
                if viewModel.goalWeight > 0 {
                    goalChipCallout()
                }
            }
        }
        .onAppear {
            viewModel.configure(with: dashboardStore)
            // Initialize cache in ViewModel (async to avoid publishing warnings)
            viewModel.updateCachedSeriesDataAsync()
            // Initialize local cache for chart rendering performance
            updateCachedChartData()
            // Flip on animation after first frame so the initial mount does not animate
            DispatchQueue.main.async { enableYAxisAnimation = true }
        }
        .onDisappear {
            // Cancel any pending scroll updates to prevent memory leaks
            scrollUpdateWorkItem?.cancel()
            scrollUpdateWorkItem = nil
        }
        .onChange(of: dashboardStore.continuousOperations) { _, _ in
            // ViewModel will invalidate cache in refreshData()
            viewModel.refreshData()
            // Update local cache since data changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
        }
        .onChange(of: dashboardStore.currentUnit) { _, _ in
            // ViewModel will invalidate cache in handleSettingsChange()
            viewModel.handleSettingsChange()
            // Update local cache since display values changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
        }
        .onChange(of: dashboardStore.isWeightlessModeEnabled) { _, _ in
            // ViewModel will invalidate cache in handleSettingsChange()
            viewModel.handleSettingsChange()
            // Update local cache since display values changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
        }
        .onChange(of: dashboardStore.state.ui.selectedMetricLabel) { _, _ in
            // Invalidate cache when selected metric changes (affects chart series)
            viewModel.invalidateCache()
            // Update local cache since series data changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
        }
        // Rebuild cached points when Y-axis domain or ticks change so normalized metric points
        // are re-plotted against the latest domain
        .onChange(of: viewModel.yAxisDomain) { _, _ in
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
        }
        .onChange(of: viewModel.yAxisTicks) { _, _ in
            DispatchQueue.main.async {
                self.updateCachedChartData()
            }
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
            let effectiveTick: Double = adjustedTick(tick)
            RuleMark(y: .value("YGrid", effectiveTick))
                .lineStyle(StrokeStyle(lineWidth: 1))
                .foregroundStyle(theme.statusIconSecondaryDisabled)
                .zIndex(-1)
        }
    }
    
    // Helper: Adjusts a tick value to avoid overlap with axis baselines
    private func adjustedTick(_ tick: Double) -> Double {
        guard viewModel.hasXAxis else { return tick }
        let lower = viewModel.yAxisDomain.lowerBound
        let upper = viewModel.yAxisDomain.upperBound
        let epsilon: Double = 1e-6
        let domainRange = upper - lower
        let xAxisHeight: CGFloat = viewModel.hasXAxis ? 18 : 0
        let availableHeight = max(1, viewModel.chartFrame.height - xAxisHeight)
        let onePointValue = domainRange / Double(availableHeight)
        // Only nudge the bottom-most tick when lower domain is negative.
        if abs(tick - lower) <= epsilon {
            return lower < 0 ? (tick + onePointValue) : tick
        }
        if abs(tick - upper) <= epsilon {
            return tick - onePointValue
        }
        return tick
    }
    
    @ChartContentBuilder
    private var xAxisGridLinesSolid: some ChartContent {
        let referenceDate = viewModel.hasXAxis ?
        viewModel.xAxisValues.last
        : viewModel.xAxisValues.first
        if let referenceDate = referenceDate, viewModel.hasXAxis {
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
        // Use cached grouped data to prevent re-creation of LineMark/PointMark on every scroll
        ForEach(Array(cachedPlottedPoints.keys.sorted()), id: \.self) { seriesName in
            if let seriesPoints = cachedPlottedPoints[seriesName] {
                chartContentForSeries(seriesName: seriesName, seriesPoints: seriesPoints)
            }
        }
    }
    
    @ChartContentBuilder
    private func chartContentForSeries(seriesName: String, seriesPoints: [PlottedGraphSeries]) -> some ChartContent {
        ForEach(seriesPoints) { plottedPoint in
            let point = plottedPoint.original
            let xDate = plottedPoint.xDate  // Use precomputed
            
            // Only enlarge the point that exactly matches the VM's selected date
            let vmSelected = viewModel.selectedDate
            let isThisPointSelected = viewModel.showCrosshair && (vmSelected != nil && xDate == vmSelected!)
            
            // Line mark
            LineMark(
                x: .value("Date", xDate),
                y: .value(point.series, point.value),
                series: .value("Series", point.series)
            )
            .foregroundStyle(point.series == DashboardStrings.weight
                             ? theme.actionPrimary
                             : theme.actionSecondary)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: viewModel.lineWidth))
            
            // Visible point mark
            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, point.value)
            )
            .symbolSize(viewModel.pointArea(isSelected: isThisPointSelected))
            .foregroundStyle(point.series == DashboardStrings.weight
                             ? theme.actionPrimary
                             : theme.actionSecondary)
        }
    }
    
    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedDate = (viewModel.selectedDate ?? viewModel.dashboardStore?.state.graph.selectedXValue), viewModel.showCrosshair {
            let xDate = viewModel.plotXDate(for: selectedDate)
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
                        .fontOpenSans(.subHeading2)
                        .multilineTextAlignment(.leading)
                        .fontWeight(.regular)
                        .monospacedDigit()
                        .foregroundColor(theme.textSubheading)
                        .frame(width: yAxisLabelWidth, alignment: .center)
                }
            }
        }
    }
    
    // MARK: - Selection Callout
    @ViewBuilder
    private func selectionCallout(for selectedDate: Date, weight: Double) -> some View {
        if let chartPosition = viewModel.getChartPosition(for: selectedDate, value: weight) {
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
        
        goalWeightChip(viewModel.goalWeight)
            .position(
                x: viewModel.chartFrame.width > 0 ? viewModel.chartFrame.width - goalChipTrailingPadding : 320,
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
            .fontOpenSans(.body3)
            .foregroundColor(theme.actionInverse)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(Capsule().fill(theme.statusSuccess))
    }
    
    // MARK: - Cache Management
    
    /// Updates cached chart data only when underlying data actually changes
    private func updateCachedChartData() {
        let newData = viewModel.getCachedSeriesData()
        // Create hash to detect actual data changes
        var hasher = Hasher()
        // Include Y-axis domain and ticks so metric line animations trigger when normalization changes
        hasher.combine(viewModel.yAxisDomain.lowerBound.bitPattern)
        hasher.combine(viewModel.yAxisDomain.upperBound.bitPattern)
        hasher.combine(viewModel.yAxisTicks.count)
        if let firstTick = viewModel.yAxisTicks.first {
            hasher.combine(firstTick.bitPattern)
        }
        if let lastTick = viewModel.yAxisTicks.last {
            hasher.combine(lastTick.bitPattern)
        }
        hasher.combine(newData.count)
        if !newData.isEmpty {
            // Sample key points for efficient hashing
            let indices = newData.count <= 5 ? Array(0..<newData.count) : [0, newData.count/4, newData.count/2, (3*newData.count)/4, newData.count-1]
            for i in indices {
                let point = newData[i]
                hasher.combine(point.date.timeIntervalSince1970.bitPattern)
                hasher.combine(point.value.bitPattern)
                hasher.combine(point.series)
            }
        }
        let newHash = hasher.finalize()
        
        // Only update cache if data actually changed
        if newHash != lastDataHash || cachedChartPoints.isEmpty {
            cachedChartPoints = newData
            
            // Pre-group, sort, and precompute xDates
            let grouped = Dictionary(grouping: cachedChartPoints) { $0.series }
            cachedGroupedPoints = grouped.mapValues { seriesPoints in
                seriesPoints.sorted { $0.date < $1.date }
            }
            
            // New: Precompute plotted dates
            cachedPlottedPoints = cachedGroupedPoints.mapValues { points in
                points.map { point in
                    PlottedGraphSeries(original: point, xDate: viewModel.plotXDate(for: point.date))
                }
            }
            lastDataHash = newHash
        }
    }
    
    /// Invalidates cache when data changes externally
    private func invalidateCache() {
        cachedChartPoints = []
        cachedGroupedPoints = [:]
        lastDataHash = 0
    }
    
    // MARK: - Animation Token
    /// Lightweight hash token that changes when the cached chart data changes,
    /// so we can animate line updates even when the Y-axis domain is unchanged.
    private var seriesAnimationToken: Int {
        if viewModel.isScrolling { return 0 } // no animations during scroll, skip work
        // Use the cached data hash for animation token since it only changes when data actually changes
        return lastDataHash
    }
    
    // MARK: - Helpers
    @inline(__always)
    private func assignFrameIfChanged(_ newFrame: CGRect) {
        // Round to avoid microscopic diffs that trigger endless updates
        let r = newFrame.integral   // or newFrame.standardized if you prefer
        if r != lastChartFrame {
            lastChartFrame = r
            viewModel.updateChartFrame(r)
        }
    }
    
    @inline(__always)
    private func assignHeightIfChanged(_ newHeight: CGFloat) {
        let h = round(newHeight) // avoid tiny float wiggles
        if h != lastChartHeight {
            lastChartHeight = h
            if isScrollable {
                dashboardStore.state.graph.chartHeight = h
            }
        }
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
                                // If the view-model decided there is no value at this position,
                                // do not show crosshair nor propagate a selection to the store.
                                if viewModel.showCrosshair {
                                    // Use view model's preferredSelectedDate if provided, else fallback to raw selection
                                    let dateToSend = viewModel.preferredSelectedDate ?? selectedDate
                                    Task {
                                        await dashboardStore.handleChartSelection(at: dateToSend)
                                    }
                                } else {
                                    // Clear any previous selection in the store
                                    Task {
                                        await dashboardStore.handleChartSelection(at: nil)
                                    }
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
                    // Reserve space for X-axis to keep chart height consistent with other sections
                    AxisMarks(position: .bottom) { _ in
                        // Hide grid/ticks but keep label height via an invisible label
                        AxisGridLine().foregroundStyle(.clear)
                        AxisTick().foregroundStyle(.clear)
                        AxisValueLabel {
                            Text("00")
                                .font(.caption)
                                .opacity(0) // invisible but reserves height
                        }
                    }
                }
                .chartXSelection(value: Binding(
                    get: { localSelectedXValue.wrappedValue },
                    set: { newValue in
                        localSelectedXValue.wrappedValue = newValue
                        viewModel.handleChartSelection(at: newValue)
                        
                        // Update dashboard store selection using snapped date when available
                        if let rawDate = newValue {
                            if viewModel.showCrosshair {
                                let dateToSend = viewModel.preferredSelectedDate ?? rawDate
                                Task {
                                    await dashboardStore.handleChartSelection(at: dateToSend)
                                }
                            } else {
                                Task { await dashboardStore.handleChartSelection(at: nil) }
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
                .modifier(
                    ScrollDetectionModifier(
                        dashboardStore: dashboardStore,
                        hasDetectedScrollInCurrentGesture: hasDetectedScrollInCurrentGesture,
                        selectedXValue: localSelectedXValue
                    )
                )
        } else {
            self
        }
    }
    
    /// Returns the appropriate chart scroll target behavior based on the time period
    /// - Parameter period: The time period for the chart
    /// - Returns: ChartScrollTargetBehavior configured for the specific period
    func getChartScrollBehavior(for period: TimePeriod) -> some ChartScrollTargetBehavior {
        switch period {
        case .week:
            // For week view: align to start of week (Sunday)
            return .valueAligned(
                matching: .init(hour: 12),
                majorAlignment: .matching(.init(hour: 12, weekday: 1)), // Sunday = 1
                limitBehavior: .automatic
            )
        case .month:
            // For month view: align to start of month (1st day)
            return .valueAligned(
                matching: .init(hour: 0),
                majorAlignment: .matching(.init(day: 1)),
                limitBehavior: .automatic
            )
        case .year:
            // For year view: align to start of year (January 1st)
            return .valueAligned(
                matching: .init(day: 1, hour: 0),
                majorAlignment: .matching(.init(month: 1, day: 1)),
                limitBehavior: .automatic
            )
        case .total:
            // For total view: no specific alignment needed (non-scrollable)
            return .valueAligned(
                matching: .init(hour: 0),
                majorAlignment: .matching(.init(hour: 0)),
                limitBehavior: .automatic
            )
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
