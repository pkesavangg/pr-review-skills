//
//  GoalStreakGridBuilder.swift
//  meApp
//
//  Created by Lakshmi Priya on 07/08/25.
//

import Foundation

/// Pure builder that converts dashboard state into a `MileStoneGridModel`.
/// Extracted from `GoalStreakGridUIKitView` so the mapping is unit-testable
/// without standing up a full `DashboardStore`.
enum GoalStreakGridBuilder {

    /// Snapshot of the minimal inputs the grid layout needs. Stateless.
    struct Inputs {
        let isEditMode: Bool
        let hasLoadedProgressMetrics: Bool
        let managerStreaks: [MetricItem]
        let streakItemsToShow: [MetricItem]
        let goalCardPosition: Int
        let streakGridOrder: [String]
        let isGoalCardRemoved: Bool
        let isStreakRemoved: (String) -> Bool
        let isTablet: Bool
    }

    static func build(inputs: Inputs) -> MileStoneGridModel {
        // Use manager streaks directly before API loads to avoid premature filtering
        let managerStreaks = inputs.managerStreaks
        let allStreaks: [MetricItem]
        if !inputs.hasLoadedProgressMetrics && !inputs.isEditMode && !managerStreaks.isEmpty {
            allStreaks = managerStreaks
        } else {
            allStreaks = inputs.streakItemsToShow
        }

        let hasStreaks = !allStreaks.isEmpty
        let hasValidGoal = !inputs.isGoalCardRemoved
        let hasStreakItemsInManager = !managerStreaks.isEmpty

        if !hasStreaks && !hasValidGoal {
            return MileStoneGridModel(mileStones: [])
        }

        // Fallback to manager streaks if API hasn't loaded yet
        if !inputs.isEditMode,
           !inputs.hasLoadedProgressMetrics,
           !hasStreaks,
           !hasStreakItemsInManager {
            return MileStoneGridModel(mileStones: hasValidGoal ? [.goalCard] : [])
        }

        let ordered = orderedStreaks(
            from: allStreaks,
            using: inputs.streakGridOrder,
            isEditMode: inputs.isEditMode
        )
        let split = splitByRemoval(ordered, isStreakRemoved: inputs.isStreakRemoved)
        // Source of truth for "any streak is removed". In non-edit mode `split.removed` is
        // empty because `streakItemsToShow` is pre-filtered, so we must consult the full
        // manager list — otherwise `effectiveGoalIndex` takes the wrong branch on save.
        let hasRemovedStreaks = inputs.managerStreaks.contains { inputs.isStreakRemoved($0.label) }
        let widgets = buildWidgetsWithGoalCard(
            activeStreaks: split.active,
            removedStreaks: split.removed,
            goalCardPos: inputs.goalCardPosition,
            isEditMode: inputs.isEditMode,
            isGoalCardRemoved: inputs.isGoalCardRemoved,
            isTablet: inputs.isTablet,
            hasRemovedStreaks: hasRemovedStreaks
        )

        var gridModel = MileStoneGridModel(mileStones: widgets)
        let spanCount = inputs.isTablet ? 4 : 2
        gridModel.reorderGrid(spanCount: spanCount, hasRemovedStreaks: hasRemovedStreaks)
        return gridModel
    }

    // MARK: - Pure helpers

    private static func orderedStreaks(
        from all: [MetricItem],
        using order: [String],
        isEditMode: Bool
    ) -> [MetricItem] {
        if isEditMode {
            // In edit mode, show ALL streaks (including removed ones).
            // Order them according to the saved order, then append any missing ones at the end.
            var ordered = order.compactMap { id in all.first { $0.id.uuidString == id } }
            let missing = all.filter { streak in !order.contains(streak.id.uuidString) }
            ordered.append(contentsOf: missing)
            return ordered
        } else {
            // Show streaks in order (removed streaks already filtered by streakItemsToShow).
            guard !order.isEmpty else { return all }
            let ordered = order.compactMap { id in all.first { $0.id.uuidString == id } }
            // Append missing streaks if order is incomplete.
            if ordered.count < all.count {
                let orderedIds = Set(ordered.map { $0.id.uuidString })
                let missing = all.filter { !orderedIds.contains($0.id.uuidString) }
                return ordered + missing
            }
            return ordered
        }
    }

    private static func splitByRemoval(
        _ streaks: [MetricItem],
        isStreakRemoved: (String) -> Bool
    ) -> (active: [MetricItem], removed: [MetricItem]) {
        var active: [MetricItem] = []
        var removed: [MetricItem] = []
        active.reserveCapacity(streaks.count)
        removed.reserveCapacity(streaks.count)
        for streak in streaks {
            if isStreakRemoved(streak.label) { removed.append(streak) } else { active.append(streak) }
        }
        return (active, removed)
    }

    private static func effectiveGoalIndex(
        clampedGoalPos: Int,
        streakCount: Int,
        columns: Int,
        isEditMode: Bool,
        hasRemovedStreaks: Bool
    ) -> Int {
        // Android-parity: when any streaks are removed, preserve the user's saved position
        // as-is. Re-adding a streak must not yank the goal card to a different row.
        if hasRemovedStreaks { return clampedGoalPos }

        let lastRowIncomplete = (streakCount % columns) != 0
        if lastRowIncomplete { return clampedGoalPos }

        if isEditMode {
            // Full rows in edit mode: odd slot snaps down to the preceding even slot.
            return max(0, (clampedGoalPos % 2 == 0) ? clampedGoalPos : clampedGoalPos - 1)
        }
        // Full rows out of edit mode: enforce row-start for layout consistency.
        return (clampedGoalPos / columns) * columns
    }

    private static func buildWidgetsWithGoalCard(
        activeStreaks: [MetricItem],
        removedStreaks: [MetricItem],
        goalCardPos: Int,
        isEditMode: Bool,
        isGoalCardRemoved: Bool,
        isTablet: Bool,
        hasRemovedStreaks: Bool
    ) -> [MileStoneType] {
        var widgets: [MileStoneType] = []

        if isGoalCardRemoved {
            widgets.append(contentsOf: activeStreaks.map { .streak($0) })
        } else {
            let streakCount = activeStreaks.count
            if streakCount == 0 {
                if !isGoalCardRemoved { widgets.append(.goalCard) }
            } else {
                let columns = isTablet ? 4 : 2
                let maxPosition = streakCount
                let clampedGoal = min(goalCardPos, maxPosition)
                let goalIndex = effectiveGoalIndex(
                    clampedGoalPos: clampedGoal,
                    streakCount: streakCount,
                    columns: columns,
                    isEditMode: isEditMode,
                    hasRemovedStreaks: hasRemovedStreaks
                )

                var goalAdded = false
                for i in 0...maxPosition {
                    if i == goalIndex && !goalAdded {
                        widgets.append(.goalCard)
                        goalAdded = true
                    }
                    if i < streakCount {
                        widgets.append(.streak(activeStreaks[i]))
                    }
                }
                if !goalAdded { widgets.append(.goalCard) }
            }
        }

        // In edit mode, show removed streaks after the active section, and place goal card at the end if removed.
        if isEditMode {
            widgets.append(contentsOf: removedStreaks.map { .streak($0) })
            if isGoalCardRemoved { widgets.append(.goalCard) }
        }

        return widgets
    }
}
