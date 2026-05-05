//
//  BaseGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import SwiftUI
import Charts

/// Cached `Calendar.current` for the chart render path. Reading `Calendar.current`
/// per body recompute triggers `_LocaleICU.minimumDaysInFirstWeek.getter` and
/// related ICU lookups that show up in scroll-hang call stacks. Hoist to file
/// scope so every body recompute reuses the same instance.
private let renderCalendar = Calendar.current

/// Builds the `chartXAxis` grid-tick set. Extracted out of the inline closure
/// inside `View.conditionalModifiers` so the calendar-arithmetic loop runs only
/// when the inputs change (and shows up as a discrete leaf in time-profiler
/// attribution rather than buried under `View.conditionalModifiers`).
@inline(__always)
private func computeGridTicks(
    nonLastTicks: [Date],
    timePeriod: TimePeriod
) -> [Date] {
    guard timePeriod == .month, !nonLastTicks.isEmpty else {
        return nonLastTicks
    }
    let calendar = renderCalendar
    let sortedTicks = nonLastTicks.sorted()
    guard let firstTick = sortedTicks.first,
          let lastTick = sortedTicks.last else {
        return nonLastTicks
    }

    // Ensure month starts are always present as grid ticks so the solid
    // month-start line appears for every visible month.
    var monthStartTicks: [Date] = []
    var currentMonthStart = calendar.dateInterval(of: .month, for: firstTick)?.start ?? firstTick
    while currentMonthStart <= lastTick {
        let monthStartNoon = calendar.date(bySettingHour: 12, minute: 0, second: 0, of: currentMonthStart) ?? currentMonthStart
        monthStartTicks.append(monthStartNoon)
        guard let next = calendar.date(byAdding: .month, value: 1, to: currentMonthStart) else { break }
        currentMonthStart = next
    }
    // Deduplicate by calendar day to avoid double lines when two ticks
    // represent the same day with different time components.
    let combined = nonLastTicks + monthStartTicks
    var uniqueByDay: [Date] = []
    var seenDays: Set<Date> = []
    for tick in combined.sorted() {
        let day = calendar.startOfDay(for: tick)
        if seenDays.insert(day).inserted {
            uniqueByDay.append(tick)
        }
    }
    return uniqueByDay
}

/// Base graph view that provides common chart rendering functionality for all time periods
/// Eliminates code duplication across WeekGraphView, MonthGraphView, YearGraphView, and TotalGraphView
struct BaseGraphView<ViewModel: SectionViewModelProtocol & Equatable>: View, Equatable {

    // MARK: - Dependencies
    @ObservedObject var viewModel: ViewModel
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    // MARK: - Local State
    @State private var localSelectedXValue: Date?
    // Enable Y-axis animation only after first render to avoid blank-first-frame
    @State private var enableYAxisAnimation: Bool = false
    // Scroll position debouncing
    @State private var scrollUpdateWorkItem: DispatchWorkItem?

    // MARK: - Throttling State (Performance Optimization)
    /// Last time updateCachedChartData was called
    @State private var lastCacheUpdateTime: Date = .distantPast
    /// Minimum interval between cache updates during scroll (50ms)
    private let cacheUpdateThrottle: TimeInterval = 0.05
    /// Work item for delayed/debounced cache updates
    @State private var cacheUpdateWorkItem: DispatchWorkItem?

    // MARK: - Scroll End Transition State
    /// Tracks if we're in post-scroll transition (to disable animations)
    @State private var isInScrollEndTransition: Bool = false
    /// Counter that increments on scroll end to force chart identity change
    @State private var chartRebuildToken: Int = 0
    /// Tracks previous Y-axis domain to detect domain-only changes
    @State private var previousYAxisDomain: ClosedRange<Double>?
    /// Tracks previous data hash to detect data changes
    @State private var previousDataHash: Int?
    /// Flag indicating if current change is domain-only (no data change)
    @State private var isDomainChangeOnly: Bool = false

    // MARK: - Cached Chart Data (Performance Optimization)
    @State private var cachedChartPoints: [GraphSeries] = []
    @State private var cachedGroupedPoints: [String: [GraphSeries]] = [:]
    @State private var lastDataHash: Int = 0
    @State private var cachedPlottedPoints: [String: [PlottedGraphSeries]] = [:]

