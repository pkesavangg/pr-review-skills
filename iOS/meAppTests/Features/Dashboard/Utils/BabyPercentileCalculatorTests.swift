//
//  BabyPercentileCalculatorTests.swift
//  meAppTests
//
//  Verifies the WHO LMS/Z-score percentile calculation for both weight and length,
//  including the length-for-age dataset ported from the Baby app (MOB-1567).
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct BabyPercentileCalculatorTests {

    /// A measurement at the reference mean sits near the 50th percentile.
    private func expectNearMedian(_ percentile: Int, tolerance: Int = 8) {
        #expect(percentile >= 50 - tolerance && percentile <= 50 + tolerance)
    }

    @Test("length percentile at the day-0 girl mean (491.48 mm) is ~50th")
    func lengthPercentileAtGirlMeanIsMedian() {
        let birthday = Date()
        let pct = BabyWeightPercentileCalculator.calculateLengthPercentile(
            lengthMm: 491, biologicalSex: "female", birthday: birthday, entryDate: birthday
        )
        #expect(pct >= 0 && pct <= 100)
        expectNearMedian(pct)
    }

    @Test("a longer-than-mean length scores above the 50th percentile")
    func longerLengthScoresHigher() {
        let birthday = Date()
        let median = BabyWeightPercentileCalculator.calculateLengthPercentile(
            lengthMm: 491, biologicalSex: "female", birthday: birthday, entryDate: birthday
        )
        let tall = BabyWeightPercentileCalculator.calculateLengthPercentile(
            lengthMm: 528, biologicalSex: "female", birthday: birthday, entryDate: birthday
        )
        #expect(tall > median)
    }

    @Test("length percentile is unavailable (-1) when the sex is withheld")
    func lengthPercentilePrivateSex() {
        let birthday = Date()
        let pct = BabyWeightPercentileCalculator.calculateLengthPercentile(
            lengthMm: 500, biologicalSex: "private", birthday: birthday, entryDate: birthday
        )
        #expect(pct == -1)
    }

    @Test("weight percentile still resolves after the shared refactor")
    func weightPercentileStillWorks() {
        let birthday = Date()
        let pct = BabyWeightPercentileCalculator.calculatePercentile(
            weightDecigrams: 32322, biologicalSex: "female", birthday: birthday, entryDate: birthday
        )
        #expect(pct >= 0 && pct <= 100)
        expectNearMedian(pct)
    }
}
