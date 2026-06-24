import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardStoreGraphSelectionSyncTests {

    @Test
    func displayWeightPrefersSelectedPointOverCrosshairDate() async throws {
        let selectedPoint = makeSummary(
            date: makeDate(2026, 4, 20),
            weight: 182,
            bmi: 24.4
        )
        let store = DashboardStore(lightweight: true)

        store.state.graph.selectedPeriod = .month
        store.state.graph.selectedPoint = selectedPoint
        store.state.graph.selectedXValue = makeDate(2026, 4, 10)

        let expectedWeight = store.goalManager.convertWeightToDisplay(Int(selectedPoint.weight))
        #expect(store.displayWeight == expectedWeight)
        #expect(store.currentMetricRefreshKind() == .selectedPoint)
    }

    @Test
    func exactChartSelectionUpdatesSelectedPointMetricsForAllPeriods() async throws {
        defer { clearEntrySummaries() }

        let dailyOlder = makeSummary(
            date: makeDate(2026, 4, 18),
            weight: 176,
            bmi: 22.1
        )
        let dailyLatest = makeSummary(
            date: makeDate(2026, 4, 22),
            weight: 182,
            bmi: 24.4
        )
        let monthlyOlder = makeSummary(
            date: makeDate(2026, 3, 1),
            weight: 170,
            bmi: 21.0
        )
        let monthlyLatest = makeSummary(
            date: makeDate(2026, 4, 1),
            weight: 185,
            bmi: 25.2
        )

        let store = await makeStore(
            daily: [dailyOlder, dailyLatest],
            monthly: [monthlyOlder, monthlyLatest]
        )

        let scenarios: [(TimePeriod, BathScaleWeightSummary)] = [
            (.week, dailyLatest),
            (.month, dailyLatest),
            (.year, monthlyLatest),
            (.total, monthlyLatest)
        ]

        for (period, latestPoint) in scenarios {
            store.state.graph.selectedPeriod = period
            await store.handleChartSelection(at: latestPoint.date)

            try await waitUntil(timeout: 2.0) { store.state.ui.hasLoadedMetricValues }

            #expect(store.state.graph.selectedPoint?.date == latestPoint.date)
            #expect(store.currentMetricRefreshKind() == .selectedPoint)

            let expectedWeight = store.goalManager.convertWeightToDisplay(Int(latestPoint.weight))
            #expect(store.displayWeight == expectedWeight)

            let expectedBMI = BodyMetricsConvertor.convert(
                latestPoint.bmi,
                shouldCompose: true,
                wholeNumber: false,
                fallbackValue: nil
            )
            let actualBMI = store.metricsManager.state.metrics.first(where: { $0.label == DashboardStrings.bmi })?.value
            #expect(actualBMI == expectedBMI)

            store.state.ui.hasLoadedMetricValues = false
            await store.graphManager.handleChartSelection(at: nil)
        }
    }

    @Test
    func monthViewModelAppliesExistingStoreLatestSelectionAfterConfiguration() async throws {
        defer { clearEntrySummaries() }

        let older = makeSummary(
            date: makeDate(2026, 4, 18),
            weight: 176,
            bmi: 22.1
        )
        let latest = makeSummary(
            date: makeDate(2026, 4, 22),
            weight: 182,
            bmi: 24.4
        )

        let store = await makeStore(
            daily: [older, latest],
            monthly: []
        )

        store.updateSelectedPeriod(.month)
        store.state.graph.selectedPoint = latest
        store.state.graph.selectedXValue = latest.date
        store.state.graph.showCrosshair = true

        let viewModel = MonthSectionViewModel()
        viewModel.configure(with: store)
        viewModel.applyProgrammaticSelection(at: store.state.graph.selectedXValue)

        #expect(viewModel.showCrosshair)
        #expect(viewModel.selectedPoint?.entryTimestamp == latest.entryTimestamp)
        #expect(viewModel.selectedDate == viewModel.plotXDate(for: latest.date))
    }

    @Test
    func initializeChartSelectsLatestEntryWhenMonthIsVisibleInitially() async throws {
        defer { clearEntrySummaries() }

        let older = makeSummary(
            date: makeDate(2026, 4, 18),
            weight: 176,
            bmi: 22.1
        )
        let latest = makeSummary(
            date: makeDate(2026, 4, 22),
            weight: 182,
            bmi: 24.4
        )

        let store = await makeStore(
            daily: [older, latest],
            monthly: []
        )

        store.state.graph.selectedPeriod = .month
        store.state.graph.selectedPoint = nil
        store.state.graph.selectedXValue = nil
        store.state.ui.hasInitializedChart = false
        store.state.ui.hasLandedInitialSelection = false

        store.initializeChart()

        try await waitUntil(timeout: 5.0) {
            store.state.graph.selectedPoint?.entryTimestamp == latest.entryTimestamp &&
            store.state.graph.selectedXValue.map { Calendar.current.isDate($0, inSameDayAs: latest.date) } == true &&
            store.state.ui.hasLandedInitialSelection
        }

        #expect(store.state.graph.selectedPoint?.entryTimestamp == latest.entryTimestamp)
        #expect(store.state.graph.selectedXValue.map { Calendar.current.isDate($0, inSameDayAs: latest.date) } == true)
        #expect(store.state.ui.hasLandedInitialSelection)
    }

}

// MARK: - Helpers

/// Polls `condition` every 10ms until it returns true or `timeout` seconds elapses.
@MainActor
private func waitUntil(timeout: TimeInterval = 2.0, condition: @MainActor () -> Bool) async throws {
    let deadline = Date().addingTimeInterval(timeout)
    while !condition() {
        guard Date() < deadline else {
            Issue.record("waitUntil timed out after \(timeout)s")
            return
        }
        try await Task.sleep(nanoseconds: 10_000_000) // 10ms
    }
}

@MainActor
private func makeStore(
    daily: [BathScaleWeightSummary],
    monthly: [BathScaleWeightSummary]
) async -> DashboardStore {
    _ = ServiceRegistry.shared
    EntryService.shared.dailySummaries = daily
    EntryService.shared.monthlySummaries = monthly

    let store = DashboardStore(lightweight: true)
    store.state.ui.hasLoadedDashboardConfig = true

    // Let any init-time async work settle before returning.
    try? await waitUntil(timeout: 5.0) { store.state.ui.hasLoadedMetricValues }
    return store
}

@MainActor
private func clearEntrySummaries() {
    EntryService.shared.dailySummaries = []
    EntryService.shared.monthlySummaries = []
}

private func makeDate(_ year: Int, _ month: Int, _ day: Int) -> Date {
    var components = DateComponents()
    components.calendar = Calendar(identifier: .gregorian)
    components.timeZone = TimeZone(secondsFromGMT: 0)
    components.year = year
    components.month = month
    components.day = day
    return components.date!
}

private func makeSummary(
    date: Date,
    weight: Double,
    bmi: Double
) -> BathScaleWeightSummary {
    let formatter = ISO8601DateFormatter()
    formatter.timeZone = TimeZone(secondsFromGMT: 0)

    return BathScaleWeightSummary(
        accountId: "test-account",
        period: DateTimeTools.formatter("yyyy-MM-dd").string(from: date),
        entryTimestamp: formatter.string(from: date),
        date: date,
        count: 1,
        weight: weight,
        bmi: bmi
    )
}