    // MARK: - Cached Labels (Performance Optimization)
    @State private var cachedYAxisLabels: [Double: String] = [:]
    @State private var cachedXAxisLabels: [Date: String] = [:]

    // MARK: - Consolidated Change Detection (Performance Optimization)
    /// Tracks previous values for change detection to avoid redundant updates
    @State private var lastDataChangeSignature: Int = 0
    @State private var lastSettingsChangeSignature: Int = 0

    /// Combined signature for data-affecting properties
    /// When this changes, we need to refresh data and update chart
    private var dataChangeSignature: Int {
        var hasher = Hasher()
        hasher.combine(dashboardStore.continuousOperations.count)
        hasher.combine(dashboardStore.ui.selectedMetricLabel)
        return hasher.finalize()
    }

    /// Combined signature for settings-affecting properties
    /// When this changes, we need to update formatting and labels
    private var settingsChangeSignature: Int {
        var hasher = Hasher()
        hasher.combine(dashboardStore.currentUnit.rawValue)
        hasher.combine(dashboardStore.isWeightlessModeEnabled)
        return hasher.finalize()
    }

    // MARK: - Configuration
    private var yAxisLabelWidth: CGFloat {
        if viewModel.chartOperations.isEmpty && viewModel.timePeriod != .total {
            return 30
        }
        return 40
    }
    private let goalChipTrailingPadding: CGFloat = 20
    private var isScrollable: Bool {
        viewModel.hasXAxis
    }

    // MARK: - Visibility Helpers
    private var shouldShowYAxisLabels: Bool {
        // Show labels if there are entries regardless of goal presence
        if !viewModel.chartOperations.isEmpty { return true }
        // No entries: hide labels only when goal is not set
        let goal = viewModel.goalWeight
        return goal != nil
    }

