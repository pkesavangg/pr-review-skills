//
//  BaseGraphView.swift
//  meApp
//
//  Created by Assistant on 04/07/25.
//

import Accessibility
import Charts
import SwiftUI

// swiftlint:disable file_length

// swiftlint:disable:next type_body_length
struct BaseGraphView<ViewModel: SectionViewModelProtocol>: View, Equatable {
    @ObservedObject var viewModel: ViewModel
    @ObservedObject var dashboardStore: DashboardStore
    var isActive: Bool = true

    @Environment(\.appTheme) var theme
    @Environment(\.babyGrowthChartCalloutDateStyle) var babyGrowthChartCalloutDateStyle

    @State var localSelectedXValue: Date?
    @State private var enableYAxisAnimation = false
    @State private var lastCacheUpdateTime: Date = .distantPast
    @State private var cacheUpdateTask: Task<Void, Never>?
    @State private var isInScrollEndTransition = false
    @State private var chartRebuildToken = 0
    @State private var previousYAxisDomain: ClosedRange<Double>?
    @State private var previousDataHash: Int?
    @State private var isDomainChangeOnly = false
    @State private var cachedChartPoints: [GraphSeries] = []
    @State private var cachedGroupedPoints: [String: [GraphSeries]] = [:]
    @State private var lastDataHash = 0
    @State private var cachedPlottedPoints: [String: [PlottedGraphSeries]] = [:]
    @State private var cachedOrderedSeriesNames: [String] = []
    @State private var cachedAllPlottedPoints: [PlottedGraphSeries] = []
    @State private var cachedYAxisLabels: [Double: String] = [:]
    @State private var cachedXAxisLabels: [Date: String] = [:]
    @State private var lastDataChangeSignature = 0
    @State private var lastSettingsChangeSignature = 0
    @State private var lastChartFrame: CGRect = .zero
    @State private var lastChartHeight: CGFloat = .zero

    private let cacheUpdateThrottle: TimeInterval = 0.05
    private let goalChipTrailingPadding: CGFloat = 20
    private let babyChartContainerHeight: CGFloat = 498

    /// MA-3837/MA-3977: snapshot of the store's selection used to drive an `.onChange` that
    /// re-syncs the section view model whenever the store selection changes (e.g. the
    /// auto-selection applied on a period switch).
    private struct StoreSelectionSyncState: Equatable {
        let selectedPointDate: Date?
        let selectedPointTimestamp: String?
        let selectedXValue: Date?
        let showCrosshair: Bool
    }

    private var storeSelectionSyncState: StoreSelectionSyncState {
        StoreSelectionSyncState(
            selectedPointDate: dashboardStore.state.graph.selectedPoint?.date,
            selectedPointTimestamp: dashboardStore.state.graph.selectedPoint?.entryTimestamp,
            selectedXValue: dashboardStore.state.graph.selectedXValue,
            showCrosshair: dashboardStore.state.graph.showCrosshair
        )
    }

    var body: some View {
        #if DEBUG
            Self._logChanges()
        #endif
        return conditionalScrollSyncing(
            ZStack {
                mainChartView

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

                if viewModel.goalWeight != nil && dashboardStore.productType != .bpm && selectedBabyProfile == nil {
                    goalChipCallout()
                }
            }
        )
        .onAppear(perform: handleOnAppear)
        .onDisappear(perform: handleOnDisappear)
        .onChange(of: viewModel.isScrolling) { oldValue, newValue in
            handleScrollStateChange(oldValue, newValue)
        }
        .onChange(of: dataChangeSignature) { oldSignature, newSignature in
            handleDataSignatureChange(oldSignature, newSignature)
        }
        .onChange(of: settingsChangeSignature) { oldSignature, newSignature in
            handleSettingsSignatureChange(oldSignature, newSignature)
        }
        .onChange(of: viewModel.yAxisDomain) { oldDomain, newDomain in
            handleYAxisDomainChange(oldDomain, newDomain)
        }
        .onChange(of: storeSelectionSyncState) { _, _ in
            // MA-3837/MA-3977: re-apply the store's selection to the section VM whenever it
            // changes (e.g. the auto-select on a period switch), so the crosshair tracks it.
            syncViewModelSelectionFromStore()
        }
        .accessibilityChartDescriptor(self)
        .graphViewStyle(
            canAddPadding: !viewModel.hasXAxis,
            canAddTrailingPadding: selectedBabyProfile == nil && viewModel.hasChartOperations,
            height: chartContainerHeight
        )
    }

