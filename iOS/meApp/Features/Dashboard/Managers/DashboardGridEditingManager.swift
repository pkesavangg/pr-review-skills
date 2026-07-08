//
//  DashboardGridEditingManager.swift
//  meApp
//
//  Grid editing, toggle/removal, drag & drop, reordering, and progress metrics loading.
//

import Foundation
import SwiftUI
import UIKit

@MainActor
final class DashboardGridEditingManager: DashboardGridEditingManaging {

    // MARK: - Dependencies

    weak var stateProvider: DashboardStateProviding?

    private let metricsManager: DashboardMetricsManager
    private let streakManager: DashboardStreakManager
    private let syncCoordinator: DashboardSyncCoordinatorProtocol
    private let cacheManager: DashboardCacheManagerProtocol
    @Injector var logger: LoggerService

    /// Closures for store-level computed properties that depend on multiple managers
    private let getMetricsToShow: () -> [MetricItem]
    private let getStreakItemsToShow: () -> [MetricItem]
    private let beginEdit: () -> Void

    // MARK: - Private State

    private static let allProgressMetricsRemovedKey = "dashboard.allProgressMetricsRemoved"
    var syncDebounceTask: Task<Void, Never>?

    // MARK: - Initialization

    init(
        stateProvider: DashboardStateProviding,
        metricsManager: DashboardMetricsManager,
        streakManager: DashboardStreakManager,
        syncCoordinator: DashboardSyncCoordinatorProtocol,
        cacheManager: DashboardCacheManagerProtocol,
        getMetricsToShow: @escaping () -> [MetricItem],
        getStreakItemsToShow: @escaping () -> [MetricItem],
        beginEdit: @escaping () -> Void
    ) {
        self.stateProvider = stateProvider
        self.metricsManager = metricsManager
        self.streakManager = streakManager
        self.syncCoordinator = syncCoordinator
        self.cacheManager = cacheManager
        self.getMetricsToShow = getMetricsToShow
        self.getStreakItemsToShow = getStreakItemsToShow
        self.beginEdit = beginEdit
        // Cache DI-backed services during construction so later container mutations
        // continue using the intended dependencies for this manager instance.
        _ = logger
    }

    // MARK: - Progress Metrics Loading

    func loadProgressMetricsFromAccount() async { // swiftlint:disable:this function_body_length
        guard let stateProvider else { return }

        guard let account = (stateProvider as? DashboardStore)?.accountService.activeAccount else {
            await MainActor.run { setupDefaultProgressMetricsOrder() }
            return
        }

        guard let progressMetricsString = account.progressMetrics else {
            await MainActor.run { setupDefaultProgressMetricsOrder() }
            return
        }

        let progressMetrics = progressMetricsString.isEmpty
            ? []
            : progressMetricsString.split(separator: ",").map { String($0) }.filter { !$0.isEmpty }

        let allMetricsRemovedFlag = cacheManager.getBool(forKey: Self.allProgressMetricsRemovedKey)
        let defaultMetricsList: Set<String> = [
            "goal", "currentStreak", "longestStreak",
            "weeklyChange", "monthlyChange", "yearlyChange", "totalChange"
        ]
        let isDefaultFullList = Set(progressMetrics) == defaultMetricsList && progressMetrics.count == defaultMetricsList.count

        let shouldTreatAsAllRemoved = allMetricsRemovedFlag && (progressMetrics.isEmpty || isDefaultFullList)

        await MainActor.run {
            let allStreaks = streakManager.state.streakItems

            let isInitialLoad = stateProvider.state.ui.streakGridOrder.isEmpty && stateProvider.state.ui.removedStreaks.isEmpty

            if progressMetrics.isEmpty, isInitialLoad {
                setupDefaultProgressMetricsOrder()
                return
            }

            if progressMetrics.isEmpty || shouldTreatAsAllRemoved, !isInitialLoad {
                stateProvider.state.ui.goalCardPosition = 0
                stateProvider.state.ui.isGoalCardRemoved = true
                if stateProvider.state.ui.streakGridOrder.isEmpty {
                    let defaultOrder = allStreaks.map { $0.id.uuidString }
                    stateProvider.state.ui.streakGridOrder = defaultOrder
                }
                stateProvider.state.ui.removedStreaks = Set(allStreaks.map { $0.label })

                streakManager.state.activeStreakItemsCount = 0

                stateProvider.scheduleUIUpdate()
                return
            }

            guard !allStreaks.isEmpty else {
                setupDefaultProgressMetricsOrder()
                return
            }

            var goalCardPosition: Int?
            var orderedStreakIds: [String] = []
            var foundStreakLabels: Set<String> = []

            for (index, apiValue) in progressMetrics.enumerated() {
                if apiValue == "goal" {
                    goalCardPosition = index
                } else if let streakLabel = syncCoordinator.mapAPIValueToStreakLabel(apiValue, allStreaks: allStreaks),
                          let streakItem = allStreaks.first(where: { $0.label == streakLabel }) {
                    orderedStreakIds.append(streakItem.id.uuidString)
                    foundStreakLabels.insert(streakLabel)
                }
            }

            stateProvider.state.ui.goalCardPosition = goalCardPosition ?? 0
            stateProvider.state.ui.isGoalCardRemoved = goalCardPosition == nil
            stateProvider.state.ui.streakGridOrder = orderedStreakIds
            stateProvider.state.ui.removedStreaks = Set(allStreaks.map { $0.label }).subtracting(foundStreakLabels)

            let activeCount = max(
                0,
                allStreaks.count - stateProvider.state.ui.removedStreaks.count
            )

            streakManager.state.activeStreakItemsCount = min(activeCount, allStreaks.count)

            stateProvider.scheduleUIUpdate()
        }
    }

