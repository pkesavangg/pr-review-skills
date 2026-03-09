//
//  DashboardEditSessionManagerProtocol.swift
//  meApp
//

import Foundation

/// Captures a snapshot of the dashboard's editable state for synchronous revert.
struct EditSessionSnapshot {
    let metrics: [MetricItem]
    let activeMetricsCount: Int
    let streakItems: [MetricItem]
    let activeStreakItemsCount: Int
    let isGoalCardRemoved: Bool
    let goalCardPosition: Int
    let streakGridOrder: [String]
    let removedMetrics: Set<String>
    let removedStreaks: Set<String>
}

/// Protocol defining edit session management operations for testability.
@MainActor
protocol DashboardEditSessionManaging {
    /// Whether an edit snapshot currently exists
    var hasSnapshot: Bool { get }

    /// The stored snapshot, if any
    var snapshot: EditSessionSnapshot? { get }

    /// Takes a snapshot of the current editable state
    func takeSnapshot(_ snapshot: EditSessionSnapshot)

    /// Updates the stored snapshot to reflect the latest saved state
    func updateSnapshot(_ snapshot: EditSessionSnapshot)

    /// Clears the stored snapshot
    func clearSnapshot()

    /// Compares the given current state against the stored snapshot
    func hasUnsavedChanges(current: EditSessionSnapshot) -> Bool
}
