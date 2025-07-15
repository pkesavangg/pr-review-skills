//
//  Protocols.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation
import SwiftUICore

/// Protocol defining data management operations
protocol DashboardDataManaging {
    func loadInitialData() async throws
    func refreshData() async throws
    func handleEntryAdded(_ entry: Entry) async throws
    func handleEntryUpdated(_ entry: Entry) async throws
    func handleEntryDeleted(_ entry: Entry) async throws
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
    func getGoalWeightForDisplay(isWeightlessMode: Bool, anchorWeight: Double?) -> Double
    func formatGoalProgress() -> String
}

/// Protocol defining graph management operations
protocol DashboardGraphManaging {
    func updateScrollPosition(to date: Date) async
    func handleChartSelection(at selectedDate: Date) async
    @available(iOS 18.0, *)
    func handleScrollPhaseChange(_ phase: ScrollPhase) async
    func generateChartData(from operations: [BathScaleWeightSummary], selectedMetric: String?, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double) -> [GraphSeries]
    func updateSelectedPeriod(_ period: TimePeriod) async
    func getYAxisScale(from operations: [BathScaleWeightSummary], goalWeight: Double, isWeightlessMode: Bool, anchorWeight: Double?, convertWeight: @escaping (Int) -> Double, chartHeight: CGFloat) -> YAxisScale
    func getVisibleOperations(from operations: [BathScaleWeightSummary]) -> [BathScaleWeightSummary]
    func ensureLatestEntriesVisible(from operations: [BathScaleWeightSummary]) async
}

/// Protocol defining metrics management operations
protocol DashboardMetricsManaging {
    func updateMetrics(with entry: Entry) async throws
    func updateMetrics(with selectedPoint: BathScaleWeightSummary) async throws
    func saveMetricsToAPI() async throws
    func loadMetricsFromAPI() async throws
    func resetMetricsToDefaults() async throws
    func toggleMetricVisibility(at index: Int) async throws
    func reorderMetrics(from source: IndexSet, to destination: Int) async throws
    func getMetricValue(for label: String, from summary: BathScaleWeightSummary) async -> Double?
    func createEntryForMetricInfo(metricLabel: String?) async -> Entry
    func getBodyMetric(for metricLabel: String) async -> BodyMetric
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
