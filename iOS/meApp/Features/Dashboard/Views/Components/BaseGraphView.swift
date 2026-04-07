//
//  BaseGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

// This file intentionally aggregates common graph rendering logic for all time periods.
// Breaking it into smaller files would lead to significant code duplication and reduce maintainability.
// The function body length is justified by the complexity of chart rendering and interaction logic.
// swiftlint:disable type_body_length function_body_length

import Charts
import SwiftUI

/// Base graph view that provides common chart rendering functionality for all time periods
/// Eliminates code duplication across WeekGraphView, MonthGraphView, YearGraphView, and TotalGraphView
struct BaseGraphView<ViewModel: SectionViewModelProtocol>: View {
    // MARK: - Dependencies
    @ObservedObject var viewModel: ViewModel
    @ObservedObject var dashboardStore: DashboardStore
    /// When false, body renders nothing so inactive periods skip all chart work while
    /// keeping @State storage and @ObservedObject subscriptions alive.
    var isActive: Bool = true
    @Environment(\.appTheme) var theme
    @Environment(\.babyGrowthChartCalloutDateStyle) private var babyGrowthChartCalloutDateStyle

    // MARK: - Local State
    @State var localSelectedXValue: Date?
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
        let ops = dashboardStore.continuousOperations
        hasher.combine(ops.count)
        hasher.combine(dashboardStore.state.ui.selectedMetricLabel)
        // Sample a spread of entries to detect edits/replacements, not just count changes
        if !ops.isEmpty {
            let indices = ops.count <= 3
                ? Array(0..<ops.count)
                : [0, ops.count / 2, ops.count - 1]
            for i in indices {
                let op = ops[i]
                hasher.combine(op.entryTimestamp)
                hasher.combine(op.weight)
            }
        }
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

    private var selectedBabyProfile: BabyProfile? {
        dashboardStore.selectedBabyProfile
    }

    private var selectedBabyCrosshairDate: Date? {
        viewModel.selectedPoint?.date
            ?? viewModel.selectedDate
            ?? viewModel.dashboardStore?.state.graph.selectedXValue
    }

    private var allPlottedPoints: [PlottedGraphSeries] {
        cachedPlottedPoints.values.flatMap { $0 }
    }

    private var babySelectionPresentation: BabyGraphSelectionPresentation? {
        dashboardStore.graphManager.resolveBabySelectionPresentation(
            babyProfile: selectedBabyProfile,
            metric: dashboardStore.selectedBabyMetric,
            selectedCrosshairDate: selectedBabyCrosshairDate,
            plottedPoints: allPlottedPoints,
            plotXDate: { viewModel.plotXDate(for: $0) },
            currentUnit: dashboardStore.currentUnit,
            displayWeight: dashboardStore.displayManager?.displayWeight ?? viewModel.displayWeight
        )
    }

    private var selectedBabyPercentile: Int? {
        babySelectionPresentation?.percentile
    }

    private var babyChartContainerHeight: CGFloat { 497.14208984375 }
    private var chartContainerHeight: CGFloat {
        selectedBabyProfile != nil ? babyChartContainerHeight : 265
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
        hasher.combine(dashboardStore.state.ui.selectedMetricLabel)
        return hasher.finalize()
    }

