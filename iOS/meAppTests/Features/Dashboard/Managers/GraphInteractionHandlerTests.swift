import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct GraphInteractionHandlerTests {
    private func makeSUT() -> GraphInteractionHandler {
        GraphInteractionHandler()
    }

    private func makeRenderConfig() -> GraphRenderingConfiguration {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!
        return GraphRenderingConfiguration(calendar: calendar)
    }

    @Test("scroll buffering: capture and consume clears the buffered position")
    func scrollBuffering() {
        let sut = makeSUT()
        let position = date("2026-03-10")

        sut.captureScrollPosition(position)

        #expect(sut.consumeBufferedScrollPosition() == position)
        #expect(sut.consumeBufferedScrollPosition() == nil)
    }

    @Test("visible operations cache: returns cached results for small movement and invalidates past the last entry")
    func visibleOperationsCache() {
        let sut = makeSUT()
        let preparer = GraphDataPreparer()
        let ops = DashboardTestFixtures.makeSortedDailySummaries()

        let first = sut.visibleOperations(
            from: ops,
            scrollPosition: date("2026-03-01"),
            period: .month,
            visibleDomainLength: 30 * 24 * 60 * 60,
            dataPreparer: preparer
        )
        let second = sut.visibleOperations(
            from: ops,
            scrollPosition: isoDate("2026-03-01T12:00:00Z"),
            period: .month,
            visibleDomainLength: 30 * 24 * 60 * 60,
            dataPreparer: preparer
        )
        let third = sut.visibleOperations(
            from: ops,
            scrollPosition: date("2026-04-20"),
            period: .month,
            visibleDomainLength: 30 * 24 * 60 * 60,
            dataPreparer: preparer
        )

        #expect(first.map(\.period) == second.map(\.period))
        #expect(third.isEmpty)
    }

    @Test("x-axis cache: reuses cached values until movement exceeds threshold or cache is invalidated")
    func xAxisCache() {
        let sut = makeSUT()
        let config = makeRenderConfig()
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-03-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-03-31"))
        ]

        let first = sut.xAxisValues(for: .month, from: ops, scrollPosition: date("2026-03-01"), renderConfig: config)
        let second = sut.xAxisValues(for: .month, from: ops, scrollPosition: isoDate("2026-03-02T00:00:00Z"), renderConfig: config)
        sut.invalidateXAxisCache()
        let third = sut.xAxisValues(for: .month, from: ops, scrollPosition: date("2026-03-01"), renderConfig: config)

        #expect(first == second)
        #expect(third == first)
    }

    @Test("selection resolution: returns closest point and interpolated weight when requested")
    func selectionResolution() {
        let sut = makeSUT()
        let preparer = GraphDataPreparer()
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-03-01"), weight: 1800),
            DashboardTestFixtures.makeSummary(date: date("2026-03-03"), weight: 1820)
        ]

        let selection = sut.resolveSelection(
            at: date("2026-03-02"),
            from: ops,
            dataPreparer: preparer,
            convertWeight: DashboardTestFixtures.convertToLbs
        )
        let interpolated = sut.resolveSelectionWithInterpolation(
            at: date("2026-03-02"),
            from: ops,
            dataPreparer: preparer,
            period: .week,
            isWeightlessMode: false,
            anchorWeight: nil,
            convertWeight: DashboardTestFixtures.convertToLbs
        )

        #expect(selection?.summary.period == "2026-03-01")
        #expect(selection?.displayWeight == 180)
        #expect(interpolated?.summary.period == "2026-03-01")
        #expect(interpolated?.interpolatedWeight != nil)
    }

    @Test("correctedScrollPosition: returns nil when latest data is visible and a new position when it is not")
    func correctedScrollPosition() {
        let sut = makeSUT()
        let config = makeRenderConfig()
        let ops = [
            DashboardTestFixtures.makeSummary(date: date("2026-03-01")),
            DashboardTestFixtures.makeSummary(date: date("2026-03-20"))
        ]

        let visible = sut.correctedScrollPosition(
            current: date("2026-03-15"),
            from: ops,
            period: .week,
            visibleDomainLength: 7 * 24 * 60 * 60,
            renderConfig: config
        )
        let hidden = sut.correctedScrollPosition(
            current: date("2026-03-01"),
            from: ops,
            period: .week,
            visibleDomainLength: 7 * 24 * 60 * 60,
            renderConfig: config
        )

        #expect(visible == nil)
        #expect(hidden != nil)
    }

    private func date(_ value: String) -> Date {
        DateTimeTools.getDateFromDateString(value, format: "yyyy-MM-dd")
    }

    private func isoDate(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)!
    }
}
