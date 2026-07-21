//
//  DashboardStoreChartEngineTests.swift
//  meAppTests
//
//  MOB-1515 (AC #1 + #2) — store-level dispatch for the v2 chart-engine seams on `DashboardStore`:
//  `selectPoint(at:)` clear/set, and `settleChart` routing — an in-place y-axis settle for a weight-only
//  chart (series + x-geometry byte-identical) vs a full rebuild when a body-comp metric is co-plotted.
//  The pure `ChartModel` settle parity (`withYAxisAndTicks`) is covered in `ChartPrepTests`; here we assert
//  the STORE picks the right path. Uses the shared `DashboardManagerTestSupport` harness.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct DashboardStoreChartEngineTests {

    private func makeDate(_ day: Int, hour: Int = 8) -> Date {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        return cal.date(from: DateComponents(year: 2026, month: 3, day: day, hour: hour))
            ?? Date(timeIntervalSinceReferenceDate: 0)
    }

    private func daily(_ days: ClosedRange<Int>, bodyFat: Bool = false) -> [BathScaleWeightSummary] {
        days.map { day in
            DashboardTestFixtures.makeSummary(
                period: String(format: "2026-03-%02d", day),
                entryTimestamp: ISO8601DateFormatter().string(from: makeDate(day)),
                date: makeDate(day),
                weight: Double(1800 + day),
                bodyFat: bodyFat ? Double(240 + day) : nil
            )
        }
    }

    private func makeStore() -> DashboardManagerTestSupport.StoreSUT {
        DashboardManagerTestSupport.makeStore(
            cacheManager: MockDashboardCacheManager(),
            formatter: MockDashboardFormatter()
        )
    }

    // MARK: - selectPoint

    @Test("selectPoint(at: nil) clears the graph selection")
    func selectPointNilClearsSelection() async {
        let sut = makeStore()
        let store = sut.store
        await DashboardManagerTestSupport.loadData(into: store, entryService: sut.entryService, daily: daily(1...10))
        store.state.graph.selectedPeriod = .month

        store.selectPoint(at: makeDate(5))
        store.selectPoint(at: nil)

        #expect(store.graphManager.state.selectedPoint == nil)
    }

    @Test("selectPoint(at: entryDate) selects that reading")
    func selectPointSelectsEntry() async {
        let sut = makeStore()
        let store = sut.store
        await DashboardManagerTestSupport.loadData(into: store, entryService: sut.entryService, daily: daily(1...10))
        store.state.graph.selectedPeriod = .month

        store.selectPoint(at: makeDate(6))

        #expect(store.graphManager.state.selectedPoint?.date == makeDate(6))
    }

    // MARK: - settleChart dispatch

    @Test("settleChart keeps series + geometry byte-identical for a weight-only chart")
    func settleWeightOnlyIsInPlace() async throws {
        let sut = makeStore()
        let store = sut.store
        await DashboardManagerTestSupport.loadData(into: store, entryService: sut.entryService, daily: daily(1...20))
        store.state.graph.selectedPeriod = .month
        store.state.ui.selectedMetricLabel = DashboardStrings.weight // no co-plot

        store.rebuildChartModel(scrollPosition: makeDate(5))
        let before = try #require(store.chartModel)

        store.settleChart(scrollPosition: makeDate(15))
        let after = try #require(store.chartModel)

        // Only the y-axis / windowed ticks may change; the scroll region is untouched.
        #expect(after.seriesPoints == before.seriesPoints)
        #expect(after.xDomain == before.xDomain)
        #expect(after.visibleDomainLength == before.visibleDomainLength)
        #expect(after.dataFingerprint == before.dataFingerprint)
    }

    @Test("settleChart falls back to a full rebuild when a body-comp metric becomes co-plotted")
    func settleCoPlottedRebuilds() async throws {
        let sut = makeStore()
        let store = sut.store
        await DashboardManagerTestSupport.loadData(
            into: store, entryService: sut.entryService, daily: daily(1...20, bodyFat: true)
        )
        store.state.graph.selectedPeriod = .month
        store.state.ui.selectedMetricLabel = DashboardStrings.weight

        store.rebuildChartModel(scrollPosition: makeDate(5))
        let before = try #require(store.chartModel)
        #expect(before.orderedSeriesNames == [DashboardStrings.weight]) // single series to start

        // Co-plot a metric, then settle: the guard fails → a full rebuild runs and adds the second series
        // (an in-place y-axis settle could never introduce a new series).
        store.state.ui.selectedMetricLabel = DashboardStrings.bodyFat
        store.settleChart(scrollPosition: makeDate(15))
        let after = try #require(store.chartModel)

        #expect(after.orderedSeriesNames.contains(DashboardStrings.bodyFat))
    }
}