    var body: some View {
        #if DEBUG
            Self._printChanges()
        #endif
        return conditionalScrollSyncing(
            ZStack {
                // Main Chart
                mainChartView

                // Selection callout overlay — baby charts use manager-resolved selection
                // presentation so the date label stays aligned with the active crosshair.
                if let rawDate = (viewModel.selectedDate ?? viewModel.dashboardStore?.state.graph.selectedXValue),
                   viewModel.showCrosshair {
                    let calloutDate = babySelectionPresentation?.crosshairDate ?? rawDate
                    if let selectedValue = selectionCalloutValue(for: calloutDate) {
                        selectionCallout(for: calloutDate, weight: selectedValue)
                    }
                }

                if selectedBabyProfile != nil,
                   viewModel.timePeriod != .total,
                   let selectedDate = selectedBabyCrosshairDate,
                   let percentile = selectedBabyPercentile,
                   let yValue = horizontalBabyCrosshairYValue {
                    babyPercentileCallout(
                        for: selectedDate,
                        value: yValue,
                        percentile: percentile
                    )
                }

                // Goal chip overlay: show when goal is set (non-nil) — hidden for BPM
                // In weightless mode, goal of 0 is valid (maintain anchor weight)
                if viewModel.goalWeight != nil && dashboardStore.productType != .bpm && selectedBabyProfile == nil {
                    goalChipCallout()
                }
            }
        )
        .onAppear {
            viewModel.configure(with: dashboardStore)
            // Initialize cache in ViewModel (async to avoid publishing warnings)
            viewModel.updateCachedSeriesDataAsync()
            // Initialize local cache for chart rendering performance
            updateCachedChartData()
            // Precompute all labels to avoid state mutation during rendering
            precomputeLabels()
            // Flip on animation after first frame so the initial mount does not animate
            Task { @MainActor in enableYAxisAnimation = true }

            // Force chart to sync with the initial scroll position after configuration
            if isScrollable {
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 100_000_000)
                    // Force the chart binding to update by triggering a small change and then setting the correct position
                    let targetPosition = viewModel.scrollPosition
                    // Temporarily set to a slightly different position to force binding update
                    viewModel.scrollPosition = targetPosition.addingTimeInterval(0.001)
                    await Task.yield()
                    // Then immediately set to the correct position
                    viewModel.scrollPosition = targetPosition
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
                Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 5_000_000)
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
            Task { @MainActor in
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
            Task { @MainActor in
                self.updateCachedChartData()
                self.invalidateLabelCaches()
                self.precomputeLabels()
            }
        }
        // Rebuild cached points when Y-axis domain or ticks change so normalized metric points
        // are re-plotted against the latest domain
        .onChange(of: viewModel.yAxisDomain) { _, newDomain in
            // Check if this is a domain-only change (domain changed but data hash didn't)
            // This prevents metrics from animating/stretching when only Y-axis domain recalculates
            let wasDomainChangeOnly = previousYAxisDomain != nil &&
                                     previousYAxisDomain != newDomain &&
                                     lastDataHash == (previousDataHash ?? 0)

            // Set flag synchronously so transaction modifier can use it
            isDomainChangeOnly = wasDomainChangeOnly
            previousYAxisDomain = newDomain

            Task { @MainActor in
                // Use throttled update to prevent excessive updates during scroll
                self.updateCachedChartDataThrottled()
                // Clear Y-axis label cache since domain change affects tick values
                self.cachedYAxisLabels.removeAll()
                // Reset flag after a brief delay to allow transaction to complete
                try? await Task.sleep(nanoseconds: 100_000_000)
                self.isDomainChangeOnly = false
            }
        }
        // Conditional scroll position syncing
        .graphViewStyle(
            canAddPadding: !viewModel.hasXAxis,
            canAddTrailingPadding: selectedBabyProfile == nil && !viewModel.chartOperations.isEmpty,
            height: chartContainerHeight
        )
    }

    // MARK: - Chart Content Builders

    @ChartContentBuilder
    private var yAxisGridLines: some ChartContent {
        if dashboardStore.isBabySelection && viewModel.hasXAxis {
            // Scrollable baby charts render their edge Y grid lines from the plot
            // overlay so the first/last rules don't get clipped at the plot bounds.
        } else {
        let ticksToRender = dashboardStore.isBabySelection ? boundaryYAxisTicks : viewModel.yAxisTicks

            ForEach(ticksToRender, id: \.self) { tick in
                // If this is the lowest tick and X-axis is visible, nudge it up by ~1pt
                // so it doesn't overlap with the axis baseline (which makes it look thicker).
                let effectiveTick: Double = adjustedTick(tick)
                RuleMark(y: .value("YGrid", effectiveTick))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(theme.statusIconSecondaryDisabled)
                    .zIndex(-1)
            }
        }
    }

    private var boundaryYAxisTicks: [Double] {
        BaseGraphViewCacheSupport.boundaryYAxisTicks(from: viewModel.yAxisTicks)
    }

