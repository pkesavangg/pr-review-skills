//
//  BPDummyDataGenerator.swift
//  meApp
//

import Foundation

/// Generates deterministic dummy blood pressure history data for display purposes.
enum BPDummyDataGenerator {

    /// Generates monthly BP summaries going back 7 months from the current date.
    static func generateMonths() -> [BPHistoryMonth] {
        let calendar = Calendar.current
        let now = Date()
        var months: [BPHistoryMonth] = []

        for offset in 0..<7 {
            guard let date = calendar.date(byAdding: .month, value: -offset, to: now) else { continue }
            let year = calendar.component(.year, from: date)
            let month = calendar.component(.month, from: date)
            let id = String(format: "%04d-%02d", year, month)

            let entries = generateEntries(for: id)
            let count = entries.count
            guard count > 0 else { continue }

            let avgSys = entries.map(\.systolic).reduce(0, +) / count
            let avgDia = entries.map(\.diastolic).reduce(0, +) / count
            let avgPulse = entries.map(\.pulse).reduce(0, +) / count

            months.append(BPHistoryMonth(
                id: id,
                count: count,
                avgSystolic: avgSys,
                avgDiastolic: avgDia,
                avgPulse: avgPulse,
                month: String(format: "%02d", month),
                year: String(format: "%04d", year)
            ))
        }

        return months
    }

    /// Generates individual BP entries for a given month (deterministic based on month ID).
    static func generateEntries(for monthId: String) -> [BPHistoryEntry] {
        // Seed based on month string for deterministic output
        let seed = monthId.hashValue
        var rng = SeededRandomGenerator(seed: UInt64(bitPattern: Int64(seed)))

        let entryCount = Int.random(in: 4...5, using: &rng)
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM"
        guard let monthDate = formatter.date(from: monthId) else { return [] }

        let calendar = Calendar.current
        let range = calendar.range(of: .day, in: .month, for: monthDate) ?? 1..<29
        let maxDay = range.upperBound - 1

        var entries: [BPHistoryEntry] = []
        let noteTexts = [
            "this is where a note would go is someone were to leave a note on a measurement.",
            nil, nil, nil
        ]

        for i in 0..<entryCount {
            let day = min(maxDay, max(1, (maxDay - i * (maxDay / entryCount))))
            let hour = 9
            let minute = 52

            guard let entryDate = calendar.date(
                from: DateComponents(
                    year: calendar.component(.year, from: monthDate),
                    month: calendar.component(.month, from: monthDate),
                    day: day,
                    hour: hour,
                    minute: minute
                )
            ) else { continue }

            let iso = ISO8601DateFormatter()
            iso.timeZone = TimeZone.current
            let timestamp = iso.string(from: entryDate)

            let systolic = Int.random(in: 110...145, using: &rng)
            let diastolic = Int.random(in: 70...90, using: &rng)
            let pulse = Int.random(in: 55...80, using: &rng)

            let noteIndex = Int.random(in: 0..<noteTexts.count, using: &rng)
            let note = noteTexts[noteIndex]

            entries.append(BPHistoryEntry(
                id: UUID(uuidString: generateDeterministicUUID(monthId: monthId, index: i)) ?? UUID(),
                entryTimestamp: timestamp,
                systolic: systolic,
                diastolic: diastolic,
                pulse: pulse,
                notes: note
            ))
        }

        // Sort newest first
        return entries.sorted { $0.entryTimestamp > $1.entryTimestamp }
    }

    // MARK: - Private Helpers

    private static func generateDeterministicUUID(monthId: String, index: Int) -> String {
        let hash = abs("\(monthId)-\(index)".hashValue)
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

/// A simple seedable random number generator for deterministic dummy data.
private struct SeededRandomGenerator: RandomNumberGenerator {
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
