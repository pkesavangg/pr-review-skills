//
//  GraphDomainScrollIndependenceTests.swift
//  meAppTests
//
//  MOB-1515 (AC #4) — the v2 engine's scrollable x-domain is computed ONCE from the data span
//  (`GraphRenderingConfiguration.fullXDomain`, no scroll input) and `buildWeight` uses it verbatim for
//  `ChartModel.xDomain`; the bounded scroll window always stays clamped inside it. (The x-axis *ticks* are
//  deliberately windowed / scroll-dependent for perf — MOB-1516 — so the retired "fullXAxisValues
//  scroll-independent" clause is intentionally not asserted; see the MOB-1515 test plan.)
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct GraphDomainScrollIndependenceTests {

    /// Sunday-first UTC Gregorian so geometry is deterministic across machines/timezones.
    private func utcCalendar() -> Calendar {
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = TimeZone(secondsFromGMT: 0) ?? .current
        cal.locale = Locale(identifier: "en_US_POSIX")
        return cal
    }

    private func makeDate(_ day: Int, hour: Int = 8) -> Date {
        guard let date = utcCalendar().date(from: DateComponents(year: 2026, month: 3, day: day, hour: hour)) else {
            Issue.record("failed to build fixture date")
            return Date(timeIntervalSinceReferenceDate: 0)
        }
        return date
    }

    private func dailyOps(_ days: ClosedRange<Int>) -> [BathScaleWeightSummary] {
        days.map { day in
            DashboardTestFixtures.makeSummary(
                period: String(format: "2026-03-%02d", day),
                entryTimestamp: ISO8601DateFormatter().string(from: makeDate(day)),
                date: makeDate(day),
                weight: Double(1800 + day)
            )
        }
    }

    private func config() -> GraphRenderingConfiguration {
        GraphRenderingConfiguration(calendar: utcCalendar())
    }

    @Test("fullXDomain is a pure function of the data — identical across repeated calls, every period")
    func fullXDomainIsDeterministic() {
        let ops = dailyOps(1...20)
        for period in TimePeriod.allCases {
            #expect(config().fullXDomain(for: period, from: ops) == config().fullXDomain(for: period, from: ops))
        }
    }

    @Test("buildWeight yields the same xDomain regardless of scroll position, for every period")
    func modelDomainIsScrollIndependent() {
        let ops = dailyOps(1...20)
        let scrollPositions = [makeDate(3), makeDate(10), makeDate(18)]
        for period in TimePeriod.allCases {
            let domains = scrollPositions.map { scroll in
                ChartPrep.buildWeight(
                    operations: ops,
                    period: period,
                    scrollPosition: scroll,
                    goalWeight: nil,
                    isWeightlessMode: false,
                    anchorWeight: nil,
                    convertWeight: DashboardTestFixtures.convertToLbs,
                    calendar: utcCalendar(),
                    config: config()
                ).xDomain
            }
            #expect(domains.allSatisfy { $0 == domains[0] }, "\(period) xDomain must not depend on scroll position")
        }
    }

    @Test("the bounded scroll window stays clamped inside the full domain at any scroll position")
    func boundedDomainStaysWithinFull() throws {
        let ops = dailyOps(1...28)
        for period in [TimePeriod.week, .month, .year] { // .total isn't scrollable → whole span
            let full = try #require(config().fullXDomain(for: period, from: ops))
            for scroll in [makeDate(2), makeDate(14), makeDate(27)] {
                let bounded = try #require(config().boundedXDomain(
                    for: period, from: ops, around: scroll, windows: ChartPrep.tickWindowRadius
                ))
                #expect(bounded.lowerBound >= full.lowerBound)
                #expect(bounded.upperBound <= full.upperBound)
            }
        }
    }

    @Test("empty operations fall back to an ordered, non-degenerate empty-state domain, every period")
    func emptyOpsYieldOrderedDomain() throws {
        for period in TimePeriod.allCases {
            let range = try #require(config().fullXDomain(for: period, from: []))
            #expect(range.lowerBound < range.upperBound)
        }
    }
}
