import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardEditSessionManagerTests {

    private func makeSnapshot(
        metricLabels: [String] = [DashboardStrings.bmi, DashboardStrings.water],
        activeMetricsCount: Int? = nil,
        streakLabels: [String] = [DashboardStrings.currentStreak, DashboardStrings.longestStreak],
        activeStreakItemsCount: Int? = nil,
        isGoalCardRemoved: Bool = false,
        goalCardPosition: Int = 0,
        streakGridOrder: [String]? = nil,
        removedMetrics: Set<String> = [],
        removedStreaks: Set<String> = []
    ) -> EditSessionSnapshot {
        let metrics = metricLabels.map { DashboardTestFixtures.makeMetricItem(label: $0) }
        let streakItems = streakLabels.map { DashboardTestFixtures.makeMetricItem(label: $0) }

        return EditSessionSnapshot(
            metrics: metrics,
            activeMetricsCount: activeMetricsCount ?? metrics.count,
            streakItems: streakItems,
            activeStreakItemsCount: activeStreakItemsCount ?? streakItems.count,
            isGoalCardRemoved: isGoalCardRemoved,
            goalCardPosition: goalCardPosition,
            streakGridOrder: streakGridOrder ?? streakItems.map(\.id.uuidString),
            removedMetrics: removedMetrics,
            removedStreaks: removedStreaks
        )
    }

    @Test("takeSnapshot stores the first snapshot and ignores later takes")
    func takeSnapshotStoresFirstSnapshotOnly() {
        let sut = DashboardEditSessionManager()
        let first = makeSnapshot(metricLabels: [DashboardStrings.bmi])
        let second = makeSnapshot(metricLabels: [DashboardStrings.water])

        sut.takeSnapshot(first)
        sut.takeSnapshot(second)

        #expect(sut.hasSnapshot == true)
        #expect(sut.snapshot?.metrics.map(\.label) == [DashboardStrings.bmi])
    }

    @Test("updateSnapshot requires an existing snapshot before replacing it")
    func updateSnapshotRequiresExistingSnapshot() {
        let sut = DashboardEditSessionManager()
        let initial = makeSnapshot(metricLabels: [DashboardStrings.bmi])
        let updated = makeSnapshot(metricLabels: [DashboardStrings.water, DashboardStrings.bodyFat])

        sut.updateSnapshot(updated)
        #expect(sut.snapshot == nil)

        sut.takeSnapshot(initial)
        sut.updateSnapshot(updated)

        #expect(sut.snapshot?.metrics.map(\.label) == [DashboardStrings.water, DashboardStrings.bodyFat])
    }

    @Test("clearSnapshot removes the stored edit state")
    func clearSnapshotRemovesStoredState() {
        let sut = DashboardEditSessionManager()
        sut.takeSnapshot(makeSnapshot())

        sut.clearSnapshot()

        #expect(sut.hasSnapshot == false)
        #expect(sut.snapshot == nil)
    }

    @Test("hasUnsavedChanges returns false when no snapshot exists")
    func hasUnsavedChangesWithoutSnapshotIsFalse() {
        let sut = DashboardEditSessionManager()

        #expect(sut.hasUnsavedChanges(current: makeSnapshot()) == false)
    }

    @Test("hasUnsavedChanges returns false when the current state matches the snapshot")
    func hasUnsavedChangesWhenStatesMatchIsFalse() {
        let sut = DashboardEditSessionManager()
        let snapshot = makeSnapshot(
            activeMetricsCount: 1,
            activeStreakItemsCount: 1,
            goalCardPosition: 2,
            streakGridOrder: ["a", "b"],
            removedMetrics: [DashboardStrings.bodyFat],
            removedStreaks: [DashboardStrings.longestStreak]
        )
        sut.takeSnapshot(snapshot)

        #expect(sut.hasUnsavedChanges(current: snapshot) == false)
    }

    @Test("hasUnsavedChanges detects metric label and order differences")
    func hasUnsavedChangesDetectsMetricDifferences() {
        let sut = DashboardEditSessionManager()
        sut.takeSnapshot(makeSnapshot(metricLabels: [DashboardStrings.bmi, DashboardStrings.water]))

        let current = makeSnapshot(metricLabels: [DashboardStrings.water, DashboardStrings.bmi])

        #expect(sut.hasUnsavedChanges(current: current) == true)
    }

    @Test("hasUnsavedChanges detects metric removal and active-count differences")
    func hasUnsavedChangesDetectsMetricRemovalDifferences() {
        let sut = DashboardEditSessionManager()
        sut.takeSnapshot(makeSnapshot(activeMetricsCount: 2, removedMetrics: []))

        let current = makeSnapshot(activeMetricsCount: 1, removedMetrics: [DashboardStrings.water])

        #expect(sut.hasUnsavedChanges(current: current) == true)
    }

    @Test("hasUnsavedChanges detects streak labels, order, and removal differences")
    func hasUnsavedChangesDetectsStreakDifferences() {
        let sut = DashboardEditSessionManager()
        let original = makeSnapshot(
            streakLabels: [DashboardStrings.currentStreak, DashboardStrings.longestStreak, "lb/week"],
            activeStreakItemsCount: 3,
            streakGridOrder: ["s1", "s2", "s3"],
            removedStreaks: []
        )
        sut.takeSnapshot(original)

        let current = makeSnapshot(
            streakLabels: ["lb/week", DashboardStrings.currentStreak, DashboardStrings.longestStreak],
            activeStreakItemsCount: 2,
            streakGridOrder: ["s3", "s1", "s2"],
            removedStreaks: [DashboardStrings.longestStreak]
        )

        #expect(sut.hasUnsavedChanges(current: current) == true)
    }

    @Test("hasUnsavedChanges detects goal-card removal and position differences")
    func hasUnsavedChangesDetectsGoalCardDifferences() {
        let sut = DashboardEditSessionManager()
        sut.takeSnapshot(makeSnapshot(isGoalCardRemoved: false, goalCardPosition: 0))

        let current = makeSnapshot(isGoalCardRemoved: true, goalCardPosition: 3)

        #expect(sut.hasUnsavedChanges(current: current) == true)
    }
}
