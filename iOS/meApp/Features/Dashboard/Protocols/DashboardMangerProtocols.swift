//
//  Protocols.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation
import SwiftUI

/// Protocol defining data management operations
protocol DashboardDataManaging {
    func loadInitialData() async throws
    func getContinuousOperations(for period: TimePeriod) -> [BathScaleWeightSummary]
    func getLatestEntry() async throws -> Entry?
    func clearCache() async throws
}


/// Protocol defining goal management operations
protocol DashboardGoalManaging {
    func loadGoalData() async throws
    func updateGoalProgress(currentWeight: Int) async throws
    func calculateWeightlessGoal(anchorWeight: Double) async throws
    func refreshGoalDataForUnitChange() async throws
    func getGoalWeightForDisplay(isWeightlessMode: Bool, anchorWeight: Double?) -> Double?
    func formatGoalProgress() -> String
}

/// Protocol defining graph management operations
protocol DashboardGraphManaging {
    func updateScrollPosition(to date: Date)
    func handleScrollPositionChange(_ newPosition: Date?)
    func handleChartSelection(at selectedDate: Date?) async
    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries]
    func updateSelectedPeriod(_ period: TimePeriod)
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale
    func calculateAndCacheYAxisDomain(from operations: [BathScaleWeightSummary], goalWeight: Double?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat)
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary]
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary])

    func handleScrollStart()
    func handleScrollEnd() async
    func generateVisibleXAxisValues(for period: TimePeriod, from operations: [BathScaleWeightSummary], scrollPosition: Date) -> [Date]
    func calculateOptimalScrollPosition(for period: TimePeriod, from operations: [BathScaleWeightSummary], showingLatest: Bool, cachedBounds: (min: Date, max: Date)?) -> Date
    func forceVisibleOperationsRecalculation()
}



/// Protocol defining metrics management operations
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
protocol DashboardStreakManaging {
    func refreshStreakData() async throws
    func updateStreakItems(with progress: Progress) async throws
    func resetStreakData() async throws
    func getStreakItemsToShow(isEditMode: Bool) -> [MetricItem]
    func toggleStreakVisibility(at index: Int) async throws
    func calculateStreakAnalytics() -> StreakAnalytics
}
