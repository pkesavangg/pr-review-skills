//
//  BabyDummyDataGenerator.swift
//  meApp
//

import Foundation

/// Generates deterministic dummy baby history data for display purposes.
enum BabyDummyDataGenerator {

    /// Generates weekly baby history summaries going back ~4 weeks from the current date.
    static func generateWeeks() -> [BabyHistoryWeek] {
        let calendar = Calendar.current
        let now = Date()
        var weeks: [BabyHistoryWeek] = []

        // Generate 4 weeks of data, each with 6 days
        for weekOffset in 0..<4 {
            let weekNumber = 4 - weekOffset // week 4, 3, 2, 1
            var days: [BabyHistoryDay] = []

            for dayOffset in 0..<6 {
                let totalDayOffset = weekOffset * 7 + dayOffset
                guard let date = calendar.date(byAdding: .day, value: -totalDayOffset, to: now) else { continue }

                let dateId = formatDateId(date)
                let seed = dateId.hashValue
                var rng = BabySeededRandomGenerator(seed: UInt64(bitPattern: Int64(seed)))

                let entryCount = Int.random(in: 3...7, using: &rng)
                let weightLbs = 8
                let weightOz = Double(Int.random(in: 100...159, using: &rng)) / 10.0
                let lengthInches = Double(Int.random(in: 110...140, using: &rng)) / 10.0
                let percentile = Int.random(in: 3...15, using: &rng)

                days.append(BabyHistoryDay(
                    id: dateId,
                    entryCount: entryCount,
                    weightLbs: weightLbs,
                    weightOz: weightOz,
                    lengthInches: lengthInches,
                    percentile: percentile
                ))
            }

            weeks.append(BabyHistoryWeek(
                id: "week-\(weekNumber)",
                weekNumber: weekNumber,
                days: days
            ))
        }

        return weeks
    }

    /// Generates individual baby entries for a given day (deterministic based on day ID).
    static func generateEntries(for dayId: String) -> [BabyHistoryEntry] {
        let seed = dayId.hashValue
        var rng = BabySeededRandomGenerator(seed: UInt64(bitPattern: Int64(seed)))

        let entryCount = Int.random(in: 3...7, using: &rng)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let dayDate = formatter.date(from: dayId) else { return [] }

        let calendar = Calendar.current
        let noteTexts: [String?] = [
            "this is where a note would go is someone were to leave a note on a measurement.",
            nil, nil, nil
        ]

        var entries: [BabyHistoryEntry] = []

        for i in 0..<entryCount {
            let hour = 2
            let minute = 30 + i * 15

            guard let entryDate = calendar.date(
                from: DateComponents(
                    year: calendar.component(.year, from: dayDate),
                    month: calendar.component(.month, from: dayDate),
                    day: calendar.component(.day, from: dayDate),
                    hour: hour,
                    minute: minute
                )
            ) else { continue }

            let iso = ISO8601DateFormatter()
            iso.timeZone = TimeZone.current
            let timestamp = iso.string(from: entryDate)

            let weightLbs = 8
            let weightOz = Double(Int.random(in: 100...159, using: &rng)) / 10.0
            let lengthInches = Double(Int.random(in: 110...140, using: &rng)) / 10.0
            let percentile = Int.random(in: 3...15, using: &rng)

            let noteIndex = Int.random(in: 0..<noteTexts.count, using: &rng)
            let note = noteTexts[noteIndex]

            entries.append(BabyHistoryEntry(
                id: UUID(uuidString: generateDeterministicUUID(dayId: dayId, index: i)) ?? UUID(),
                entryTimestamp: timestamp,
                weightLbs: weightLbs,
                weightOz: weightOz,
                lengthInches: lengthInches,
                percentile: percentile,
                notes: note
            ))
        }

        // Sort newest first
        return entries.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    // MARK: - Private Helpers

    private static func formatDateId(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: date)
    }

    private static func generateDeterministicUUID(dayId: String, index: Int) -> String {
        let hash = abs("\(dayId)-\(index)".hashValue)
        let hex = String(format: "%032x", hash)
        let padded = String((hex + "00000000000000000000000000000000").prefix(32))
        let part1 = padded.prefix(8)
        let part2 = padded.dropFirst(8).prefix(4)
        let part3 = padded.dropFirst(12).prefix(4)
        let part4 = padded.dropFirst(16).prefix(4)
        let part5 = padded.dropFirst(20).prefix(12)
        return "\(part1)-\(part2)-\(part3)-\(part4)-\(part5)"
    }
}

// MARK: - Seeded Random Number Generator

/// A simple seedable random number generator for deterministic baby dummy data.
private struct BabySeededRandomGenerator: RandomNumberGenerator {
    private var state: UInt64

    init(seed: UInt64) {
        self.state = seed
    }

    mutating func next() -> UInt64 {
        state &+= 0x9e3779b97f4a7c15
        var z = state
        z = (z ^ (z >> 30)) &* 0xbf58476d1ce4e5b9
        z = (z ^ (z >> 27)) &* 0x94d049bb133111eb
        return z ^ (z >> 31)
    }
}
