//
//  Protocols.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation
import SwiftUI

/// Protocol defining data management operations
@MainActor
protocol DashboardDataManaging {
    func loadInitialData() async throws
    func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary]
    func getLatestEntry() async throws -> Entry?
    func clearCache() async throws
    func loadLatestEntryData() async throws -> (entry: Entry?, weight: Int?)
    func initializeDataManager() async throws
}

/// Protocol defining goal management operations
@MainActor
protocol DashboardGoalManaging {
    func loadGoalData() async throws
    func updateGoalProgress(currentWeight: Int) async throws
    func calculateWeightlessGoal(anchorWeight: Double) async throws
    func refreshGoalDataForUnitChange() async throws
    func getGoalWeightForDisplay(isWeightlessMode: Bool, anchorWeight: Double?) -> Double?
    func formatGoalProgress() -> String
}

/// Protocol defining graph management operations
@MainActor
protocol DashboardGraphManaging {
    func updateScrollPosition(to date: Date)
    func handleScrollPositionChange(_ newPosition: Date?)
    func handleChartSelection(at selectedDate: Date?) async
    func updateSelectedPoint(_ point: BathScaleWeightSummary)
    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Double) -> Double) -> [GraphSeries]
    func updateSelectedPeriod(_ period: TimePeriod)
// swiftlint:disable:next function_parameter_count
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Double) -> Double, chartHeight: CGFloat) -> YAxisScale
// swiftlint:disable:next function_parameter_count
    func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Double) -> Double, chartHeight: CGFloat)
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary]
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary])

    func handleScrollStart()
    func handleScrollEnd() async
    func generateVisibleXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], scrollPosition: Date) -> [Date]
    func calculateOptimalScrollPosition(for period: TimePeriod, from operations: [BathScaleWeightSummary], anchorDate: Date?, showingLatest: Bool, cachedBounds: (min: Date, max: Date)?) -> Date
    func forceVisibleOperationsRecalculation()
}

/// Protocol defining metrics management operations
@MainActor
protocol DashboardMetricsManaging {
    func updateMetrics(with entry: Entry) async throws
    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws
    func saveMetricsToAPI(removedMetrics: Set<String>) async throws
    func loadMetricsFromAPI() async throws
    func resetMetricsToDefaults() async throws
    func toggleMetricVisibility(at index: Int) async throws
    func reorderMetrics(from source: IndexSet, to destination: Int) async throws
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) -> Double?
    func getMetricsToShow(isEditMode: Bool, dashboardType: DashboardType, removedMetrics: Set<String>) -> [MetricItem]
    func getRemovedMetricLabels() -> Set<String>
    func createEntryForMetricInfo(metricLabel: String?) async -> Entry
    func getBodyMetric(for metricLabel: String) -> BodyMetric
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>)
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async
}

/// Protocol defining streak management operations
@MainActor
protocol DashboardStreakManaging {
    func refreshStreakData() async throws
    func updateStreakItems(with progress: Progress) async throws
    func resetStreakData() async throws
    func getStreakItemsToShow(isEditMode: Bool) -> [MetricItem]
    func toggleStreakVisibility(at index: Int) async throws
    func calculateStreakAnalytics() -> StreakAnalytics
}

/// Protocol defining date range management operations
@MainActor
protocol DashboardDateRangeManagerProtocol {
    // MARK: - Date Range Calculations
    
