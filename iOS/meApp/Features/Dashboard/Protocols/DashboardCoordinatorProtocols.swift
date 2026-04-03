//
//  DashboardCoordinatorProtocols.swift
//  meApp
//
//  Protocols for the four coordinating managers that handle chart, display,
//  grid editing, and lifecycle operations on behalf of DashboardStore.
//

import Charts
import Foundation
import SwiftUI

// MARK: - State Access

/// Provides centralized state access for dashboard managers.
/// DashboardStore conforms to this protocol so that managers can read/write
/// the single `DashboardState` source of truth without holding a strong
/// reference to the store itself.
@MainActor
protocol DashboardStateProviding: AnyObject {
    var state: DashboardState { get set }
    var productType: EntryType { get }
    var isBabySelection: Bool { get }
    func scheduleUIUpdate()
    func forceImmediateUIUpdate()
    func yAxisScale(for operations: [BathScaleWeightSummary], chartHeight: CGFloat) -> YAxisScale
}

// MARK: - Chart Management

/// Protocol for chart initialization, scroll handling, Y-axis management,
/// chart selection, and related view updates.
@MainActor
protocol DashboardChartManaging {
    // Y-Axis
    var yAxisDomain: ClosedRange<Double> { get }
    var yAxisTicks: [Double] { get }
    func getYAxisScale() -> YAxisScale
    func updateYAxisCache(force: Bool)

    // Chart Lifecycle
    func initializeChart()
    func clearAllCaches()
    func forceCompleteRecalculationAfterScrollPosition()

    // Scroll Handling
    func handleScrollPositionChange(_ newPosition: Date?)
    func handleScrollStart()
    func handleScrollEndOptimized()
    func handleScrollPhaseChange(to phase: ScrollPhase) async

    // Chart Selection & Period
    func handleChartSelection(at selectedDate: Date?) async
    func clearSelection()
    func updateSelectedPeriod(_ period: TimePeriod, anchorDate: Date?)

    // View Updates
    func updateWeightDisplayForCurrentView()

    // Delegations
    func xAxisValuesWithBuffer(for period: TimePeriod) -> [Date]
    func xLabelString(for date: Date, period: TimePeriod) -> String?
    func selectEntry(_ entry: BathScaleWeightSummary?)
    func ensureLatestEntriesVisible()
    func getVisibleOperations() -> [BathScaleWeightSummary]

    // Scroll State
    var isProcessingScrollEnd: Bool { get set }
}

// MARK: - Display Management

/// Protocol for weight display, date range labels, formatting,
/// metric info, and metric view updates.
@MainActor
protocol DashboardDisplayManaging {
    // Weight Display
    var displayWeight: Double? { get }
    var weightLabel: String { get }
    var weightDisplayLabel: String { get }
    var displayUnitText: String { get }
    var activeMonthInterval: DateInterval? { get }
    func getCurrentAverageWeight() -> Double
    func updateVisibleDataAfterScroll()

    // Operations
    func getOperationsForLabelDateRange() -> [BathScaleWeightSummary]

    // Formatting
    func formatWeightDisplayText(_ weight: Double?) -> String
    func formatYAxisTickLabel(_ weight: Double) -> String
    func formatChartDate(_ date: Date) -> String
    func roundedGoalWeight(_ weight: Double) -> Double
    func formattedMetricValue(for metric: (preLabel: String?, value: String)) -> String

    // Metric Info
    func createEntryForMetricInfo(metricLabel: String?) -> Entry
    func createEntryForMetricInfoAsync(metricLabel: String?) async -> Entry
    func metricInfoDateLabel(for entryDTO: BathScaleOperationDTO) -> String
    func allowedMetricsForMetricInfo() -> [BodyMetric]
    func validateMetricInfoSelection(_ current: BodyMetric) -> BodyMetric
    func getBodyMetric(for metricLabel: String) -> BodyMetric

    // Metrics Updates
    func updateMetricsForCurrentView()
    func updateMetricsWithVisibleRegionAverage()
    func resetMetricsToLatestEntry()

