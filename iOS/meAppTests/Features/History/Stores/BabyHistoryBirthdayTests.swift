//
//  BabyHistoryBirthdayTests.swift
//  meAppTests
//
//  Coverage for the MOB-1164 birthday-week balloon logic:
//  `HistoryStore.isBirthday(dayId:birthdayComponents:)` (anniversary-aware match),
//  `BabyHistoryWeek.containsBirthday`, and `BabyProfile.displayName` fallback.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
struct BabyHistoryBirthdayTests {

    // MARK: - Helpers

    /// Builds month/day components for an anniversary comparison (year ignored).
    private func components(month: Int, day: Int) -> DateComponents {
        var comps = DateComponents()
        comps.month = month
        comps.day = day
        return comps
    }

    private func makeDay(id: String, isBirthday: Bool = false) -> BabyHistoryDay {
        BabyHistoryDay(
            id: id,
            entryCount: 1,
            weightLbs: 8,
            weightOz: 14.9,
            weightKg: 4.0,
            weightLb: 8.9,
            lengthInches: 12,
            lengthCm: 30,
            percentile: 6,
            weightDisplay: "8 lb 14.9 oz",
            lengthDisplay: "12 in",
            isBirthday: isBirthday
        )
    }

    // MARK: - HistoryStore.isBirthday

    @Test("Matches the exact birthday date")
    func matchesExactBirthday() {
        let birthday = components(month: 8, day: 17)
        #expect(HistoryStore.isBirthday(dayId: "2025-08-17", birthdayComponents: birthday))
    }

    @Test("Matches the birthday anniversary in a different year")
    func matchesAnniversaryDifferentYear() {
        let birthday = components(month: 8, day: 17)
        #expect(HistoryStore.isBirthday(dayId: "2026-08-17", birthdayComponents: birthday))
        #expect(HistoryStore.isBirthday(dayId: "2024-08-17", birthdayComponents: birthday))
    }

    @Test("Does not match a non-birthday date in the same month")
    func doesNotMatchOtherDay() {
        let birthday = components(month: 8, day: 17)
        #expect(!HistoryStore.isBirthday(dayId: "2025-08-16", birthdayComponents: birthday))
        #expect(!HistoryStore.isBirthday(dayId: "2025-08-18", birthdayComponents: birthday))
    }

    @Test("Does not match the same day in a different month")
    func doesNotMatchOtherMonth() {
        let birthday = components(month: 8, day: 17)
        #expect(!HistoryStore.isBirthday(dayId: "2025-07-17", birthdayComponents: birthday))
    }

    @Test("Returns false when no birthday is set")
    func returnsFalseWhenNoBirthday() {
        #expect(!HistoryStore.isBirthday(dayId: "2025-08-17", birthdayComponents: nil))
    }

    @Test("Returns false for a malformed day id")
    func returnsFalseForMalformedDayId() {
        let birthday = components(month: 8, day: 17)
        #expect(!HistoryStore.isBirthday(dayId: "not-a-date", birthdayComponents: birthday))
        #expect(!HistoryStore.isBirthday(dayId: "2025-08", birthdayComponents: birthday))
        #expect(!HistoryStore.isBirthday(dayId: "", birthdayComponents: birthday))
    }

    // MARK: - BabyHistoryWeek.containsBirthday

    @Test("Week containing a birthday day reports containsBirthday true")
    func weekContainsBirthdayTrue() {
        let week = BabyHistoryWeek(
            id: "week-4",
            weekNumber: 4,
            days: [
                makeDay(id: "2025-08-19"),
                makeDay(id: "2025-08-17", isBirthday: true),
                makeDay(id: "2025-08-15")
            ]
        )
        #expect(week.containsBirthday)
    }

    @Test("Week without a birthday day reports containsBirthday false")
    func weekContainsBirthdayFalse() {
        let week = BabyHistoryWeek(
            id: "week-3",
            weekNumber: 3,
            days: [makeDay(id: "2025-08-13"), makeDay(id: "2025-08-12")]
        )
        #expect(!week.containsBirthday)
    }

    // MARK: - BabyProfile.displayName

    @Test("Uses the baby's name when present")
    func displayNameUsesName() {
        let profile = BabyProfile(id: "1", name: "Katey")
        #expect(profile.displayName == "Katey")
    }

    @Test("Falls back to Baby Scale when the name is empty or whitespace")
    func displayNameFallsBack() {
        #expect(BabyProfile(id: "1", name: "").displayName == ProductTypeStrings.babyScale)
        #expect(BabyProfile(id: "2", name: "   ").displayName == ProductTypeStrings.babyScale)
    }
}