    func getYearLabelDateRange(xScrollPosition: Date) -> (start: Date, end: Date)?
    func getLabelDateRangeForMonth(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        continuousOperations: [BathScaleWeightSummary]
    ) -> DateInterval
    func getLabelDateRangeForYear(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> DateInterval
    func getLabelDateRangeForWeek(xScrollPosition: Date) -> DateInterval
    func getFullyContainedMonthInterval(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval
    ) -> DateInterval?
    func inclusiveEnd(fromExclusive end: Date) -> Date
    
    // MARK: - Label Formatting
    
    func labelForTotalPeriod(
        dateBounds: (min: Date, max: Date)?,
        formatDateRange: (Date, Date, TimePeriod) -> String,
        fallbackLabel: () -> String
    ) -> String
    func labelForYearGridlines(
        xScrollPosition: Date,
        formatDateRange: (Date, Date, TimePeriod) -> String,
        fallbackLabel: () -> String
    ) -> String
    func labelForMonthGridlines(
        xScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        continuousOperations: [BathScaleWeightSummary],
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String
    func labelForWeekGridlines(
        xScrollPosition: Date,
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String
    func defaultRangeLabel(
        for period: TimePeriod,
        lastScrollPosition: Date,
        visibleDomainLength: TimeInterval,
        formatDateRange: (Date, Date, TimePeriod) -> String
    ) -> String
    func formatWeekRangeLabel(from start: Date, to end: Date) -> String
    func emptyStatePeriodLabel(for period: TimePeriod, today: Date) -> String
    
    // MARK: - Date Filtering Operations
    
    func filterOperationsInDateRange(
        operations: [BathScaleWeightSummary],
        start: Date,
        end: Date
    ) -> [BathScaleWeightSummary]
    func filterOperationsInDateRangeByDay(
        operations: [BathScaleWeightSummary],
        start: Date,
        end: Date
    ) -> [BathScaleWeightSummary]
    // swiftlint:disable:next function_parameter_count
    func getOperationsForLabelDateRange(
        period: TimePeriod,
        xScrollPosition: Date,
        visibleDomainLength: (TimePeriod) -> TimeInterval,
        continuousOperations: [BathScaleWeightSummary],
        dateBounds: (min: Date, max: Date)?,
        cachedPeriod: TimePeriod?,
        cachedScrollPos: Date?,
        cachedOps: [BathScaleWeightSummary]
    ) -> DateRangeOperationsResult
}

/// Protocol defining sync coordination operations
@MainActor
protocol DashboardSyncCoordinatorProtocol {
    // MARK: - Sync Operations
    
    func syncEntries() async
    
    // MARK: - Save Operations
    
    func saveChanges(
        saveMetrics: @escaping () async throws -> Void,
        saveProgressMetrics: @escaping () async throws -> Void,
        loadProgressMetrics: @escaping () async -> Void,
        onSuccess: @MainActor @escaping () -> Void,
        onError: @MainActor @escaping (Error) -> Void
    )
    
    // swiftlint:disable:next function_parameter_count
    func saveProgressMetricsToAPI(
        streakItems: [MetricItem],
        streakOrder: [String],
        goalCardPosition: Int,
        isGoalCardRemoved: Bool,
        removedStreaks: Set<String>,
        updateProgressMetrics: ([String]) async throws -> Void
    ) async throws
    
    // MARK: - Configuration Loading
    
    func loadDashboardConfigurationFromAPI(config: DashboardConfigurationLoadConfig) async
    
    func loadProgressMetricsFromAccount(
        activeAccount: AccountSnapshot?,
        allStreaks: [MetricItem],
        streakManagerActiveCount: inout Int,
        onProgressMetricsLoaded: (Int, Bool, [String], Set<String>) -> Void,
        setupDefaultOrder: () -> Void
    ) async

    func loadMetricsFromLocalAccount(
        activeAccount: AccountSnapshot?,
        updateDashboardType: (DashboardType) -> Void,
        updateMetricsOrder: ([String]) -> Void,
        setupInitialMetrics: () -> Void,
        onMetricsLoaded: () -> Void
    ) async
    
    // swiftlint:disable:next function_parameter_count
    func reloadDashboardConfiguration(
        fullRefresh: Bool,
        updateMetrics: Bool,
        loadConfiguration: () async -> Void,
        updateMetricsForView: () -> Void,
        scheduleUIUpdate: () -> Void,
        refreshDashboardState: () -> Void
    ) async
    
    func refreshAll(
        syncEntries: () async -> Void,
        onAppearActions: () -> Void
    ) async
    
    // MARK: - API Mapping Helpers
    
    func mapAPIValueToStreakLabel(_ apiValue: String, allStreaks: [MetricItem]) -> String?
    func mapStreakLabelToAPI(_ label: String) -> String?
}