    // BPM
    var currentBpmClassification: AhaPressureClass { get }
    func handleBpmPointSelection(_ point: BathScaleWeightSummary)
    func getBpmDisplayValues() -> BpmDisplayData?
}

// MARK: - Grid Editing Management

/// Protocol for grid editing operations: progress metrics loading, toggle/removal,
/// drag & drop, reordering, and related UI state management.
@MainActor
protocol DashboardGridEditingManaging {
    // Progress Metrics
    func loadProgressMetricsFromAccount() async
    func resetProgressMetricsToDefaults() async
    func regenerateStreakGridOrderAfterRefresh(oldStreakItems: [MetricItem], oldOrder: [String])

    // Sync Removal State
    func syncRemovalStateFromMetricsManager()
    func syncRemovalStateFromStreakManager()
    func debouncedSyncRemovalState()

    // Toggle Operations
    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int)
    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int)
    func toggleGoalCardRemoval()
    func toggleMetricRemoval(_ metricLabel: String)
    func toggleStreakRemoval(_ streakLabel: String)

    // Removal Queries
    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool
    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool
    func isMetricRemoved(_ metricLabel: String) -> Bool
    func isStreakRemoved(_ streakLabel: String) -> Bool

    // Goal Card
    func updateGoalCardPosition(_ newPosition: Int)
    func validateGoalCardPosition()

    // Drag and Drop
    func startDraggingMetric(_ metric: MetricItem)
    func startDraggingStreak(_ streak: MetricItem)
    func startDraggingGoalCard()
    func updateDropTarget(_ targetId: String?)
    func endDragging()
    func handleMetricDragEnd()
    func handleStreakDragEnd()

    // Reordering
    func reorderMetrics(from source: IndexSet, to destination: Int)
    func reorderStreakItems(from source: IndexSet, to destination: Int)
    func moveMetric(from sourceIndex: Int, to destinationIndex: Int)

    // UI State
    func resetDragState()
    func resetGridLayout()
    func restartWiggleAnimations()
    func selectMetric(_ label: String)
    func toggleEditMode()

    // Bindings
    var metricsBinding: Binding<[MetricItem]> { get }
    var streakItemsBinding: Binding<[MetricItem]> { get }
    var draggingMetricBinding: Binding<MetricItem?> { get }
    var draggingStreakBinding: Binding<MetricItem?> { get }
    var dropHoverIdBinding: Binding<String?> { get }
}

// MARK: - Lifecycle Management

/// Protocol for dashboard initialization, data loading, entry lifecycle,
/// settings handling, save/reset flows, and view lifecycle.
@MainActor
protocol DashboardLifecycleManaging {
    // Initialization
    func initializeDashboard() async

    // Data Loading
    func loadDashboardConfigurationFromAPI() async
    func loadLatestEntryData()
    func loadGoalCardData()

    // Entry Lifecycle
    func onEntryAdded(_ notification: EntryNotification)
    func onEntryUpdated(_ notification: EntryNotification)
    func onEntryDeleted(_ notification: EntryNotification)

    // Settings
    func handleSettingsChange(shouldRefreshStreak: Bool)
    func handleDashboardTypeChange()
    func handleUnitChange()
    func handleActiveAccountChanged()

    // Save/Reset
    func saveChanges()
    func saveProgressMetricsToAPI() async throws
    func resetDashboard()
    func resetDashboardEnhanced()
    func showResetDashboardAlert()

    // View Lifecycle
    func onAppearActions()
    func refreshDashboardState()
    func reloadDashboardConfiguration(fullRefresh: Bool, updateMetrics: Bool) async
    func refreshAll() async

    // UI Handlers
    func handleMetricLongPress(for metricLabel: String, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>)
    func handleSelectedMetricInfoChange(_ newValue: String?, selectedEntry: Binding<Entry?>, selectedMetric: Binding<BodyMetric?>) async
    func handleSelectedMetricLabelChange(_ newValue: String?)
    func handleSelectedEntryChange(_ newValue: Entry?)
    func handleMetricInfoSheetDismiss(_ newValue: MetricInfoWrapper?)
}