    // MARK: - Coordinated Animation
    /// Computes the appropriate animation for chart updates
    /// Ensures line and point marks animate together (or not at all)
    private var coordinatedChartAnimation: Animation? {
        // During scrolling: no animation
        if viewModel.isScrolling {
            return nil
        }
        // During scroll-end transition: no animation (data is settling)
        if isInScrollEndTransition {
            return nil
        }
        // Domain-only changes: no animation to prevent metrics from elongating unnaturally
        // Metrics should only animate when actual data changes, not when Y-axis domain recalculates
        if isDomainChangeOnly {
            return nil
        }
        // Normal state: use standard chart animation if enabled
        if enableYAxisAnimation && viewModel.shouldAnimateChartData {
            return .easeInOut(duration: 0.25)
        }
        // Y-axis animation only
        if enableYAxisAnimation {
            return .easeInOut(duration: 0.3)
        }
        return nil
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
        hasher.combine(dashboardStore.ui.selectedMetricLabel)
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
                    // Disable ALL animations during scroll and scroll-end transition
                    if viewModel.isScrolling || isInScrollEndTransition {
                        t.animation = nil
                    }
                }
                // Conditional chart modifiers based on scrollability
                .conditionalModifiers(
                    isScrollable: isScrollable,
                    viewModel: viewModel,
                    localSelectedXValue: $localSelectedXValue,
                    dashboardStore: dashboardStore,
                    theme: theme,
                    getCachedXAxisLabel: getCachedXAxisLabel
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
                // Coordinated animation for line and point marks
                // - During scroll: no animation
                // - During scroll-end transition: no animation (data settling)
                // - Domain-only changes: no animation (prevents metrics from elongating unnaturally)
                // - Normal state: standard animations
                .animation(coordinatedChartAnimation, value: viewModel.yAxisDomain)
                .animation(coordinatedChartAnimation, value: seriesAnimationToken)
                .animation(coordinatedChartAnimation, value: dashboardStore.ui.selectedMetricLabel)
                .animation(.none, value: viewModel.scrollPosition) // Never animate scroll position
                .animation(.none, value: viewModel.isScrolling) // Never animate scrolling state changes
                .conditionalTouchModifiers(
                    isScrollable: isScrollable,
                    localSelectedXValue: $localSelectedXValue,
                    dashboardStore: dashboardStore
                )

                // Selection callout overlay
                if let selectedDate = (viewModel.selectedDate ?? viewModel.dashboardStore?.graph.selectedXValue),
                   let displayWeight = viewModel.displayWeight,
                   viewModel.showCrosshair {
                    selectionCallout(for: selectedDate, weight: displayWeight)
                }

                // Goal chip overlay: show when goal is set (non-nil)
                // In weightless mode, goal of 0 is valid (maintain anchor weight)
                if viewModel.goalWeight != nil {
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
            // Precompute all labels to avoid state mutation during rendering
            precomputeLabels()
            // Flip on animation after first frame so the initial mount does not animate
            DispatchQueue.main.async { enableYAxisAnimation = true }

            // Force chart to sync with the initial scroll position after configuration
            if isScrollable {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    // Force the chart binding to update by triggering a small change and then setting the correct position
                    let targetPosition = viewModel.scrollPosition
                    // Temporarily set to a slightly different position to force binding update
                    viewModel.scrollPosition = targetPosition.addingTimeInterval(0.001)
                    // Then immediately set to the correct position
                    DispatchQueue.main.async {
                        viewModel.scrollPosition = targetPosition
                    }
                }
            }
        }
        .onDisappear {
            // Cancel any pending scroll updates to prevent memory leaks
            scrollUpdateWorkItem?.cancel()
            scrollUpdateWorkItem = nil
            // Cancel any pending cache updates
            cacheUpdateWorkItem?.cancel()
            cacheUpdateWorkItem = nil
        }
        // Track scroll end transition to disable animations during Y-axis recalculation
        .onChange(of: viewModel.isScrolling) { oldValue, newValue in
            // Detect scroll end (was scrolling, now not)
            if oldValue && !newValue {
                // Enter transition state - disable animations briefly while scroll state settles
                isInScrollEndTransition = true
                chartRebuildToken += 1

                // Exit transition state quickly - Y-axis updates at 0.6s will animate smoothly
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.005) {
                    isInScrollEndTransition = false
                }
            }
        }
        // PERFORMANCE: Consolidated data change handler
        // Combines: continuousOperations count, selectedMetricLabel
        // Reduces from 4 separate onChange handlers to 2
        .onChange(of: dataChangeSignature) { _, newSignature in
            guard newSignature != lastDataChangeSignature else { return }
            lastDataChangeSignature = newSignature

            // Refresh data and invalidate caches
            viewModel.refreshData()
            viewModel.invalidateCache()
            viewModel.invalidateXAxisCache()
            // Update local cache since data changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
                self.invalidateLabelCaches()
                self.precomputeLabels()
            }
        }
        // PERFORMANCE: Consolidated settings change handler
        // Combines: currentUnit, isWeightlessModeEnabled
        .onChange(of: settingsChangeSignature) { _, newSignature in
            guard newSignature != lastSettingsChangeSignature else { return }
            lastSettingsChangeSignature = newSignature

            // ViewModel will update store's Y-axis cache and invalidate its own cache
            viewModel.handleSettingsChange()

            // Update local cache since display values changed
            DispatchQueue.main.async {
                self.updateCachedChartData()
                self.invalidateLabelCaches()
                self.precomputeLabels()
            }
        }
        // Rebuild cached points when Y-axis domain or ticks change so normalized metric points
        // are re-plotted against the latest domain
        .onChange(of: viewModel.yAxisDomain) { oldDomain, newDomain in
            // Check if this is a domain-only change (domain changed but data hash didn't)
            // This prevents metrics from animating/stretching when only Y-axis domain recalculates
            let wasDomainChangeOnly = previousYAxisDomain != nil &&
                                     previousYAxisDomain != newDomain &&
                                     lastDataHash == (previousDataHash ?? 0)

            // Set flag synchronously so transaction modifier can use it
            isDomainChangeOnly = wasDomainChangeOnly
            previousYAxisDomain = newDomain

            DispatchQueue.main.async {
                // Use throttled update to prevent excessive updates during scroll
                self.updateCachedChartDataThrottled()
                // Clear Y-axis label cache since domain change affects tick values
                self.cachedYAxisLabels.removeAll()
                // Reset flag after a brief delay to allow transaction to complete
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    self.isDomainChangeOnly = false
                }
            }
        }
        // Conditional scroll position syncing
        .conditionalScrollSyncing(
            isScrollable: isScrollable,
            viewModel: viewModel,
            dashboardStore: dashboardStore,
            localSelectedXValue: $localSelectedXValue
        )
        .graphViewStyle(canAddPadding: !viewModel.hasXAxis, canAddTrailingPadding: !viewModel.chartOperations.isEmpty)
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
        let xAxisHeight: CGFloat = 18
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
        // Use cached grouped data with render-time filtering
        // This ensures ALL visible points are shown while limiting buffer points
        ForEach(Array(cachedPlottedPoints.keys), id: \.self) { seriesName in
            if let seriesPoints = cachedPlottedPoints[seriesName] {
                // Filter to visible + downsampled buffer for this series
                let pointsToRender = getPointsToRender(from: seriesPoints)
                chartContentForSeries(seriesName: seriesName, seriesPoints: pointsToRender)
            }
        }
    }

    @ChartContentBuilder
    private func chartContentForSeries(seriesName: String, seriesPoints: [PlottedGraphSeries]) -> some ChartContent {
        ForEach(seriesPoints) { plottedPoint in
            let point = plottedPoint.original
            let xDate = plottedPoint.xDate  // Use precomputed

            // Clamp value to the Y-axis domain so marks never overflow outside
            // the plot area into the x-axis region during domain transitions.
            let domainLower = viewModel.yAxisDomain.lowerBound
            let domainUpper = viewModel.yAxisDomain.upperBound
            let clampedValue = min(max(point.value, domainLower), domainUpper)
            let isWithinDomain = point.value >= domainLower && point.value <= domainUpper

            // Only enlarge the point that exactly matches the VM's selected date
            let vmSelected = viewModel.selectedDate
            let isThisPointSelected = viewModel.showCrosshair && (vmSelected != nil && xDate == vmSelected!)

            // Check if point is outside the active month interval (should be greyed out)
            let isOutsideMonthInterval = isPointOutsideActiveMonth(date: point.date)

            // Line color stays consistent (SwiftUI Charts applies color to entire interpolated line)
            let lineColor = point.series == DashboardStrings.weight
                ? theme.actionPrimary
                : theme.actionSecondary

            // Point color is greyed out if outside the active month
            let pointColor = point.series == DashboardStrings.weight
                ? (isOutsideMonthInterval ? theme.actionPrimaryDisabled : theme.actionPrimary)
                : (isOutsideMonthInterval ? theme.actionSecondaryDisabled : theme.actionSecondary)

            // Line mark — uses clamped value so the line stops at the domain boundary
            LineMark(
                x: .value("Date", xDate),
                y: .value(point.series, clampedValue),
                series: .value("Series", point.series)
            )
            .foregroundStyle(lineColor)
            .interpolationMethod(.monotone)
            .lineStyle(StrokeStyle(lineWidth: viewModel.lineWidth))

            // Visible point mark — only shown when within domain to avoid
            // dots sitting on the axis boundary
            PointMark(
                x: .value("Date", xDate),
                y: .value(point.series, isWithinDomain ? point.value : clampedValue)
            )
            .symbolSize(isWithinDomain ? viewModel.pointArea(isSelected: isThisPointSelected) : 0)
            .foregroundStyle(pointColor)
        }
    }

    /// Checks if a point's date falls outside the active month interval.
    /// Returns true only when in month period with a full month visible, not scrolling, and the point is outside that month.
    private func isPointOutsideActiveMonth(date: Date) -> Bool {
        // Don't grey out points while scrolling
        guard !viewModel.isScrolling else { return false }

        guard let monthInterval = dashboardStore.activeMonthInterval else {
            return false
        }
        // Check if date is before month start or on/after month end
        return date < monthInterval.start || date >= monthInterval.end
    }

    @ChartContentBuilder
    private var crosshairContent: some ChartContent {
        if let selectedDate = (viewModel.selectedDate ?? viewModel.dashboardStore?.graph.selectedXValue), viewModel.showCrosshair {
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
                    CachedYAxisLabel(
                        text: getCachedYAxisLabel(doubleValue),
                        color: theme.textSubheading,
                        width: yAxisLabelWidth,
                        isVisible: shouldShowYAxisLabels
                    )
                    .equatable()
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
        if let goalWeight = viewModel.goalWeight, viewModel.chartFrame.height > 0 {
            let goalPosition = viewModel.getGoalChipPosition()

            goalWeightChip(goalWeight)
                .position(
                    x: viewModel.chartFrame.width > 0 ? viewModel.chartFrame.width - goalChipTrailingPadding : 320,
                    y: goalPosition.yPosition
                )
                .animation(coordinatedChartAnimation, value: goalPosition.yPosition)
        } else {
            EmptyView()
        }
    }

    // MARK: - Goal Chip UI

    @ViewBuilder
    private func goalWeightChip(_ value: Double) -> some View {
        // Round value for display, then format for weightless sign semantics
        let rounded = viewModel.dashboardStore?.roundedGoalWeight(value) ?? value.rounded(.toNearestOrAwayFromZero)
        let label: String = {
            if let store = viewModel.dashboardStore {
                return store.formatWeightDisplayText(rounded)
            } else {
                return getCachedYAxisLabel(rounded)
            }
        }()
        Text(label)
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
            // Store ALL points in cache - filtering happens during render
            cachedChartPoints = newData

            // Pre-group, sort, and precompute xDates
            let grouped = Dictionary(grouping: cachedChartPoints) { $0.series }
            cachedGroupedPoints = grouped.mapValues { seriesPoints in
                seriesPoints.sorted { $0.date < $1.date }
            }

            // Precompute plotted dates for ALL points
            cachedPlottedPoints = cachedGroupedPoints.mapValues { points in
                points.map { point in
                    PlottedGraphSeries(original: point, xDate: viewModel.plotXDate(for: point.date))
                }
            }
            previousDataHash = lastDataHash
            lastDataHash = newHash
        }
    }

    /// Returns points to render: ALL visible points + downsampled buffer
    /// Called during render to ensure visible window always shows all points
    private func getPointsToRender(from points: [PlottedGraphSeries]) -> [PlottedGraphSeries] {
        // For small datasets, render everything (already sorted)
        guard points.count > 200 else { return points }

        let visibleStart = viewModel.scrollPosition
        let visibleEnd = viewModel.scrollPosition.addingTimeInterval(viewModel.visibleDomainLength)

        // Separate into visible and buffer regions
        var visible: [PlottedGraphSeries] = []
        var leftBuffer: [PlottedGraphSeries] = []
        var rightBuffer: [PlottedGraphSeries] = []

        for point in points {
            let date = point.original.date
            if date >= visibleStart && date <= visibleEnd {
                visible.append(point)
            } else if date < visibleStart {
                leftBuffer.append(point)
            } else {
                rightBuffer.append(point)
            }
        }

        // Keep ALL visible points
        var result = visible

        // Downsample buffers to ~30 points each for line continuity
        let maxBufferPoints = 30

        if leftBuffer.count > maxBufferPoints {
            let step = leftBuffer.count / maxBufferPoints
            var sampled: [PlottedGraphSeries] = []
            for i in stride(from: 0, to: leftBuffer.count, by: step) {
                sampled.append(leftBuffer[i])
            }
            // Always include the point closest to visible area
            if let last = leftBuffer.last, sampled.last?.original.date != last.original.date {
                sampled.append(last)
            }
            result.append(contentsOf: sampled)
        } else {
            result.append(contentsOf: leftBuffer)
        }

        if rightBuffer.count > maxBufferPoints {
            let step = rightBuffer.count / maxBufferPoints
            var sampled: [PlottedGraphSeries] = []
            // Always include the point closest to visible area
            if let first = rightBuffer.first {
                sampled.append(first)
            }
            for i in stride(from: step, to: rightBuffer.count, by: step) {
                sampled.append(rightBuffer[i])
            }
            result.append(contentsOf: sampled)
        } else {
            result.append(contentsOf: rightBuffer)
        }

        // CRITICAL: Sort by date so chart draws lines correctly
        return result.sorted { $0.original.date < $1.original.date }
    }

    /// Invalidates cache when data changes externally
    private func invalidateCache() {
        cachedChartPoints = []
        cachedGroupedPoints = [:]
        lastDataHash = 0
    }

    // MARK: - Throttled Updates (Performance Optimization)

    /// Throttled version of updateCachedChartData.
    /// Limits how often cache updates run during rapid changes (e.g., scrolling).
    private func updateCachedChartDataThrottled() {
        let now = Date()

        // If enough time has passed, update immediately
        guard now.timeIntervalSince(lastCacheUpdateTime) > cacheUpdateThrottle else {
            // Schedule a delayed update if not already scheduled
            scheduleDelayedCacheUpdate()
            return
        }

        lastCacheUpdateTime = now
        updateCachedChartData()
    }

    /// Schedules a delayed cache update, cancelling any previous pending update.
    /// Ensures cache eventually updates even during rapid changes.
    private func scheduleDelayedCacheUpdate() {
        cacheUpdateWorkItem?.cancel()

        let workItem = DispatchWorkItem { [self] in
            self.updateCachedChartData()
            self.precomputeLabels()
        }
        cacheUpdateWorkItem = workItem

        DispatchQueue.main.asyncAfter(deadline: .now() + cacheUpdateThrottle, execute: workItem)
    }

    // MARK: - Animation Token
    /// Lightweight hash token that changes when the cached chart data changes,
    /// so we can animate line updates even when the Y-axis domain is unchanged.
    private var seriesAnimationToken: Int {
        if viewModel.isScrolling { return 0 } // no animations during scroll, skip work
        // Use the cached data hash for animation token since it only changes when data actually changes
        return lastDataHash
    }

    // MARK: - Label Caching Helpers

    /// Returns cached Y-axis label (read-only during rendering)
    private func getCachedYAxisLabel(_ value: Double) -> String {
        return cachedYAxisLabels[value] ?? dashboardStore.formatYAxisTickLabel(value)
    }

    /// Returns cached X-axis label (read-only during rendering)
    private func getCachedXAxisLabel(_ date: Date) -> String? {
        return cachedXAxisLabels[date] ?? viewModel.formatXAxisLabel(for: date)
    }

    /// Precomputes and caches all labels before rendering
    private func precomputeLabels() {
        // Cache Y-axis labels
        for tick in viewModel.yAxisTicks {
            if cachedYAxisLabels[tick] == nil {
                cachedYAxisLabels[tick] = dashboardStore.formatYAxisTickLabel(tick)
            }
        }

        // Cache goal weight label if present (non-nil)
        // In weightless mode, goal of 0 is valid (maintain anchor weight)
        if let goalWeight = viewModel.goalWeight, cachedYAxisLabels[goalWeight] == nil {
            cachedYAxisLabels[goalWeight] = dashboardStore.formatYAxisTickLabel(goalWeight)
        }

        // Cache X-axis labels for scrollable views
        if isScrollable {
            for date in viewModel.xAxisValues {
                if cachedXAxisLabels[date] == nil {
                    cachedXAxisLabels[date] = viewModel.formatXAxisLabel(for: date)
                }
            }
        }
    }

    /// Clears label caches when formatting context changes
    private func invalidateLabelCaches() {
        cachedYAxisLabels.removeAll()
        cachedXAxisLabels.removeAll()
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
                dashboardStore.graph.chartHeight = h
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
        dashboardStore: DashboardStore,
        theme: AppColors.Palette,
        getCachedXAxisLabel: @escaping (Date) -> String?
    ) -> some View {
        if isScrollable {
            self
                .chartXVisibleDomain(length: viewModel.visibleDomainLength)
                // When there are no operations (empty-state), explicitly pin the
                // X-axis domain to the current period tick range so labels render
                // left-to-right (sun → mon → … → sat for week).
                .conditionalEmptyDomain(viewModel: viewModel)
                .chartScrollableAxes(.horizontal)
                .chartScrollPosition(x: Binding(
                    get: {
                        viewModel.scrollPosition
                    },
                    set: { (newPosition: Date?) in
                        guard let newPosition = newPosition else { return }
                        // Throttling is handled in handleScrollPositionChange
                        viewModel.handleScrollPositionChange(newPosition)
                    }
                ))
                .chartXAxis {
                    let allTicks = viewModel.xAxisValues
                    let nonLastTicks = Array(allTicks.dropLast())
                    let gridTicks = computeGridTicks(
                        nonLastTicks: nonLastTicks,
                        timePeriod: viewModel.timePeriod
                    )
                    // Use ticks as-is; we keep Saturday visible via a phantom extra tick in data
                    let adjustedLabelTicks: [Date] = allTicks
                    // Grid lines and ticks for all but the last value (to avoid the trailing thick edge)
                    AxisMarks(values: gridTicks) { value in
                        if let date = value.as(Date.self), viewModel.shouldShowSolidLine(for: date) {
                            // Solid line for start of week/month/year
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                .foregroundStyle(theme.statusIconSecondaryDisabled)
                            // For month-start lines, show the tick below X-axis only when
                            // the 1st day of month is also Sunday.
                            if viewModel.timePeriod == .month {
                                let comps = renderCalendar.dateComponents([.day, .weekday], from: date)
                                let isMonthStartSunday = (comps.day == 1 && comps.weekday == 1)
                                if isMonthStartSunday {
                                    AxisTick(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                        .foregroundStyle(theme.statusIconSecondaryDisabled)
                                } else {
                                    AxisTick().foregroundStyle(.clear)
                                }
                            } else {
                                AxisTick(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                    .foregroundStyle(theme.statusIconSecondaryDisabled)
                            }
                        } else {
                            // Default dotted line for other grid lines
                            AxisGridLine()
                            AxisTick()
                        }
                    }

                    // Labels for all tick values
                    AxisMarks(values: adjustedLabelTicks) { value in
                        if viewModel.timePeriod == .month {
                            // Hide default tick/gridline for month label marks so
                            // month-start solid lines do not appear below the X-axis.
                            AxisGridLine().foregroundStyle(.clear)
                            AxisTick().foregroundStyle(.clear)
                        }
                        AxisValueLabel {
                            if let date = value.as(Date.self),
                               let labelString = getCachedXAxisLabel(date) {
                                if viewModel.timePeriod == .month {
                                    CachedXAxisLabel(text: labelString, color: theme.textSubheading)
                                        .equatable()
                                        .fixedSize(horizontal: true, vertical: false)
                                        .padding(.horizontal, 2)
                                        .background(theme.textInverse)
                                } else {
                                    CachedXAxisLabel(text: labelString, color: theme.textSubheading)
                                        .equatable()
                                }
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
                        // Disable selection when there's no data
                        if viewModel.chartOperations.isEmpty {
                            localSelectedXValue.wrappedValue = nil
                            viewModel.clearSelection()
                            return
                        }
                        // Only handle selection if not actively scrolling
                        guard !viewModel.isScrolling else { return }
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
                ))
                // Immediate tap selection - bypasses scroll/selection disambiguation delay
                .chartGesture { proxy in
                    SpatialTapGesture()
                        .onEnded { value in
                            guard !viewModel.chartOperations.isEmpty else { return }
                            guard !viewModel.isScrolling else { return }
                            if let date: Date = proxy.value(atX: value.location.x) {
                                localSelectedXValue.wrappedValue = date
                                viewModel.handleChartSelection(at: date)
                                if viewModel.showCrosshair {
                                    let dateToSend = viewModel.preferredSelectedDate ?? date
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

                        // Disable selection when there's no data
                        if viewModel.chartOperations.isEmpty {
                            localSelectedXValue.wrappedValue = nil
                            viewModel.clearSelection()
                            return
                        }
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
                // Immediate tap selection - bypasses scroll/selection disambiguation delay
                .chartGesture { proxy in
                    SpatialTapGesture()
                        .onEnded { value in
                            guard !viewModel.chartOperations.isEmpty else { return }
                            if let date: Date = proxy.value(atX: value.location.x) {
                                localSelectedXValue.wrappedValue = date
                                viewModel.handleChartSelection(at: date)
                                if viewModel.showCrosshair {
                                    let dateToSend = viewModel.preferredSelectedDate ?? date
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
        }
    }

    /// Applies a fixed X-axis domain using the period tick range when there are no operations.
    /// This ensures labels render left-to-right (e.g., Sun → Sat) with no plotted data.
    @ViewBuilder
    func conditionalEmptyDomain<ViewModel: SectionViewModelProtocol>(viewModel: ViewModel) -> some View {
        if viewModel.hasXAxis && viewModel.chartOperations.isEmpty {
            let ticks = viewModel.xAxisValues.sorted()
            if let first = ticks.first, let last = ticks.last, first < last {
                self.chartXScale(domain: first...last)
            } else {
                self
            }
        } else {
            self
        }
    }

    @ViewBuilder
    func conditionalPreferenceChange(isScrollable: Bool, dashboardStore: DashboardStore) -> some View {
        if isScrollable {
            self.onPreferenceChange(AnnotationHeightKey.self) { height in
                // Idempotent: short-circuit when the value hasn't changed. Without this,
                // `onPreferenceChange` fires on every BaseGraphView body recompute,
                // mutates `graph.annotationHeight`, and re-invalidates the body — the
                // same feedback-loop pattern Step 6 fixed in `SegmentedButtonView`.
                // See history doc §3.13 / Step 8.
                guard dashboardStore.graph.annotationHeight != height else { return }
                dashboardStore.graph.annotationHeight = height
            }
        } else {
            self
        }
    }

    @ViewBuilder
    func conditionalTouchModifiers(
        isScrollable: Bool,
        localSelectedXValue: Binding<Date?>,
        dashboardStore: DashboardStore
    ) -> some View {
        if isScrollable {
            self
                .modifier(
                    ScrollDetectionModifier(
                        dashboardStore: dashboardStore,
                        selectedXValue: localSelectedXValue
                    )
                )
        } else {
            self
        }
    }

    /// Returns the appropriate chart scroll target behavior based on the time period
    /// - Parameter period: The time period for the chart
    /// - Returns: PagedChartScrollBehavior with paging support + date alignment
    func getChartScrollBehavior(for period: TimePeriod) -> PagedChartScrollBehavior {
        switch period {
        case .week:
            // For week view: align to start of week (Sunday)
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 12),
                majorAlignment: DateComponents(hour: 6, weekday: 1) // Sunday = 1
            )
        case .month:
            // For month view: align to start of month (1st day)
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 12),
                majorAlignment: DateComponents(day: 31, hour: 12)
            )
        case .year:
            // For year view: align strictly to month ticks (1st day, local noon)
            // so snapping always lands on month grid lines (e.g., Oct 2025, Nov 2025).
            return PagedChartScrollBehavior(
                matching: DateComponents(day: 1, hour: 12),
                majorAlignment: DateComponents(month: 1, day: 1, hour: 12)
            )
        case .total:
            // For total view: no specific alignment needed (non-scrollable)
            return PagedChartScrollBehavior(
                matching: DateComponents(hour: 0),
                majorAlignment: DateComponents(hour: 0)
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
                .onChange(of: dashboardStore.graph.xScrollPosition) { oldPosition, newPosition in
                    // Only sync if position actually changed (programmatic navigation)
                    // Skip if viewModel already has this position to avoid redundant updates
                    guard abs(newPosition.timeIntervalSince(viewModel.scrollPosition)) > 0.1 else { return }
                    viewModel.updateScrollPosition(to: newPosition)
                }
                .onChange(of: dashboardStore.graph.isScrolling) { oldValue, newValue in
                    viewModel.isScrolling = newValue
                    // Immediately clear local selection when scrolling starts to remove crosshair and label
                    if newValue {
                        localSelectedXValue.wrappedValue = nil
                        // Also clear the view model's selection state immediately
                        viewModel.clearSelection()
                    }
                }
                .onChange(of: dashboardStore.graph.selectedPeriod) { _, _ in
                    // Clear local selection when period changes (similar to scrolling behavior)
                    localSelectedXValue.wrappedValue = nil
                    viewModel.clearSelection()
                }
            // CRITICAL: Sync Y-axis domain and ticks from dashboard store cache
                .onChange(of: dashboardStore.graph.cachedYAxisDomain) { _, _ in
                    viewModel.syncYAxisFromStore()
                }
                .onChange(of: dashboardStore.graph.cachedYAxisTicks) { _, _ in
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
