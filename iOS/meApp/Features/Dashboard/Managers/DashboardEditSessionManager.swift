//
//  DashboardEditSessionManager.swift
//  meApp
//

import Foundation

/// Manages edit session snapshots for the dashboard.
/// Stores a snapshot of the dashboard's editable state so it can be
/// compared against the current state or restored on cancel.
@MainActor
final class DashboardEditSessionManager: DashboardEditSessionManaging {

    // MARK: - Stored Snapshot

    private(set) var snapshot: EditSessionSnapshot?

    var hasSnapshot: Bool { snapshot != nil }

    // MARK: - Snapshot Lifecycle

    func takeSnapshot(_ snapshot: EditSessionSnapshot) {
        guard self.snapshot == nil else { return }
        self.snapshot = snapshot
    }

    func updateSnapshot(_ snapshot: EditSessionSnapshot) {
        guard self.snapshot != nil else { return }
        self.snapshot = snapshot
    }

    func clearSnapshot() {
        snapshot = nil
    }

    // MARK: - Comparison

    func hasUnsavedChanges(current: EditSessionSnapshot) -> Bool {
        guard let snapshot = snapshot else { return false }
        return current.metrics.map(\.label) != snapshot.metrics.map(\.label)
            || current.activeMetricsCount != snapshot.activeMetricsCount
            || current.streakItems.map(\.label) != snapshot.streakItems.map(\.label)
            || current.activeStreakItemsCount != snapshot.activeStreakItemsCount
            || current.isGoalCardRemoved != snapshot.isGoalCardRemoved
            || current.goalCardPosition != snapshot.goalCardPosition
            || current.streakGridOrder != snapshot.streakGridOrder
            || current.removedMetrics != snapshot.removedMetrics
            || current.removedStreaks != snapshot.removedStreaks
    }
}
