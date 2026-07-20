//
//  BabyWeightPercentileCalculator.swift
//  meApp
//
//  Calculates a baby's weight percentile using WHO growth standards.
//  Ported from `babyApp/src/app/services/percentile.service.ts`.
//
//  Algorithm:
//  1. Look up mean (M) and standard deviation (SD) for the baby's age in days
//  2. Interpolate between bracketing rows when the exact day isn't in the table
//  3. Compute Z-score = (observed - M) / SD
//  4. Map Z-score to a percentile via the standard normal CDF (Z-table)
//

import Foundation

// MARK: - Measurement Row

/// A single row from the WHO growth measurement JSON (Day, Mean, Standard Deviation).
struct BabyGrowthMeasurementRow: Codable {
    let day: Int
    let mean: Double
    let sd: Double

    enum CodingKeys: String, CodingKey {
        case day
        case mean = "m"
        case sd
    }
}

// MARK: - Calculator

enum BabyWeightPercentileCalculator {

    /// Which WHO reference dataset a measurement is scored against.
    enum MeasurementKind { case weight, length }

    /// Interval between measurement rows in the WHO data (~7 days).
    private static let dataResolution = 7

    // MARK: - Cached Data

    /// Loaded measurement rows keyed by JSON filename (one per sex × dataset).
    private static var measurementCache: [String: [BabyGrowthMeasurementRow]] = [:]

    /// The bundled measurement JSON filename for a sex + dataset.
    private static func measurementFileName(sex: String, kind: MeasurementKind) -> String {
        let isMale = sex.lowercased() != "female"
        switch (kind, isMale) {
        case (.weight, true): return BabyPercentileGrowthReference.JSONFile.boyWeightMeasurements
        case (.weight, false): return BabyPercentileGrowthReference.JSONFile.girlWeightMeasurements
        case (.length, true): return BabyPercentileGrowthReference.JSONFile.boyLengthMeasurements
        case (.length, false): return BabyPercentileGrowthReference.JSONFile.girlLengthMeasurements
        }
    }

    private static func loadMeasurements(for sex: String, kind: MeasurementKind) -> [BabyGrowthMeasurementRow] {
        let name = measurementFileName(sex: sex, kind: kind)
        if let cached = measurementCache[name] { return cached }

        guard let url = BabyPercentileGrowthReference.bundledJSONURL(
            name: name,
            subdirectory: BabyPercentileGrowthReference.JSONFile.measurementsSubfolder
        ),
              let data = try? Data(contentsOf: url),
              let rows = try? JSONDecoder().decode([BabyGrowthMeasurementRow].self, from: data)
        else { return [] }

        measurementCache[name] = rows
        return rows
    }

    // MARK: - Public API

    /// Returns the weight-for-age percentile (0–100), or `-1` when it cannot be calculated.
    static func calculatePercentile(
        weightDecigrams: Int,
        biologicalSex: String?,
        birthday: Date?,
        entryDate: Date,
        calendar: Calendar = .current
    ) -> Int {
        guard let sex = biologicalSex, let birthday else { return -1 }
        let days = max(0, calendar.dateComponents([.day], from: birthday, to: entryDate).day ?? 0)
        return percentile(observed: Double(weightDecigrams), daysSinceBirth: days, sex: sex, kind: .weight)
    }

    /// Returns the length-for-age percentile (0–100), or `-1` when it cannot be calculated.
    /// Uses the WHO length reference data (mm) with the same LMS/Z-score method as weight —
    /// 1:1 with the Baby app's `calcMeasurementPercentile` for `measureLength` (MOB-1567).
    static func calculateLengthPercentile(
        lengthMm: Int,
        biologicalSex: String?,
        birthday: Date?,
        entryDate: Date,
        calendar: Calendar = .current
    ) -> Int {
        guard let sex = biologicalSex, let birthday else { return -1 }
        let days = max(0, calendar.dateComponents([.day], from: birthday, to: entryDate).day ?? 0)
        return percentile(observed: Double(lengthMm), daysSinceBirth: days, sex: sex, kind: .length)
    }