    private func handleOnAppear() {
        viewModel.configure(with: dashboardStore)
        viewModel.updateCachedSeriesDataAsync()
        refreshCachedChartData()
        refreshLabelCache()
        Task { @MainActor in enableYAxisAnimation = true }
        // MA-3837/MA-3977: on mount (incl. the chart remount after a tab switch), adopt the
        // store's current selection so the auto-selected latest point renders on first frame.
        syncViewModelSelectionFromStore()

        guard isScrollable else { return }
        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 100_000_000)
            let targetPosition = viewModel.scrollPosition
            viewModel.scrollPosition = targetPosition.addingTimeInterval(0.001)
            await Task.yield()
            viewModel.scrollPosition = targetPosition
        }
    }

    private func handleOnDisappear() {
        cacheUpdateTask?.cancel()
        cacheUpdateTask = nil
    }

    /// MA-3837/MA-3977: mirror the store's validated selection into the section view model.
    /// Routes through `GraphState.validatedSelection` + `applyStoreValidatedSelection` so the
    /// on-mount sync and the `.onChange(storeSelectionSyncState)` path read the store-side
    /// selection through the same shape, bypassing the user-input snap/range guards that can
    /// otherwise clear a valid programmatic selection on the first frame after a tab switch.
    private func syncViewModelSelectionFromStore() {
        guard let selection = dashboardStore.state.graph.validatedSelection else {
            localSelectedXValue = nil
            viewModel.clearSelection()
            return
        }
        localSelectedXValue = selection.date
        viewModel.applyStoreValidatedSelection(date: selection.date, point: selection.point)
    }

    private func handleScrollStateChange(_ oldValue: Bool, _ newValue: Bool) {
        guard oldValue && !newValue else { return }
        isInScrollEndTransition = true
        chartRebuildToken += 1

        Task { @MainActor in
            try? await Task.sleep(nanoseconds: 5_000_000)
            isInScrollEndTransition = false
        }
    }

    private func handleDataSignatureChange(_ oldSignature: Int, _ newSignature: Int) {
        guard newSignature != oldSignature, newSignature != lastDataChangeSignature else { return }
        lastDataChangeSignature = newSignature

        viewModel.refreshData()
        viewModel.invalidateXAxisCache()

        Task { @MainActor in
            refreshCachedChartData()
            clearLabelCache()
            refreshLabelCache()
        }
    }

    private func handleSettingsSignatureChange(_ oldSignature: Int, _ newSignature: Int) {
        guard newSignature != oldSignature, newSignature != lastSettingsChangeSignature else { return }
        lastSettingsChangeSignature = newSignature

        viewModel.handleSettingsChange()

        Task { @MainActor in
            refreshCachedChartData()
            clearLabelCache()
            refreshLabelCache()
        }
    }

    private func handleYAxisDomainChange(_ oldDomain: ClosedRange<Double>, _ newDomain: ClosedRange<Double>) {
        isDomainChangeOnly = BaseGraphViewCacheManager.isDomainOnlyChange(
            previousYAxisDomain: previousYAxisDomain,
            newDomain: newDomain,
            lastDataHash: lastDataHash,
            previousDataHash: previousDataHash
        )
        previousYAxisDomain = newDomain

        Task { @MainActor in
            refreshCachedChartDataThrottled()
            cachedYAxisLabels.removeAll()
            try? await Task.sleep(nanoseconds: 100_000_000)
            isDomainChangeOnly = false
        }
    }

    private func refreshCachedChartData() {
        let cacheSnapshot = BaseGraphViewCacheSnapshot(
            seriesData: viewModel.getCachedSeriesData(),
            yAxisDomain: viewModel.yAxisDomain,
            yAxisTicks: viewModel.yAxisTicks
        )

        guard let cacheState = BaseGraphViewCacheManager.cacheState(
            snapshot: cacheSnapshot,
            previousHash: lastDataHash,
            isCacheEmpty: cachedChartPoints.isEmpty,
            plotXDate: { viewModel.plotXDate(for: $0) }
        ) else {
            return
        }

        cachedChartPoints = cacheState.chartPoints
        cachedGroupedPoints = cacheState.groupedPoints
        cachedPlottedPoints = cacheState.plottedPoints
        cachedOrderedSeriesNames = cacheState.orderedSeriesNames
        cachedAllPlottedPoints = cacheState.allPlottedPoints
        previousDataHash = cacheState.previousDataHash
        lastDataHash = cacheState.lastDataHash
    }

    private func refreshCachedChartDataThrottled() {
        switch BaseGraphViewCacheManager.throttleAction(
            now: Date(),
            lastCacheUpdateTime: lastCacheUpdateTime,
            throttleInterval: cacheUpdateThrottle
        ) {
        case let .updateNow(updatedAt):
            lastCacheUpdateTime = updatedAt
            refreshCachedChartData()
        case .schedule:
            cacheUpdateTask?.cancel()
            cacheUpdateTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: UInt64(cacheUpdateThrottle * 1_000_000_000))
                guard !Task.isCancelled else { return }
                refreshCachedChartData()
                refreshLabelCache()
            }
        }
    }

    private func clearCachedChartData() {
        let emptyState = BaseGraphViewCacheManager.invalidatedCacheState()
        cachedChartPoints = emptyState.chartPoints
        cachedGroupedPoints = emptyState.groupedPoints
        cachedPlottedPoints = emptyState.plottedPoints
        cachedOrderedSeriesNames = emptyState.orderedSeriesNames
        cachedAllPlottedPoints = emptyState.allPlottedPoints
        previousDataHash = emptyState.previousDataHash
        lastDataHash = emptyState.lastDataHash
    }

    private func refreshLabelCache() {
        let labelCache = BaseGraphViewCacheManager.precomputeLabels(
            yAxisTicks: viewModel.yAxisTicks,
            goalWeight: viewModel.goalWeight,
            existingYAxisLabels: cachedYAxisLabels,
            yAxisFormatter: dashboardStore.displayManager.formatYAxisTickLabel,
            isScrollable: isScrollable,
            xAxisValues: viewModel.xAxisValues,
            existingXAxisLabels: cachedXAxisLabels,
            xAxisFormatter: viewModel.formatXAxisLabel(for:)
        )

        cachedYAxisLabels = labelCache.yAxisLabels
        cachedXAxisLabels = labelCache.xAxisLabels
    }

    private func clearLabelCache() {
        cachedYAxisLabels.removeAll()
        cachedXAxisLabels.removeAll()
    }

    private var yAxisLabelWidth: CGFloat {
        if !viewModel.hasChartOperations && viewModel.timePeriod != .total {
            return 30
        }
        return 40
    }

    private var isScrollable: Bool { viewModel.hasXAxis }
    private var selectedBabyProfile: BabyProfile? { dashboardStore.selectedBabyProfile }

    private var selectedBabyCrosshairDate: Date? {
        viewModel.selectedPoint?.date
            ?? viewModel.selectedDate
            ?? viewModel.dashboardStore?.state.graph.selectedXValue
    }

    private var babySelectionPresentation: BabyGraphSelectionPresentation? {
        guard let selectedBabyProfile,
              selectedBabyCrosshairDate != nil else {
            return nil
        }

        return dashboardStore.graphManager.resolveBabySelectionPresentation(
            babyProfile: selectedBabyProfile,
            metric: dashboardStore.selectedBabyMetric,
            selectedCrosshairDate: selectedBabyCrosshairDate,
            plottedPoints: cachedAllPlottedPoints,
            plotXDate: { viewModel.plotXDate(for: $0) },
            currentUnit: dashboardStore.currentUnit,
            displayWeight: dashboardStore.displayManager?.displayWeight ?? viewModel.displayWeight
        )
    }

    private var selectedBabyPercentile: Int? {
        babySelectionPresentation?.percentile
    }

    private var chartContainerHeight: CGFloat {
        selectedBabyProfile != nil ? babyChartContainerHeight : 265
    }

    private var shouldShowYAxisLabels: Bool {
        if viewModel.hasChartOperations { return true }
        return viewModel.goalWeight != nil
    }

    private var coordinatedChartAnimation: Animation? {
        BaseGraphViewCacheManager.coordinatedChartAnimation(
            isScrolling: viewModel.isScrolling,
            isInScrollEndTransition: isInScrollEndTransition,
            isDomainChangeOnly: isDomainChangeOnly,
            enableYAxisAnimation: enableYAxisAnimation,
            shouldAnimateChartData: viewModel.shouldAnimateChartData
        )
    }

    private var seriesAnimationToken: Int {
        BaseGraphViewCacheManager.seriesAnimationToken(
            isScrolling: viewModel.isScrolling,
            lastDataHash: lastDataHash
        )
    }

    static func == (lhs: BaseGraphView, rhs: BaseGraphView) -> Bool {
        lhs.viewHash == rhs.viewHash
    }

    private var viewHash: Int {
        BaseGraphViewCacheManager.viewHash(
            yAxisTicks: viewModel.yAxisTicks,
            yAxisDomain: viewModel.yAxisDomain,
            timePeriod: viewModel.timePeriod,
            goalWeight: viewModel.goalWeight,
            showCrosshair: viewModel.showCrosshair,
            selectedDate: viewModel.selectedDate,
            selectedMetricLabel: dashboardStore.state.ui.selectedMetricLabel
        )
    }

    private var dataChangeSignature: Int {
        BaseGraphViewCacheManager.dataChangeSignature(
            dataRevision: dashboardStore.dataChangeRevision,
            selectedMetricLabel: dashboardStore.state.ui.selectedMetricLabel,
            productType: dashboardStore.productType,
            selectedProductItem: dashboardStore.selectedProductItem
        )
    }

    private var settingsChangeSignature: Int {
        BaseGraphViewCacheManager.settingsChangeSignature(
            currentUnitRawValue: dashboardStore.currentUnit.rawValue,
            isWeightlessModeEnabled: dashboardStore.isWeightlessModeEnabled
        )
    }

    var dashboardYAxisCacheSignature: Int {
        BaseGraphViewCacheManager.yAxisCacheSignature(
            cachedDomain: dashboardStore.state.graph.cachedYAxisDomain,
            cachedTicks: dashboardStore.state.graph.cachedYAxisTicks
        )
    }

    @ChartContentBuilder
    private var yAxisGridLines: some ChartContent {
        if dashboardStore.isBabySelection && viewModel.hasXAxis {
        } else {
            let ticksToRender = dashboardStore.isBabySelection ? boundaryYAxisTicks : viewModel.yAxisTicks

            ForEach(ticksToRender, id: \.self) { tick in
                RuleMark(y: .value("YGrid", adjustedTick(tick)))
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
            let referenceDate = viewModel.hasXAxis ? viewModel.xAxisValues.last : viewModel.xAxisValues.first
            if let referenceDate, viewModel.hasXAxis {
                let domainLength = viewModel.visibleDomainLength
                let width = max(1, viewModel.chartFrame.width)
                let secondsPerPoint = domainLength / Double(width)
                let effectiveDate = referenceDate.addingTimeInterval(-(secondsPerPoint * 0.5))

                RuleMark(x: .value("XGrid", effectiveDate))
                    .lineStyle(StrokeStyle(lineWidth: 1))
                    .foregroundStyle(theme.statusIconSecondaryDisabled)
            }
        }
    }

    @ChartContentBuilder
    private var yAxisBaseline: some ChartContent {
        if !viewModel.hasXAxis {
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
            theme: theme
        ) { viewModel.plotXDate(for: $0) }
    }

    private var chartSeriesContent: ChartSeriesContent {
        let visiblePercentileRange: ClosedRange<Date>? = {
            if selectedBabyProfile != nil, !viewModel.hasXAxis {
                return viewModel.dateRange
            }

            let ticks: [Date]
            if viewModel.timePeriod == .week || viewModel.timePeriod == .year {
                ticks = viewModel.xAxisValues.sorted()
            } else {
                ticks = viewModel.gridTicks.sorted()
            }
            return ticks.first.flatMap { first in
                ticks.last.map { first...$0 }
            }
        }()
        return ChartSeriesContent(
            orderedSeriesNames: cachedOrderedSeriesNames,
            cachedPlottedPoints: cachedPlottedPoints,
            yAxisDomain: viewModel.yAxisDomain,
            scrollPosition: viewModel.scrollPosition,
            visibleDomainLength: viewModel.visibleDomainLength,
            visibleGridRange: visiblePercentileRange,
            selectedPlottedDate: viewModel.selectedPoint.map { viewModel.plotXDate(for: $0.date) },
            showCrosshair: viewModel.showCrosshair,
            isScrolling: viewModel.isScrolling,
            lineWidth: viewModel.lineWidth,
            timePeriod: viewModel.timePeriod,
            productType: dashboardStore.productType,
            activeMonthInterval: dashboardStore.displayManager.activeMonthInterval,
            bpmClassification: dashboardStore.displayManager?.getBpmDisplayValues()?.classification,
            theme: theme,
            babyProfile: selectedBabyProfile
        ) { viewModel.pointArea(isSelected: $0) }
    }

    private var chartBpmReferenceLines: BpmReferenceLines {
        BpmReferenceLines(productType: dashboardStore.productType, theme: theme)
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
                    .id(lastDataHash)
                    .chartYScale(domain: viewModel.yAxisDomain)
                    .chartYAxis { yAxisMarks }
                    .chartLegend(.hidden)
                    .chartScrollTargetBehavior(getChartScrollBehavior(for: viewModel.timePeriod))
                    .transaction { transaction in
                        if viewModel.isScrolling || isInScrollEndTransition {
                            transaction.animation = nil
                        }
                    },
                    xAxisLabels: xAxisLabels
                )
                .frame(height: chartContainerHeight)
                .frame(maxWidth: .infinity, minHeight: chartContainerHeight)
                .padding(.leading, 0)
                .onGeometryChange(for: CGFloat.self) { proxy in
                    proxy.size.height
                } action: { newHeight in
                    assignHeightIfChanged(newHeight)
                }
                .onGeometryChange(for: CGRect.self) { proxy in
                    proxy.frame(in: .local)
                } action: { newFrame in
                    assignFrameIfChanged(newFrame)
                }
                .animation(coordinatedChartAnimation, value: viewModel.yAxisDomain)
                .animation(coordinatedChartAnimation, value: seriesAnimationToken)
                .animation(coordinatedChartAnimation, value: dashboardStore.state.ui.selectedMetricLabel)
                .animation(.none, value: viewModel.scrollPosition)
                .animation(.none, value: viewModel.isScrolling)
            )
        )
    }

    private var yAxisMarks: some AxisContent {
        AxisMarks(values: viewModel.yAxisTicks) { value in
            if let doubleValue = value.as(Double.self) {
                AxisValueLabel {
                    Text(
                        BaseGraphViewCacheManager.yAxisLabel(
                            for: doubleValue,
                            cachedLabels: cachedYAxisLabels,
                            formatter: dashboardStore.displayManager.formatYAxisTickLabel
                        )
                    )
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
                    formattedValue: formattedGoalWeight
                ) {
                    BaseGraphViewCacheManager.yAxisLabel(
                        for: $0,
                        cachedLabels: cachedYAxisLabels,
                        formatter: dashboardStore.displayManager.formatYAxisTickLabel
                    )
                },
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

// MARK: - AXChartDescriptorRepresentable

extension BaseGraphView: AXChartDescriptorRepresentable {
    // swiftlint:disable:next function_body_length
    func makeChartDescriptor() -> AXChartDescriptor {
        let dateFormatter = DateFormatter()
        dateFormatter.dateStyle = .medium
        dateFormatter.timeStyle = .none

        // Ordered unique date strings for the categorical x-axis
        var seenDates = Set<String>()
        var orderedDateStrings: [String] = []
        for point in cachedAllPlottedPoints.sorted(by: { $0.date < $1.date }) {
            let str = dateFormatter.string(from: point.date)
            if seenDates.insert(str).inserted {
                orderedDateStrings.append(str)
            }
        }

        let xAxis = AXCategoricalDataAxisDescriptor(
            title: DashboardStrings.accChartXAxisName,
            categoryOrder: orderedDateStrings.isEmpty ? ["–"] : orderedDateStrings
        )

        let yRange = viewModel.yAxisDomain
        let lower = yRange.lowerBound.isFinite ? yRange.lowerBound : 0.0
        let upper = yRange.upperBound.isFinite ? yRange.upperBound : 1.0
        let safeRange = lower < upper ? lower...upper : lower...(lower + 1)
        let displayManager = dashboardStore.displayManager
        let yAxis = AXNumericDataAxisDescriptor(
            title: chartDescriptorYAxisTitle,
            range: safeRange,
            gridlinePositions: viewModel.yAxisTicks
        ) { value in
            displayManager?.formatYAxisTickLabel(value) ?? ""
        }

        // Build per-series data points, preserving the existing series render order
        var seriesDataPoints: [String: [AXDataPoint]] = [:]
        for point in cachedAllPlottedPoints.sorted(by: { $0.date < $1.date }) {
            let key = point.original.series
            let axPoint = AXDataPoint(x: dateFormatter.string(from: point.date), y: point.original.value)
            seriesDataPoints[key, default: []].append(axPoint)
        }

        var seriesDescriptors = cachedOrderedSeriesNames.compactMap { name -> AXDataSeriesDescriptor? in
            guard let points = seriesDataPoints[name] else { return nil }
            return AXDataSeriesDescriptor(
                name: axSeriesDisplayName(for: name),
                isContinuous: true,
                dataPoints: points
            )
        }
        if seriesDescriptors.isEmpty {
            seriesDescriptors = [AXDataSeriesDescriptor(name: "", isContinuous: true, dataPoints: [])]
        }

        return AXChartDescriptor(
            title: chartDescriptorTitle,
            summary: nil,
            xAxis: xAxis,
            yAxis: yAxis,
            additionalAxes: [],
            series: seriesDescriptors
        )
    }

    private var chartDescriptorTitle: String {
        switch dashboardStore.productType {
        case .bpm: return DashboardStrings.accBpmChartLabel
        case .baby: return DashboardStrings.accBabyChartLabel
        default: return DashboardStrings.accWeightChartLabel
        }
    }

    private var chartDescriptorYAxisTitle: String {
        switch dashboardStore.productType {
        case .bpm: return DashboardStrings.accChartBpmYAxisName
        case .baby: return DashboardStrings.accChartBabyYAxisName
        default: return DashboardStrings.accChartWeightYAxisName
        }
    }

    private func axSeriesDisplayName(for series: String) -> String {
        switch series.lowercased() {
        case "systolic": return BpmDashboardStrings.systolic
        case "diastolic": return BpmDashboardStrings.diastolic
        case "pulse": return BpmDashboardStrings.pulse.capitalized
        default: return series
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
// swiftlint:enable file_length
