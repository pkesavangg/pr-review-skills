//
//  ChartDomainSanitizerTests.swift
//  meAppTests
//
//  MOB-518 / W2 — verifies the pure domain guards that keep degenerate / non-finite domains from
//  reaching Swift Charts' scale modifiers (the source of the `Invalid frame dimension` flood).
//

import Foundation
@testable import meApp
import Testing

@Suite("ChartDomainSanitizer — Charts domain guards")
struct ChartDomainSanitizerTests {

    // MARK: - finiteWidth

    @Test("finiteWidth passes a healthy domain through unchanged")
    func finiteWidthPassthrough() {
        let range = 100.0...200.0
        #expect(ChartDomainSanitizer.finiteWidth(range) == range)
    }

    @Test("finiteWidth widens a zero-width domain around its midpoint")
    func finiteWidthZeroWidth() {
        let result = ChartDomainSanitizer.finiteWidth(50.0...50.0, minWidth: 1)
        #expect(result.lowerBound == 49.5)
        #expect(result.upperBound == 50.5)
    }

    @Test("finiteWidth widens a sub-minWidth domain to exactly minWidth")
    func finiteWidthTooNarrow() {
        let result = ChartDomainSanitizer.finiteWidth(10.0...10.4, minWidth: 1)
        #expect(abs((result.upperBound - result.lowerBound) - 1) < 1e-9)
    }

    @Test("finiteWidth falls back when a bound is non-finite")
    func finiteWidthNonFinite() {
        #expect(ChartDomainSanitizer.finiteWidth(0.0...Double.infinity, fallback: 0...100) == 0.0...100.0)
        #expect(ChartDomainSanitizer.finiteWidth(-Double.infinity...10, fallback: 0...100) == 0.0...100.0)
    }

    // MARK: - orderedDates

    @Test("orderedDates passes a healthy date range through unchanged")
    func orderedDatesPassthrough() {
        let start = Date(timeIntervalSince1970: 0)
        let end = Date(timeIntervalSince1970: 1000)
        #expect(ChartDomainSanitizer.orderedDates(start...end) == start...end)
    }

    @Test("orderedDates widens an equal-bound date range")
    func orderedDatesEqual() {
        let start = Date(timeIntervalSince1970: 0)
        let result = ChartDomainSanitizer.orderedDates(start...start, minWidth: 1)
        #expect(result.upperBound.timeIntervalSince(result.lowerBound) == 1)
    }

    // MARK: - positiveLength

    @Test("positiveLength keeps a positive length and clamps non-finite / non-positive ones")
    func positiveLengthClamps() {
        #expect(ChartDomainSanitizer.positiveLength(3600) == 3600)
        #expect(ChartDomainSanitizer.positiveLength(0, fallback: 5) == 5)
        #expect(ChartDomainSanitizer.positiveLength(.nan, fallback: 5) == 5)
        #expect(ChartDomainSanitizer.positiveLength(-10, fallback: 5) == 5)
    }
}