    private func adjustedTick(_ tick: Double) -> Double {
        BaseGraphViewCacheSupport.adjustedBoundaryTick(
            tick,
            hasXAxis: viewModel.hasXAxis,
            yAxisDomain: viewModel.yAxisDomain,
            chartHeight: viewModel.chartFrame.height,
            isBabySelection: dashboardStore.isBabySelection
        )
    }

    @ChartContentBuilder
    private var xAxisGridLinesSolid: some ChartContent {
        if !dashboardStore.isBabySelection {
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

    /// Y-value for the horizontal baby crosshair — computed outside Chart { } to avoid
    /// complex nested logic inside the result builder, which can silently produce no content.
    private var horizontalBabyCrosshairYValue: Double? {
        guard viewModel.showCrosshair else { return nil }
        return babySelectionPresentation?.crosshairValue
    }

    private var selectedCrosshairDate: Date? {
        viewModel.selectedDate ?? viewModel.dashboardStore?.state.graph.selectedXValue
    }

    private var chartCrosshairContent: CrosshairContent {
        CrosshairContent(
            selectedDate: selectedCrosshairDate,
            showCrosshair: viewModel.showCrosshair,
            crosshairDate: babySelectionPresentation?.crosshairDate,
            horizontalYValue: horizontalBabyCrosshairYValue,
            timePeriod: viewModel.timePeriod,
            selectedBabyPercentile: selectedBabyPercentile,
            theme: theme,
            plotXDate: { viewModel.plotXDate(for: $0) }
        )
    }

    private var chartSeriesContent: ChartSeriesContent {
        ChartSeriesContent(
            cachedPlottedPoints: cachedPlottedPoints,
            yAxisDomain: viewModel.yAxisDomain,
            scrollPosition: viewModel.scrollPosition,
            visibleDomainLength: viewModel.visibleDomainLength,
            selectedPoint: viewModel.selectedPoint,
            showCrosshair: viewModel.showCrosshair,
            isScrolling: viewModel.isScrolling,
            lineWidth: viewModel.lineWidth,
            timePeriod: viewModel.timePeriod,
            productType: dashboardStore.productType,
            activeMonthInterval: dashboardStore.displayManager.activeMonthInterval,
            bpmClassification: dashboardStore.displayManager?.getBpmDisplayValues()?.classification,
            theme: theme,
            babyProfile: selectedBabyProfile,
            plotXDate: { viewModel.plotXDate(for: $0) },
            pointArea: { viewModel.pointArea(isSelected: $0) }
        )
    }

    private var chartBpmReferenceLines: BpmReferenceLines {
        BpmReferenceLines(
            productType: dashboardStore.productType,
            theme: theme
        )
    }

    private var mainChartView: some View {
        let xAxisLabels = cachedXAxisLabels
        return conditionalTouchModifiers(
            conditionalPreferenceChange(
                conditionalModifiers(
                    Chart {
                        yAxisGridLines
                        xAxisGridLinesSolid
                        yAxisBaseline
                        chartCrosshairContent
                        chartSeriesContent
                        chartBpmReferenceLines
                    }
                    .chartYScale(domain: viewModel.yAxisDomain)
                    .chartYAxis { yAxisMarks }
                    .chartLegend(.hidden)
                    .chartScrollTargetBehavior(getChartScrollBehavior(for: viewModel.timePeriod))
                    .transaction { transaction in
                        // Disable ALL animations during scroll and scroll-end transition
                        if viewModel.isScrolling || isInScrollEndTransition {
                            transaction.animation = nil
                        }
                    },
                    xAxisLabels: xAxisLabels
                )
                .frame(height: chartContainerHeight)
                .frame(maxWidth: .infinity, minHeight: chartContainerHeight)
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
                // Coordinated animation for line and point marks
                // - During scroll: no animation
                // - During scroll-end transition: no animation (data settling)
                // - Domain-only changes: no animation (prevents metrics from elongating unnaturally)
                // - Normal state: standard animations
                .animation(coordinatedChartAnimation, value: viewModel.yAxisDomain)
                .animation(coordinatedChartAnimation, value: seriesAnimationToken)
                .animation(coordinatedChartAnimation, value: dashboardStore.state.ui.selectedMetricLabel)
                .animation(.none, value: viewModel.scrollPosition)
                .animation(.none, value: viewModel.isScrolling)
            )
        )
    }

    // MARK: - Y-Axis Marks

    private var yAxisMarks: some AxisContent {
        AxisMarks(values: viewModel.yAxisTicks) { value in
            if let doubleValue = value.as(Double.self) {
                AxisValueLabel {
                    Text(getCachedYAxisLabel(doubleValue))
                        .fontOpenSans(.subHeading2)
                        .multilineTextAlignment(.leading)
                        .fontWeight(.regular)
                        .monospacedDigit()
                        .foregroundStyle(theme.textSubheading)
                        .frame(width: yAxisLabelWidth, alignment: .center)
                        .opacity(shouldShowYAxisLabels ? 1 : 0)
                }
            }
        }
    }

    // MARK: - Selection Callout

    private func selectionCalloutValue(for selectedDate: Date) -> Double? {
        BaseGraphViewCalloutSupport.selectionValue(
            for: selectedDate,
            plottedPoints: cachedPlottedPoints,
            babySelectionPresentation: babySelectionPresentation,
            plotXDate: { viewModel.plotXDate(for: $0) },
            fallbackDisplayWeight: viewModel.displayWeight
        )
    }

    @ViewBuilder
    private func selectionCallout(for selectedDate: Date, weight: Double) -> some View {
        if let chartPosition = viewModel.getChartPosition(for: selectedDate, value: weight) {
            GraphSelectionDateCalloutView(
                label: BaseGraphViewCalloutSupport.selectionDateLabel(
                    for: selectedDate,
                    usesBabyGrowthChartStyle: babyGrowthChartCalloutDateStyle,
                    fallbackLabel: viewModel.formatSelectedXAxisLabel()
                ),
                theme: theme,
                xPosition: BaseGraphViewCalloutSupport.selectionXPosition(
                    chartX: chartPosition.x,
                    chartWidth: viewModel.chartFrame.width,
                    isScrollable: isScrollable
                )
            )
        }
    }

    @ViewBuilder
    private func babyPercentileCallout(for selectedDate: Date, value: Double, percentile: Int) -> some View {
        if let chartPosition = viewModel.getChartPosition(for: selectedDate, value: value) {
            BabyPercentileCalloutView(
                percentile: percentile,
                theme: theme,
                topPadding: BaseGraphViewCalloutSupport.percentileTopPadding(for: chartPosition.y)
            )
        }
    }

    // MARK: - Goal Chip Callout
    @ViewBuilder
    private func goalChipCallout() -> some View {
        if let goalWeight = viewModel.goalWeight, viewModel.chartFrame.height > 0 {
            let goalPosition = viewModel.getGoalChipPosition()
            let roundedGoalWeight = viewModel.dashboardStore?.displayManager.roundedGoalWeight(goalWeight)
                ?? goalWeight.rounded(.toNearestOrAwayFromZero)
            let formattedGoalWeight = viewModel.dashboardStore?.displayManager
                .formatWeightDisplayText(roundedGoalWeight)

            GoalWeightChipView(
                label: BaseGraphViewCalloutSupport.goalWeightLabel(
                    roundedValue: roundedGoalWeight,
                    formattedValue: formattedGoalWeight,
                    fallbackFormatter: getCachedYAxisLabel
                ),
                theme: theme
            )
                .position(
                    x: viewModel.chartFrame.width > 0 ? viewModel.chartFrame.width - goalChipTrailingPadding : 320,
                    y: goalPosition.yPosition
                )
                .animation(coordinatedChartAnimation, value: goalPosition.yPosition)
        } else {
            EmptyView()
        }
    }

    // MARK: - Cache Management

    /// Updates cached chart data only when underlying data actually changes
    private func updateCachedChartData() {
        let cacheSnapshot = BaseGraphViewCacheSnapshot(
            seriesData: viewModel.getCachedSeriesData(),
            yAxisDomain: viewModel.yAxisDomain,
            yAxisTicks: viewModel.yAxisTicks
        )

        guard let cacheUpdate = BaseGraphViewCacheSupport.makeCacheUpdate(
            snapshot: cacheSnapshot,
            previousHash: lastDataHash,
            isCacheEmpty: cachedChartPoints.isEmpty,
            plotXDate: { viewModel.plotXDate(for: $0) }
        ) else {
            return
        }

        cachedChartPoints = cacheUpdate.chartPoints
        cachedGroupedPoints = cacheUpdate.groupedPoints
        cachedPlottedPoints = cacheUpdate.plottedPoints
        previousDataHash = lastDataHash
        lastDataHash = cacheUpdate.dataHash
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
        cachedYAxisLabels[value] ?? dashboardStore.displayManager.formatYAxisTickLabel(value)
    }

    /// Precomputes and caches all labels before rendering
    private func precomputeLabels() {
        cachedYAxisLabels = BaseGraphViewCacheSupport.precomputedYAxisLabels(
            ticks: viewModel.yAxisTicks,
            goalWeight: viewModel.goalWeight,
            existingLabels: cachedYAxisLabels,
            formatter: dashboardStore.displayManager.formatYAxisTickLabel
        )

        guard isScrollable else { return }

        cachedXAxisLabels = BaseGraphViewCacheSupport.precomputedXAxisLabels(
            dates: viewModel.xAxisValues,
            existingLabels: cachedXAxisLabels,
            formatter: viewModel.formatXAxisLabel(for:)
        )
    }

    /// Clears label caches when formatting context changes
    private func invalidateLabelCaches() {
        cachedYAxisLabels.removeAll()
        cachedXAxisLabels.removeAll()
    }

    // MARK: - Helpers
    private func assignFrameIfChanged(_ newFrame: CGRect) {
        let roundedFrame = BaseGraphViewCacheSupport.roundedFrame(newFrame)
        if roundedFrame != lastChartFrame {
            lastChartFrame = roundedFrame
            viewModel.updateChartFrame(roundedFrame)
        }
    }

    private func assignHeightIfChanged(_ newHeight: CGFloat) {
        let roundedHeight = BaseGraphViewCacheSupport.roundedHeight(newHeight)
        if roundedHeight != lastChartHeight {
            lastChartHeight = roundedHeight
            if isScrollable {
                dashboardStore.state.graph.chartHeight = roundedHeight
            }
        }
    }

}

// MARK: - View Extensions for Conditional Modifiers

extension View {

    // swiftlint:disable cyclomatic_complexity
    // This function has high complexity due to multiple conditional modifier application
    // based on scrollability and time period. Splitting would fragment the modifier
    // application logic and reduce maintainability.
    @ViewBuilder
// swiftlint:disable:next function_parameter_count
    func conditionalModifiers<ViewModel: SectionViewModelProtocol>(
        isScrollable: Bool,
        viewModel: ViewModel,
        localSelectedXValue: Binding<Date?>,
        dashboardStore: DashboardStore,
        theme: AppColors.Palette,
        xAxisLabels: [Date: String],
        isBabyChart: Bool,
        babyBoundaryYAxisTicks: [Double],
        babyChartPlotWidth: CGFloat,
        babyChartPlotHeight: CGFloat
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
                    let gridTicks: [Date] = {
                        guard viewModel.timePeriod == .month, !nonLastTicks.isEmpty else {
                            return nonLastTicks
                        }
                        let calendar = Calendar.current
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
                    }()
                    // Use ticks as-is; we keep Saturday visible via a phantom extra tick in data
                    let adjustedLabelTicks: [Date] = {
                        if viewModel.timePeriod == .year {
                            return nonLastTicks
                        }
                        return allTicks
                    }()
                    let renderedGridTicks: [Date] = {
                        if isBabyChart && viewModel.hasXAxis {
                            return Array(gridTicks.dropLast())
                        }
                        return gridTicks
                    }()
                    // Grid lines and ticks for all but the last value (to avoid the trailing thick edge)
                    AxisMarks(values: renderedGridTicks) { value in
                        if let date = value.as(Date.self), viewModel.shouldShowSolidLine(for: date) {
                            // Solid line for start of week/month/year
                            AxisGridLine(stroke: StrokeStyle(lineWidth: 1, dash: []))
                                .foregroundStyle(theme.statusIconSecondaryDisabled)
                            // For month-start lines, show the tick below X-axis only when
                            // the 1st day of month is also Sunday.
                            if viewModel.timePeriod == .month {
                                let calendar = Calendar.current
                                let comps = calendar.dateComponents([.day, .weekday], from: date)
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
                               let labelString = xAxisLabels[date] ?? viewModel.formatXAxisLabel(for: date) {
                                if viewModel.timePeriod == .month {
                                    Text(labelString)
                                        .font(.caption)
                                        .foregroundColor(theme.textSubheading)
                                        .fixedSize(horizontal: true, vertical: false)
                                        .padding(.horizontal, 2)
                                        .background(theme.textInverse)
                                } else {
                                    Text(labelString)
                                        .font(.caption)
                                        .foregroundColor(theme.textSubheading)
                                }
                            }
                        }
                    }
                }
            // Always add leading padding so the leftmost visible grid line
            // never renders flush against the chart edge, regardless of scroll
            // speed or position.
                .chartPlotStyle { plot in
                    if isBabyChart {
                        plot
                            .frame(width: babyChartPlotWidth, height: babyChartPlotHeight)
                            .overlay {
                                if viewModel.hasXAxis {
                                    SnapshotChartPlotBorderView(
                                        color: theme.statusIconSecondaryDisabled,
                                        yDomain: viewModel.yAxisDomain,
                                        yTicks: viewModel.yAxisTicks,
                                        showHorizontalGridLines: false,
                                        visibleHorizontalTicks: babyBoundaryYAxisTicks
                                    )
                                }
                            }
                    } else {
                        plot.padding(.leading, .spacingXS)
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
                                    await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                                }
                            } else {
                                // Clear any previous selection in the store
                                Task {
                                    await dashboardStore.chartManager.handleChartSelection(at: nil)
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
                                        await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                                    }
                                } else {
                                    // Clear any previous selection in the store
                                    Task {
                                        await dashboardStore.chartManager.handleChartSelection(at: nil)
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
                        viewModel.handleChartSelection(at: newValue)

                        // Update dashboard store selection using snapped date when available
                        if let rawDate = newValue {
                            if viewModel.showCrosshair {
                                let dateToSend = viewModel.preferredSelectedDate ?? rawDate
                                localSelectedXValue.wrappedValue = dateToSend
                                Task {
                                    await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                                }
                            } else {
                                localSelectedXValue.wrappedValue = nil
                                Task { await dashboardStore.chartManager.handleChartSelection(at: nil) }
                            }
                        } else {
                            localSelectedXValue.wrappedValue = nil
                        }
                    }
                ))
                // Immediate tap selection - bypasses scroll/selection disambiguation delay
                .chartGesture { proxy in
                    SpatialTapGesture()
                        .onEnded { value in
                            guard !viewModel.chartOperations.isEmpty else { return }
                            if let date: Date = proxy.value(atX: value.location.x) {
                                viewModel.handleChartSelection(at: date)
                                if viewModel.showCrosshair {
                                    let dateToSend = viewModel.preferredSelectedDate ?? date
                                    localSelectedXValue.wrappedValue = dateToSend
                                    Task {
                                        await dashboardStore.chartManager.handleChartSelection(at: dateToSend)
                                    }
                                } else {
                                    localSelectedXValue.wrappedValue = nil
                                    // Clear any previous selection in the store
                                    Task {
                                        await dashboardStore.chartManager.handleChartSelection(at: nil)
                                    }
                                }
                            }
                        }
                }
        }
    }
    // swiftlint:enable cyclomatic_complexity

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
                dashboardStore.state.graph.annotationHeight = height
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
                .onChange(of: dashboardStore.state.graph.xScrollPosition) { _, newPosition in
                    // Only sync if position actually changed (programmatic navigation)
                    // Skip if viewModel already has this position to avoid redundant updates
                    guard abs(newPosition.timeIntervalSince(viewModel.scrollPosition)) > 0.1 else { return }
                    viewModel.updateScrollPosition(to: newPosition)
                }
                .onChange(of: dashboardStore.state.graph.isScrolling) { _, newValue in
                    viewModel.isScrolling = newValue
                    // Immediately clear local selection when scrolling starts to remove crosshair and label
                    if newValue {
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
// swiftlint:disable:next file_length
// swiftlint:enable type_body_length function_body_length