    func resetProgressMetricsToDefaults() async {
        await MainActor.run { setupDefaultProgressMetricsOrder() }
    }

    private func setupDefaultProgressMetricsOrder() {
        guard let stateProvider else { return }
        let allStreaks = streakManager.state.streakItems
        var defaultOrder: [String] = []

        if let streak = allStreaks.first(where: { $0.label == DashboardStrings.currentStreak }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label == DashboardStrings.longestStreak }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/week") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/month") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/year") }) {
            defaultOrder.append(streak.id.uuidString)
        }
        if let streak = allStreaks.first(where: { $0.label.contains("/total") }) {
            defaultOrder.append(streak.id.uuidString)
        }

        stateProvider.state.ui.goalCardPosition = 0
        stateProvider.state.ui.isGoalCardRemoved = false
        stateProvider.state.ui.streakGridOrder = defaultOrder
        stateProvider.state.ui.removedStreaks = []
    }

    func regenerateStreakGridOrderAfterRefresh(
        oldStreakItems: [MetricItem],
        oldOrder: [String]
    ) {
        guard let stateProvider else { return }
        let newItems = streakManager.state.streakItems
        guard !oldOrder.isEmpty else {
            setupDefaultProgressMetricsOrder()
            return
        }

        let oldIdToLabel = Dictionary(
            oldStreakItems.map { ($0.id.uuidString, $0.label) }
        ) { first, _ in first }

        let labelToNewId = Dictionary(
            newItems.map { ($0.label, $0.id.uuidString) }
        ) { first, _ in first }

        var newOrder = oldOrder.compactMap {
            oldIdToLabel[$0].flatMap { labelToNewId[$0] }
        }

        let existingIds = Set(newOrder)
        newOrder.append(contentsOf:
            newItems
                .map { $0.id.uuidString }
                .filter { !existingIds.contains($0) })

        stateProvider.state.ui.streakGridOrder = newOrder
    }

    // MARK: - Sync Removal State

    func syncRemovalStateFromMetricsManager() {
        guard let stateProvider else { return }
        let currentMetrics = metricsManager.state.metrics
        let activeCount = metricsManager.state.activeMetricsCount

        stateProvider.state.ui.removedMetrics.removeAll()

        let safeActiveCount = min(activeCount, currentMetrics.count)

        for i in safeActiveCount ..< currentMetrics.count {
            stateProvider.state.ui.removedMetrics.insert(currentMetrics[i].label)
        }
    }

    func debouncedSyncRemovalState() {
        syncDebounceTask?.cancel()

        syncDebounceTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 150_000_000)

            if !Task.isCancelled {
                self?.syncRemovalStateFromMetricsManager()
            }
        }
    }

    func syncRemovalStateFromStreakManager() {
        guard let stateProvider else { return }
        let currentStreakItems = streakManager.state.streakItems
        var activeCount = streakManager.state.activeStreakItemsCount

        guard !currentStreakItems.isEmpty else {
            stateProvider.state.ui.removedStreaks.removeAll()
            return
        }

        if activeCount > currentStreakItems.count {
            activeCount = currentStreakItems.count
        }

        stateProvider.state.ui.removedStreaks.removeAll()

        let safeActiveCount = min(activeCount, currentStreakItems.count)

        guard safeActiveCount < currentStreakItems.count else {
            return
        }
        for i in safeActiveCount ..< currentStreakItems.count {
            stateProvider.state.ui.removedStreaks.insert(currentStreakItems[i].label)
        }
    }

    // MARK: - UI Action Methods

    func toggleMetricRemovalInReorderedArray(at reorderedIndex: Int) {
        guard let stateProvider else { return }
        let metricsToShow = getMetricsToShow()
        guard reorderedIndex < metricsToShow.count else { return }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == metric.id }) else { return }

        Task {
            try? await metricsManager.toggleMetricVisibility(at: originalIndex)

            await MainActor.run {
                self.syncRemovalStateFromMetricsManager()
                stateProvider.forceImmediateUIUpdate()
            }
        }
    }

    func isMetricRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let metricsToShow = getMetricsToShow()
        guard reorderedIndex < metricsToShow.count else { return false }
        let metric = metricsToShow[reorderedIndex]
        guard let originalIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == metric.id }) else { return false }
        return originalIndex >= metricsManager.state.activeMetricsCount
    }

    func toggleStreakRemovalInReorderedArray(at reorderedIndex: Int) {
        guard let stateProvider else { return }
        let streakItemsToShow = getStreakItemsToShow()
        guard reorderedIndex < streakItemsToShow.count else { return }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = stateProvider.state.streak.streakItems.firstIndex(where: { $0.id == streak.id }) else { return }

        Task {
            try? await streakManager.toggleStreakVisibility(at: originalIndex)

            await MainActor.run {
                self.syncRemovalStateFromStreakManager()
                self.validateGoalCardPosition()
                stateProvider.forceImmediateUIUpdate()
            }
        }
    }

    func isStreakRemovedInReorderedArray(at reorderedIndex: Int) -> Bool {
        let streakItemsToShow = getStreakItemsToShow()
        guard reorderedIndex < streakItemsToShow.count else { return false }
        let streak = streakItemsToShow[reorderedIndex]
        guard let originalIndex = streakManager.state.streakItems.firstIndex(where: { $0.id == streak.id }) else { return false }
        return streakManager.isStreakRemoved(at: originalIndex)
    }

    // MARK: - Removal Status Methods for UIKit Views

    func isMetricRemoved(_ metricLabel: String) -> Bool {
        guard let stateProvider else { return false }
        return stateProvider.state.ui.removedMetrics.contains(metricLabel)
    }

    func isStreakRemoved(_ streakLabel: String) -> Bool {
        guard let stateProvider else { return false }
        return stateProvider.state.ui.removedStreaks.contains(streakLabel)
    }

    // MARK: - Toggle Removal Methods for UIKit Views

    func toggleMetricRemoval(_ metricLabel: String) {
        guard let stateProvider else { return }
        if let metricIndex = metricsManager.state.metrics.firstIndex(where: { $0.label == metricLabel }) {
            if stateProvider.state.ui.removedMetrics.contains(metricLabel) {
                stateProvider.state.ui.removedMetrics.remove(metricLabel)
            } else {
                stateProvider.state.ui.removedMetrics.insert(metricLabel)
            }
            do {
                try metricsManager.toggleMetricVisibilitySync(at: metricIndex)
                syncRemovalStateFromMetricsManager()
            } catch {
                logger.log(level: .error, tag: "DashboardGridEditingManager", message: "Failed to toggle metric visibility: \(error)")
            }
        }
    }

    func toggleStreakRemoval(_ streakLabel: String) {
        guard let stateProvider else { return }
        guard let streakIndex = streakManager.state.streakItems.firstIndex(where: { $0.label == streakLabel }) else {
            return
        }

        _ = streakIndex >= streakManager.state.activeStreakItemsCount

        Task {
            _ = try? await streakManager.toggleStreakVisibility(at: streakIndex)

            await MainActor.run {
                self.syncRemovalStateFromStreakManager()
                self.validateGoalCardPosition()
                stateProvider.forceImmediateUIUpdate()
            }
        }
    }

    func toggleGoalCardRemoval() {
        guard let stateProvider else { return }
        stateProvider.state.ui.isGoalCardRemoved.toggle()
        stateProvider.forceImmediateUIUpdate()
    }

    func updateGoalCardPosition(_ newPosition: Int) {
        guard let stateProvider else { return }
        let streakItemsToShow = getStreakItemsToShow()
        let maxPosition = streakItemsToShow.count
        var clampedPosition = max(0, min(newPosition, maxPosition))
        if stateProvider.state.ui.removedStreaks.isEmpty {
            let columns = DevicePlatform.isTablet ? 4 : 2
            clampedPosition = (clampedPosition / columns) * columns
        }

        if stateProvider.state.ui.goalCardPosition != clampedPosition {
            stateProvider.state.ui.goalCardPosition = clampedPosition
            stateProvider.forceImmediateUIUpdate()
        }
    }

    func validateGoalCardPosition() {
        guard let stateProvider else { return }
        let streakItemsToShow = getStreakItemsToShow()
        let maxPosition = streakItemsToShow.count

        if stateProvider.state.ui.goalCardPosition > maxPosition {
            stateProvider.state.ui.goalCardPosition = maxPosition
        }

        if stateProvider.state.ui.goalCardPosition < 0 {
            stateProvider.state.ui.goalCardPosition = 0
        }

        if stateProvider.state.ui.removedStreaks.isEmpty {
            let columns = DevicePlatform.isTablet ? 4 : 2
            let snapped = (stateProvider.state.ui.goalCardPosition / columns) * columns
            if snapped != stateProvider.state.ui.goalCardPosition {
                stateProvider.state.ui.goalCardPosition = snapped
            }
        }

        let hasRemovedStreaks = !stateProvider.state.ui.removedStreaks.isEmpty
        logger.log(
            level: .debug,
            tag: "DashboardGridEditingManager",
            message: "Goal card position validated: \(stateProvider.state.ui.goalCardPosition), maxPosition: \(maxPosition), " +
                "streakCount: \(streakItemsToShow.count), hasRemovedStreaks: \(hasRemovedStreaks), isEditMode: \(stateProvider.state.ui.isEditMode)"
        )
    }

    func resetDragState() {
        guard let stateProvider else { return }
        stateProvider.state.ui.draggingMetric = nil
        stateProvider.state.ui.draggingStreak = nil
        stateProvider.state.ui.dropHoverId = nil
        stateProvider.state.ui.isGoalCardBeingDragged = false
    }

    func resetGridLayout() {
        guard let stateProvider else { return }
        resetDragState()
        stateProvider.state.ui.gridLayoutId = UUID()
    }

    func restartWiggleAnimations() {
        UIView.clearWiggleIntervalCache()
        resetGridLayout()
    }

    func selectMetric(_ label: String) {
        guard let stateProvider else { return }
        if stateProvider.state.ui.selectedMetricLabel == label {
            stateProvider.state.ui.selectedMetricLabel = nil
        } else {
            stateProvider.state.ui.selectedMetricLabel = label
        }
    }

    func toggleEditMode() {
        guard let stateProvider else { return }
        if !stateProvider.state.ui.isEditMode {
            beginEdit()
            stateProvider.state.ui.isEditMode = true
        } else {
            // Already in edit mode - delegate to store's resetEditSession
            (stateProvider as? DashboardStore)?.resetEditSession()
        }
    }

    // MARK: - Drag and Drop Bindings

    var metricsBinding: Binding<[MetricItem]> {
        Binding(
            get: { [weak self] in
                self?.metricsManager.state.metrics ?? []
            },
            set: { [weak self] newValue in
                self?.metricsManager.state.metrics = newValue
            }
        )
    }

    var streakItemsBinding: Binding<[MetricItem]> {
        Binding(
            get: { [weak self] in
                self?.streakManager.state.streakItems ?? []
            },
            set: { [weak self] newValue in
                self?.streakManager.state.streakItems = newValue
            }
        )
    }

    var draggingMetricBinding: Binding<MetricItem?> {
        Binding(
            get: { [weak self] in
                self?.stateProvider?.state.ui.draggingMetric
            },
            set: { [weak self] newValue in
                self?.stateProvider?.state.ui.draggingMetric = newValue
            }
        )
    }

    var draggingStreakBinding: Binding<MetricItem?> {
        Binding(
            get: { [weak self] in
                self?.stateProvider?.state.ui.draggingStreak
            },
            set: { [weak self] newValue in
                self?.stateProvider?.state.ui.draggingStreak = newValue
            }
        )
    }

    var dropHoverIdBinding: Binding<String?> {
        Binding(
            get: { [weak self] in
                self?.stateProvider?.state.ui.dropHoverId
            },
            set: { [weak self] newValue in
                self?.stateProvider?.state.ui.dropHoverId = newValue
            }
        )
    }

    // MARK: - Drag State Management

    func startDraggingMetric(_ metric: MetricItem) {
        stateProvider?.state.ui.draggingMetric = metric
    }

    func startDraggingStreak(_ streak: MetricItem) {
        stateProvider?.state.ui.draggingStreak = streak
    }

    func startDraggingGoalCard() {
        stateProvider?.state.ui.isGoalCardBeingDragged = true
    }

    func updateDropTarget(_ targetId: String?) {
        stateProvider?.state.ui.dropHoverId = targetId
    }

    func endDragging() {
        guard let stateProvider else { return }
        stateProvider.state.ui.draggingMetric = nil
        stateProvider.state.ui.draggingStreak = nil
        stateProvider.state.ui.isGoalCardBeingDragged = false
        stateProvider.state.ui.dropHoverId = nil
    }

    func handleMetricDragEnd() {
        endDragging()
        stateProvider?.forceImmediateUIUpdate()
    }

    func handleStreakDragEnd() {
        endDragging()
        stateProvider?.forceImmediateUIUpdate()
    }

    // MARK: - Reordering Methods

    func reorderMetrics(from source: IndexSet, to destination: Int) {
        metricsManager.state.metrics.move(fromOffsets: source, toOffset: destination)
    }

    func reorderStreakItems(from source: IndexSet, to destination: Int) {
        streakManager.state.streakItems.move(fromOffsets: source, toOffset: destination)
    }

    func moveMetric(from sourceIndex: Int, to destinationIndex: Int) {
        guard let stateProvider else { return }
        let metricsToShow = getMetricsToShow()
        let activeMetricsCount = metricsToShow.count - stateProvider.state.ui.removedMetrics.count

        guard sourceIndex != destinationIndex,
              sourceIndex >= 0, sourceIndex < activeMetricsCount,
              destinationIndex >= 0, destinationIndex < activeMetricsCount,
              sourceIndex < metricsToShow.count,
              destinationIndex < metricsToShow.count
        else {
            logger.log(
                level: .error,
                tag: "DashboardGridEditingManager",
                message: "Invalid move indices: from \(sourceIndex) to \(destinationIndex). Active metrics count: \(activeMetricsCount)"
            )
            return
        }

        let visibleMetrics = metricsToShow
        let sourceMetric = visibleMetrics[sourceIndex]
        let destinationMetric = visibleMetrics[destinationIndex]

        guard let sourceActualIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == sourceMetric.id }),
              let destinationActualIndex = metricsManager.state.metrics.firstIndex(where: { $0.id == destinationMetric.id })
        else {
            logger.log(
                level: .error,
                tag: "DashboardGridEditingManager",
                message: "Failed to map visible metrics to actual indices during move. " +
                    "sourceMetricId=\(sourceMetric.id), destinationMetricId=\(destinationMetric.id)"
            )
            return
        }

        let movedMetric = metricsManager.state.metrics.remove(at: sourceActualIndex)
        metricsManager.state.metrics.insert(movedMetric, at: destinationActualIndex)

        let currentActiveCount = min(metricsManager.state.activeMetricsCount, metricsManager.state.metrics.count)
        metricsManager.state.activeMetricsCount = currentActiveCount

        HapticFeedbackService.light()

        logger.log(
            level: .info,
            tag: "DashboardGridEditingManager",
            message: "Moved metric '\(sourceMetric.label)' from \(sourceActualIndex) to \(destinationActualIndex)"
        )
    }
}
