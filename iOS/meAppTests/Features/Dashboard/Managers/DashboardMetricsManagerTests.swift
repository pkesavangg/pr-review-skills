import Foundation
@testable import meApp
import SwiftUI
import Testing

@Suite(.serialized)
@MainActor
struct DashboardMetricsManagerTests {
    // Test SUT alias; labeled tuple is clearer than a one-off struct.
    // swiftlint:disable:next large_tuple
    private typealias SUT = (
        sut: DashboardMetricsManager,
        accountService: AccountService,
        entryService: EntryService,
        entryRepo: MockEntryRepository
    )

    @Test("init: sets up the default 12 metric cards with placeholders")
    func initSetsUpDefaultMetrics() {
        let (sut, _, _, _) = makeSUT()

        #expect(sut.state.metrics.count == 12)
        #expect(sut.state.activeMetricsCount == 12)
        #expect(sut.state.metrics.allSatisfy { $0.value == DashboardStrings.placeholder })
    }

    @Test("updateMetricsForVisibleAverage: week operations update metric cards with averaged values")
    func updateMetricsForVisibleAverageWeek() async {
        let (sut, _, _, _) = makeSUT()

        await sut.updateMetricsForVisibleAverage(visibleOperations: [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-01",
                bodyFat: 250,
                muscleMass: 820,
                water: 540,
                bmi: 230
            ),
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03-02",
                bodyFat: 270,
                muscleMass: 840,
                water: 560,
                bmi: 250
            )
        ])

        #expect(value(for: DashboardStrings.bmi, in: sut) == "24.0")
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == "26.0")
        #expect(value(for: DashboardStrings.muscle, in: sut) == "83.0")
        #expect(value(for: DashboardStrings.water, in: sut) == "55.0")
    }

    @Test("updateMetricsForVisibleAverage: month operations update metric cards with the current visible period data")
    func updateMetricsForVisibleAverageMonth() async {
        let (sut, _, _, _) = makeSUT()

        await sut.updateMetricsForVisibleAverage(visibleOperations: [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-03",
                bodyFat: 220,
                bmi: 210,
                bmr: 15500,
                pulse: 68
            ),
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-04",
                bodyFat: 260,
                bmi: 230,
                bmr: 16500,
                pulse: 72
            )
        ])

        #expect(value(for: DashboardStrings.bmi, in: sut) == "22.0")
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == "24.0")
        #expect(value(for: DashboardStrings.heartBpm, in: sut) == "70")
        #expect(value(for: DashboardStrings.bmrKcal, in: sut) == "1600")
    }

    @Test("updateMetricsForVisibleAverage: year operations update metric cards with visible averages")
    func updateMetricsForVisibleAverageYear() async {
        let (sut, _, _, _) = makeSUT()

        await sut.updateMetricsForVisibleAverage(visibleOperations: [
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-01",
                metabolicAge: 33,
                proteinPercent: 180,
                skeletalMusclePercent: 390,
            ),
            DashboardTestFixtures.makeSummaryWithAllMetrics(
                period: "2026-12",
                metabolicAge: 37,
                proteinPercent: 220,
                skeletalMusclePercent: 430,
            )
        ])

        #expect(value(for: DashboardStrings.protein, in: sut) == "20.0")
        #expect(value(for: DashboardStrings.skelMuscle, in: sut) == "41.0")
        #expect(value(for: DashboardStrings.metAge, in: sut) == "35")
    }

    @Test("updateMetricsForVisibleAverage: total with no operations restores placeholders")
    func updateMetricsForVisibleAverageTotalEmpty() async {
        let (sut, _, _, _) = makeSUT()

        sut.state.metrics[0] = DashboardTestFixtures.makeMetricItem(
            value: "25.0",
            label: DashboardStrings.bmi,
            unit: nil,
            icon: AppAssets.bmiIcon
        )

        await sut.updateMetricsForVisibleAverage(visibleOperations: [])

        #expect(value(for: DashboardStrings.bmi, in: sut) == DashboardStrings.placeholder)
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == DashboardStrings.placeholder)
        #expect(value(for: DashboardStrings.heartBpm, in: sut) == DashboardStrings.placeholder)
    }

    @Test("getMetricsToShow: returns active list in normal mode and appends removed cards in edit mode")
    func getMetricsToShowRespectsVisibilityAndEditMode() {
        let (sut, _, _, _) = makeSUT()
        sut.updateMetricsOrder(from: ["bmi", "bodyFat", "muscleMass", "water"])
        let removed = Set([DashboardStrings.bodyFat])

        let visible = sut.getMetricsToShow(
            isEditMode: false,
            dashboardType: .dashboard4,
            removedMetrics: removed
        )
        let editable = sut.getMetricsToShow(
            isEditMode: true,
            dashboardType: .dashboard4,
            removedMetrics: removed
        )

        #expect(visible.map(\.label) == [
            DashboardStrings.bmi,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])
        #expect(Array(editable.map(\.label).prefix(3)) == [
            DashboardStrings.bmi,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])
        #expect(editable.last?.label == DashboardStrings.bodyFat)
    }

    @Test("handleMetricLongPress: updates the selected metric bindings immediately")
    func handleMetricLongPressUpdatesBindings() {
        let (sut, _, _, _) = makeSUT()
        sut.state.metrics = [
            DashboardTestFixtures.makeMetricItem(value: "24.0", label: DashboardStrings.bmi, unit: nil),
            DashboardTestFixtures.makeMetricItem(value: "26.0", label: DashboardStrings.bodyFat, unit: "%")
        ]

        var selectedEntry: Entry?
        var selectedMetric: BodyMetric?

        sut.handleMetricLongPress(
            for: DashboardStrings.bodyFat,
            selectedEntry: Binding(get: { selectedEntry }, set: { selectedEntry = $0 }),
            selectedMetric: Binding(get: { selectedMetric }, set: { selectedMetric = $0 })
        )

        #expect(selectedMetric == .bodyFat)
        #expect(selectedEntry?.scaleEntry?.bodyFat == 26)
    }

    @Test("handleSelectedMetricInfoChange: updates the selected bindings asynchronously")
    func handleSelectedMetricInfoChangeUpdatesBindings() async {
        let (sut, _, _, _) = makeSUT()
        sut.state.metrics = [
            DashboardTestFixtures.makeMetricItem(value: "24.0", label: DashboardStrings.bmi, unit: nil),
            DashboardTestFixtures.makeMetricItem(value: "1600", label: DashboardStrings.bmrKcal, unit: DashboardStrings.kcalUnitSymbol)
        ]

        var selectedEntry: Entry?
        var selectedMetric: BodyMetric?

        await sut.handleSelectedMetricInfoChange(
            DashboardStrings.bmrKcal,
            selectedEntry: Binding(get: { selectedEntry }, set: { selectedEntry = $0 }),
            selectedMetric: Binding(get: { selectedMetric }, set: { selectedMetric = $0 })
        )

        #expect(selectedMetric == .bmr)
        #expect(selectedEntry?.scaleEntryMetric?.bmr == 16000)
    }

    @Test("updateMetrics: fallback cache is reused until invalidated")
    func updateMetricsFallbackCacheHitAndInvalidation() async throws {
        let (sut, accountService, _, entryRepo) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        entryRepo.entries = [
            EntryTestFixtures.makeEntry(
                timestamp: "2026-03-01T08:00:00Z",
                bodyFat: 250
            )
        ]

        let missingBodyFat = EntryTestFixtures.makeEntry(
            timestamp: "2026-03-10T08:00:00Z",
            bodyFat: nil
        )

        try await sut.updateMetrics(with: missingBodyFat)
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == "25.0")

        entryRepo.entries = [
            EntryTestFixtures.makeEntry(
                timestamp: "2026-03-12T08:00:00Z",
                bodyFat: 300
            )
        ]

        try await sut.updateMetrics(with: missingBodyFat)
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == "25.0")

        sut.clearFallbackCache()
        try await sut.updateMetrics(with: missingBodyFat)
        #expect(value(for: DashboardStrings.bodyFat, in: sut) == "30.0")
    }

    @Test("loadMetricsFromAPI and resetMetricsToDefaults: respect dashboard type and restore API ordering")
    func loadMetricsFromAPIAndResetDefaults() async throws {
        let (sut, accountService, _, _) = makeSUT(dashboardType: .dashboard12)
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "bmi,bodyFat,muscleMass,water",
            dashboardType: "dashboard4"
        )

        try await sut.loadMetricsFromAPI()
        #expect(sut.state.dashboardType == .dashboard4)
        #expect(sut.state.activeMetricsCount == 4)
        #expect(sut.state.metrics.prefix(4).map(\.label) == [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])

        sut.state.metrics.swapAt(0, 1)
        try await sut.resetMetricsToDefaults()
        #expect(sut.state.metrics.prefix(4).map(\.label) == [
            DashboardStrings.bmi,
            DashboardStrings.bodyFat,
            DashboardStrings.muscle,
            DashboardStrings.water
        ])
    }

    @Test("metric state helpers: toggle visibility, reorder metrics, and derive removed labels")
    func metricStateHelpers() async throws {
        let (sut, _, _, _) = makeSUT(dashboardType: .dashboard4)
        sut.updateMetricsOrder(from: ["bmi", "bodyFat", "muscleMass", "water"])

        try sut.toggleMetricVisibilitySync(at: 1)
        #expect(sut.state.activeMetricsCount == 3)
        #expect(sut.getRemovedMetricLabels() == Set([DashboardStrings.bodyFat]))

        try await sut.toggleMetricVisibility(at: 3)
        #expect(sut.state.activeMetricsCount == 4)

        try await sut.reorderMetrics(from: IndexSet(integer: 0), to: 3)
        #expect(Array(sut.state.metrics.map(\.label).prefix(4)) == [
            DashboardStrings.muscle,
            DashboardStrings.water,
            DashboardStrings.bmi,
            DashboardStrings.bodyFat
        ])
    }

    @Test("metric utility helpers: placeholders, summary lookup, and grid columns stay consistent")
    func metricUtilityHelpers() {
        let (sut, _, _, _) = makeSUT(dashboardType: .dashboard12)
        sut.state.metrics = [
            DashboardTestFixtures.makeMetricItem(value: "24.0", label: DashboardStrings.bmi, unit: nil),
            DashboardTestFixtures.makeMetricItem(value: "72", label: DashboardStrings.heartBpm, unit: DashboardStrings.bpmUnitSymbol)
        ]

        sut.setPlaceholdersForAllMetrics()
        let summary = DashboardTestFixtures.makeSummaryWithAllMetrics(
            bmi: 230,
            pulse: 70
        )

        #expect(value(for: DashboardStrings.bmi, in: sut) == DashboardStrings.placeholder)
        #expect(value(for: DashboardStrings.heartBpm, in: sut) == DashboardStrings.placeholder)
        #expect(sut.getMetricValue(for: DashboardStrings.bmi, from: summary) == 230)
        #expect(sut.getMetricValue(for: DashboardStrings.heartBpm, from: summary) == 70)
        #expect(sut.getMetricGridColumns(for: .dashboard12).count == sut.getMetricGridColumnCount(for: .dashboard12))
    }

    private func makeSUT(
        dashboardType: DashboardType = .dashboard12
    ) -> SUT {
        TestDependencyContainer.reset()

        let accountService = AccountService(
            apiRepo: MockAccountAPIRepository(),
            localRepo: MockAccountRepository(),
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: MockNetworkMonitor(isConnected: true),
            performInitialLoad: false
        )
        let logger = LoggerService()
        let entryRepo = MockEntryRepository()
        let entryService = EntryService(
            accountService: accountService,
            localRepo: entryRepo,
            localKVRepo: MockEntrySyncStore(),
            remoteRepo: MockEntryRepositoryAPI()
        )

        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(logger as LoggerService)
        DependencyContainer.shared.register(entryService as EntryService)

        let sut = DashboardMetricsManager()
        sut.state.dashboardType = dashboardType
        return (sut, accountService, entryService, entryRepo)
    }

    private func value(for label: String, in sut: DashboardMetricsManager) -> String? {
        sut.state.metrics.first { $0.label == label }?.value
    }
}