    /// Shared LMS/Z-score percentile for either dataset.
    private static func percentile(
        observed: Double,
        daysSinceBirth: Int,
        sex: String,
        kind: MeasurementKind
    ) -> Int {
        guard !sex.isEmpty, sex.lowercased() != "private" else { return -1 }

        let measurements = loadMeasurements(for: sex, kind: kind)
        guard !measurements.isEmpty else { return -1 }

        // Find rows bracketing the baby's age (within ± dataResolution).
        let bracketingRows = measurements.filter {
            $0.day >= daysSinceBirth - dataResolution + 1
                && $0.day <= daysSinceBirth + dataResolution
        }

        let mean: Double
        let sd: Double

        switch bracketingRows.count {
        case 0:
            return -1
        case 1:
            mean = bracketingRows[0].mean
            sd = bracketingRows[0].sd
        default:
            let row0 = bracketingRows[0]
            let row1 = bracketingRows[1]
            let dayDiff = Double(daysSinceBirth - row0.day)
            let weight = dayDiff / Double(dataResolution)
            mean = row0.mean + (row1.mean - row0.mean) * weight
            sd = row0.sd + (row1.sd - row0.sd) * weight
        }

        guard sd > 0 else { return -1 }

        let zScore = (observed - mean) / sd
        return zScoreToPercentile(zScore)
    }

    // MARK: - Z-Score → Percentile

    /// Converts a Z-score to a percentile integer (0–100) using the bundled Z-table.
    private static func zScoreToPercentile(_ zscore: Double) -> Int {
        if zscore > 3.49 { return 100 }
        if zscore < -3.49 { return 0 }

        let table = BabyGrowthPercentileZTable.table
        guard let zColumnHeaders = table["z"] else { return -1 }

        // Decompose z-score into row key (tenths) and column key (hundredths).
        let truncated: Double
        let rowValue: Double

        if zscore > 0 {
            truncated = floor(zscore * 100) / 100
            rowValue = -(floor(zscore * 10) / 10)
        } else {
            truncated = ceil(zscore * 100) / 100
            rowValue = ceil(zscore * 10) / 10
        }

        // Hundredths digit for column lookup.
        let hundredths: Double
        if rowValue == 0 {
            hundredths = abs(truncated)
        } else {
            let remainder = truncated.truncatingRemainder(dividingBy: rowValue)
            hundredths = abs(round(remainder * 10000) / 10000)
        }

        // Round to 2 decimals to avoid floating-point mismatch.
        let colValue = round(hundredths * 100) / 100

        let rowKey = rowValue == 0 ? "0.0" : String(format: "%.1f", rowValue)

        guard let rowData = table[rowKey],
              let colIndex = zColumnHeaders.firstIndex(where: { abs($0 - colValue) < 0.001 })
        else { return -1 }

        let probability = rowData[colIndex]

        let percentile: Double
        if zscore > 0 {
            percentile = (1 - probability) * 100
        } else {
            percentile = probability * 100
        }

        return max(0, min(100, Int(round(percentile))))
    }

    // MARK: - Display Formatting

    /// Formats a percentile integer as Smart Baby's ordinal label with capped extremes — e.g. "95th",
    /// "> 99th" (pct > 99), "< 1st" (pct < 1) — parity with babyApp's `getPercentileSuffix`. Returns `"--"`
    /// for the no-data sentinel (`-1`). The caller appends the " %" unit (matching babyApp's focus label /
    /// history list). MOB-1591: the ordinal and number are joined with NO space ("95th", not "95 th") so it
    /// reads exactly like Smart Baby.
    static func percentileDisplayText(_ percentile: Int) -> String {
        if percentile < 0 { return "--" }
        if percentile < 1 { return "< 1st" }
        if percentile > 99 { return "> 99th" }

        let suffix: String
        let mod100 = percentile % 100
        if (11...13).contains(mod100) {
            suffix = "th"
        } else {
            switch percentile % 10 {
            case 1: suffix = "st"
            case 2: suffix = "nd"
            case 3: suffix = "rd"
            default: suffix = "th"
            }
        }
        return "\(percentile)\(suffix)"
    }
}
