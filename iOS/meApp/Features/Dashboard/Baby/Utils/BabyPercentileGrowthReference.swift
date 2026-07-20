//
//  BabyPercentileGrowthReference.swift
//  meApp
//
//  Parity with Smart Baby (Ionic) growth percentile charts:
//  - `babyApp/src/app/services/percentile.service.ts`
//  - `babyApp/src/app/services/graph.service.ts` (focus date + percentile labels)
//  - `babyApp/src/assets/data/measurements/*.csv` (Day, M, SD — weight in decigrams, length in mm)
//  - `babyApp/src/assets/data/percentileLines/*Line.csv` (Day + 5th,10th,25th,50th,75th,90th,95th)
//
//  Bundled JSON + Z-table originate from DevicesTestApp:
//  `ggBluetoothNativeLibrary/ios/GGBluetoothPackageTest/.../Resources/Data/`
//

import Foundation

/// Reference data and formatting aligned with the Smart Baby app’s growth charts.
enum BabyPercentileGrowthReference {
    /// Subfolder under `Resources` copied into the app bundle (synced target).
    static let bundledDataRoot = "Data/BabyGrowthPercentile"

    /// Divisor to convert the JSON decigram values to kilograms.
    static let decigramsToKgFactor: Double = 10_000.0

    /// Millimetres per inch — the length reference JSON is stored in mm (Smart Baby's base unit); the
    /// dashboard plots inches, so curves divide by this and a reading multiplies by it before the z-score.
    static let millimetersPerInch: Double = 25.4

    enum JSONFile {
        static let boyWeightMeasurements = "BoyWeightDecigrams"
        static let girlWeightMeasurements = "GirlWeightDecigrams"
        static let boyWeightPercentileLines = "BoyWeightDecigramsLine"
        static let girlWeightPercentileLines = "GirlWeightDecigramsLine"
        // MOB-1516: WHO length-for-age tables (mm), converted from Smart Baby's `*LengthMM(.Line).csv`.
        static let boyLengthMeasurements = "BoyLengthMM"
        static let girlLengthMeasurements = "GirlLengthMM"
        static let boyLengthPercentileLines = "BoyLengthMMLine"
        static let girlLengthPercentileLines = "GirlLengthMMLine"
        static let zTable = "ZTable"
        static let measurementsSubfolder = "measurements"
        static let percentileLinesSubfolder = "percentileLines"
    }

    private struct BabyPercentileMeasurementEntry: Codable {
        let day: Int
        let meanDecigrams: Int
        let standardDeviationDecigrams: Int

        enum CodingKeys: String, CodingKey {
            case day
            case meanDecigrams = "m"
            case standardDeviationDecigrams = "sd"
        }
    }

    private struct BabyInterpolatedMeasurement {
        let mean: Double
        let standardDeviation: Double
    }

    private static let boyPercentileEntries = loadPercentileEntries(
        named: JSONFile.boyWeightPercentileLines
    )
    private static let girlPercentileEntries = loadPercentileEntries(
        named: JSONFile.girlWeightPercentileLines
    )
    private static let boyMeasurementEntries = loadMeasurementEntries(
        named: JSONFile.boyWeightMeasurements
    )
    private static let girlMeasurementEntries = loadMeasurementEntries(
        named: JSONFile.girlWeightMeasurements
    )
    private static let boyLengthPercentileEntries = loadPercentileEntries(
        named: JSONFile.boyLengthPercentileLines
    )
    private static let girlLengthPercentileEntries = loadPercentileEntries(
        named: JSONFile.girlLengthPercentileLines
    )
    private static let boyLengthMeasurementEntries = loadMeasurementEntries(
        named: JSONFile.boyLengthMeasurements
    )
    private static let girlLengthMeasurementEntries = loadMeasurementEntries(
        named: JSONFile.girlLengthMeasurements
    )

    // MARK: - Bundle Helpers

    /// Resolves a bundled JSON URL (GGBluetoothPackageTest loads by basename at bundle root; meApp keeps subfolders).
    static func bundledJSONURL(name: String, subdirectory: String, bundle: Bundle = .main) -> URL? {
        let relative = "\(bundledDataRoot)/\(subdirectory)"
        if let url = bundle.url(forResource: name, withExtension: "json", subdirectory: relative) {
            return url
        }
        if let url = bundle.url(forResource: name, withExtension: "json", subdirectory: "Resources/\(relative)") {
            return url
        }
        return bundle.url(forResource: name, withExtension: "json")
    }

    /// WHO publication year for the child growth standards used by the reference tables.
    static let whoChildGrowthStandardsYear = 2006

    /// Interpolation window in days (`dataResolution` in Smart Baby `PercentileService`).
    static let measurementInterpolationDayWindow = 7

    // MARK: - Percentile Chart Data

    /// Loads all percentile line entries from the gender-appropriate JSON.
    private static func loadPercentileEntries(named jsonName: String, bundle: Bundle = .main) -> [BabyPercentileLineEntry] {
        guard let url = bundledJSONURL(name: jsonName, subdirectory: JSONFile.percentileLinesSubfolder, bundle: bundle),
              let data = try? Data(contentsOf: url),
              let entries = try? JSONDecoder().decode([BabyPercentileLineEntry].self, from: data)
        else { return [] }
        return entries
    }

    private static func loadPercentileEntries(biologicalSex: String?) -> [BabyPercentileLineEntry] {
        isMaleReference(biologicalSex) ? boyPercentileEntries : girlPercentileEntries
    }

    private static func loadMeasurementEntries(named jsonName: String, bundle: Bundle = .main) -> [BabyPercentileMeasurementEntry] {
        guard let url = bundledJSONURL(name: jsonName, subdirectory: JSONFile.measurementsSubfolder, bundle: bundle),
              let data = try? Data(contentsOf: url),
              let entries = try? JSONDecoder().decode([BabyPercentileMeasurementEntry].self, from: data)
        else { return [] }
        return entries
    }

    private static func loadMeasurementEntries(biologicalSex: String?) -> [BabyPercentileMeasurementEntry] {
        isMaleReference(biologicalSex) ? boyMeasurementEntries : girlMeasurementEntries
    }

    private static func lengthPercentileLineEntries(biologicalSex: String?) -> [BabyPercentileLineEntry] {
        isMaleReference(biologicalSex) ? boyLengthPercentileEntries : girlLengthPercentileEntries
    }

    private static func lengthMeasurementEntries(biologicalSex: String?) -> [BabyPercentileMeasurementEntry] {
        isMaleReference(biologicalSex) ? boyLengthMeasurementEntries : girlLengthMeasurementEntries
    }

    /// `true` when no sex-specific WHO reference applies: sex is `"private"` or unset.
    /// Parity with Smart Baby `percentile.service.ts`, which returns `null` / `[]` for
    /// private — a withheld sex is never silently mapped to the boys' or girls' tables.
    static func isSexWithheld(_ biologicalSex: String?) -> Bool {
        guard let sex = biologicalSex?.lowercased(), !sex.isEmpty else { return true }
        return sex == "private"
    }

    /// Returns WHO percentile reference points filtered to a date range and converted to display units.
    ///
    /// - Parameters:
    ///   - biologicalSex: `"male"` / `"female"` (`"private"` / `nil` yields no points).
    ///   - birthday: The baby’s date of birth — used to map JSON day offsets to calendar dates.
    ///   - dateRange: The visible chart window.
    ///   - convertDecigramsToDisplay: Converts a decigram value to the active display unit (lbs or kg).
    static func percentileChartPoints(
        biologicalSex: String?,
        birthday: Date,
        dateRange: ClosedRange<Date>,
        convertDecigramsToDisplay: (Int) -> Double,
        calendar: Calendar = .current
    ) -> [BabyPercentileChartPoint] {
        guard !isSexWithheld(biologicalSex) else { return [] }
        return chartPoints(
            entries: loadPercentileEntries(biologicalSex: biologicalSex),
            birthday: birthday,
            dateRange: dateRange,
            convertToDisplay: convertDecigramsToDisplay,
            calendar: calendar
        )
    }

    /// MOB-1516: WHO length-for-age percentile curves. Identical shape to `percentileChartPoints` but reads
    /// the length tables (mm) and converts each mm value to the display unit (inches) via
    /// `convertMillimetersToDisplay`. Parity with Smart Baby's `*LengthMMLine.csv` curves.
    static func lengthPercentileChartPoints(
        biologicalSex: String?,
        birthday: Date,
        dateRange: ClosedRange<Date>,
        convertMillimetersToDisplay: (Int) -> Double,
        calendar: Calendar = .current
    ) -> [BabyPercentileChartPoint] {
        guard !isSexWithheld(biologicalSex) else { return [] }
        return chartPoints(
            entries: lengthPercentileLineEntries(biologicalSex: biologicalSex),
            birthday: birthday,
            dateRange: dateRange,
            convertToDisplay: convertMillimetersToDisplay,
            calendar: calendar
        )
    }

    /// Shared curve builder for weight + length: filter the reference rows to the visible window (+ an
    /// 8-day boundary each side for line continuity), downsample to ~150 rows, map each day-of-life offset
    /// to a calendar date, and emit one display-converted point per percentile line.
    private static func chartPoints(
        entries: [BabyPercentileLineEntry],
        birthday: Date,
        dateRange: ClosedRange<Date>,
        convertToDisplay: (Int) -> Double,
        calendar: Calendar
    ) -> [BabyPercentileChartPoint] {
        guard !entries.isEmpty else { return [] }

        let startDay = max(0, calendar.dateComponents([.day], from: birthday, to: dateRange.lowerBound).day ?? 0)
        let endDay = max(0, calendar.dateComponents([.day], from: birthday, to: dateRange.upperBound).day ?? 0)

        // Include one boundary entry outside each edge for smooth line continuity.
        let filtered = entries.filter { $0.day >= startDay - 8 && $0.day <= endDay + 8 }

        // Downsample: keep ~150 points per percentile line max.
        // For short ranges (< 150 days) keep every point; for longer ranges stride.
        let stride = max(1, filtered.count / 150)
        var downsampled: [BabyPercentileLineEntry] = []
        downsampled.reserveCapacity(min(filtered.count, 152))
        for (index, entry) in filtered.enumerated() {
            // Always keep first, last, and every Nth entry
            if index == 0 || index == filtered.count - 1 || index % stride == 0 {
                downsampled.append(entry)
            }
        }

        let lines = BabyPercentileLine.allCases
        var points: [BabyPercentileChartPoint] = []
        points.reserveCapacity(downsampled.count * lines.count)

        for entry in downsampled {
            guard let date = calendar.date(byAdding: .day, value: entry.day, to: birthday) else { continue }
            for line in lines {
                points.append(BabyPercentileChartPoint(
                    date: date,
                    value: convertToDisplay(entry.value(for: line)),
                    line: line
                ))
            }
        }
        return points
    }

    static func weightPercentile(
        biologicalSex: String?,
        birthday: Date,
        date: Date,
        weightDecigrams: Int,
        calendar: Calendar = .current
    ) -> Int? {
        guard !isSexWithheld(biologicalSex) else { return nil }
        let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: date).day ?? 0)
        let entries = loadMeasurementEntries(biologicalSex: biologicalSex)
        guard let measurement = interpolatedMeasurement(forDayOfLife: dayOfLife, entries: entries),
              measurement.standardDeviation > AppConstants.Precision.doubleEqualityEpsilon else {
            return nil
        }

        let zScore = (Double(weightDecigrams) - measurement.mean) / measurement.standardDeviation
        return percentile(fromZScore: zScore)
    }

    /// MOB-1516: WHO length-for-age percentile for a single reading. Mirrors `weightPercentile` but reads the
    /// length M/SD tables (mm) and takes the reading in mm (the dashboard passes inches × `millimetersPerInch`).
    static func lengthPercentile(
        biologicalSex: String?,
        birthday: Date,
        date: Date,
        lengthMillimeters: Double,
        calendar: Calendar = .current
    ) -> Int? {
        guard !isSexWithheld(biologicalSex) else { return nil }
        let dayOfLife = max(0, calendar.dateComponents([.day], from: birthday, to: date).day ?? 0)
        let entries = lengthMeasurementEntries(biologicalSex: biologicalSex)
        guard let measurement = interpolatedMeasurement(forDayOfLife: dayOfLife, entries: entries),
              measurement.standardDeviation > AppConstants.Precision.doubleEqualityEpsilon else {
            return nil
        }

        let zScore = (lengthMillimeters - measurement.mean) / measurement.standardDeviation
        return percentile(fromZScore: zScore)
    }

    // MARK: - Date Formatting

    /// Date shown next to the selected point on Smart Baby’s graph (`focusPoint` in `graph.service.ts`):
    /// local calendar `MM/dd/yy`, matching the reorder + two-digit slice behavior in JS.
    static func formatChartSelectionDate(_ date: Date, calendar: Calendar = .current) -> String {
        let parts = calendar.dateComponents([.year, .month, .day], from: date)
        guard let year = parts.year, let month = parts.month, let day = parts.day else {
            return ""
        }
        let yy = year % 100
        return String(format: "%02d/%02d/%02d", month, day, yy)
    }

    private static func isMaleReference(_ biologicalSex: String?) -> Bool {
        biologicalSex?.lowercased() != "female"
    }

    private static func interpolatedMeasurement(
        forDayOfLife dayOfLife: Int,
        entries: [BabyPercentileMeasurementEntry]
    ) -> BabyInterpolatedMeasurement? {
        guard !entries.isEmpty else { return nil }
        // Parity with Smart Baby: it only yields a percentile when a reference row is within ±window days of
        // the age (its `dataResolution` filter); past the table it returns −1 (no %). Reject ages outside the
        // covered range so we never EXTRAPOLATE off the first/last row. (The tables span 0–~20y, so this is a
        // safety/parity guard, not a range a real baby age reaches.)
        let days = entries.map(\.day)
        guard let firstDay = days.min(), let lastDay = days.max(),
              dayOfLife >= firstDay - measurementInterpolationDayWindow,
              dayOfLife <= lastDay + measurementInterpolationDayWindow else {
            return nil
        }
        if let exact = entries.first(where: { $0.day == dayOfLife }) {
            return BabyInterpolatedMeasurement(
                mean: Double(exact.meanDecigrams),
                standardDeviation: Double(exact.standardDeviationDecigrams)
            )
        }

        let sortedEntries = entries.sorted { $0.day < $1.day }
        let lower = sortedEntries.last { $0.day < dayOfLife } ?? sortedEntries.first
        let upper = sortedEntries.first { $0.day > dayOfLife } ?? sortedEntries.last

        guard let lower, let upper else { return nil }
        guard lower.day != upper.day else {
            return BabyInterpolatedMeasurement(
                mean: Double(lower.meanDecigrams),
                standardDeviation: Double(lower.standardDeviationDecigrams)
            )
        }

        let progress = Double(dayOfLife - lower.day) / Double(upper.day - lower.day)
        let mean = Double(lower.meanDecigrams) +
            ((Double(upper.meanDecigrams) - Double(lower.meanDecigrams)) * progress)
        let standardDeviation = Double(lower.standardDeviationDecigrams) +
            ((Double(upper.standardDeviationDecigrams) - Double(lower.standardDeviationDecigrams)) * progress)
        return BabyInterpolatedMeasurement(mean: mean, standardDeviation: standardDeviation)
    }

    private static func percentile(fromZScore zScore: Double) -> Int? {
        let clampedZScore = min(max(zScore, -3.49), 3.49)
        guard let cumulativeProbability = cumulativeProbability(for: clampedZScore) else { return nil }
        let percentile = Int((cumulativeProbability * 100).rounded())
        return min(max(percentile, 0), 100)
    }

    private static func cumulativeProbability(for zScore: Double) -> Double? {
        if abs(zScore) < AppConstants.Precision.doubleEqualityEpsilon {
            return 0.5
        }

        if zScore > 0 {
            guard let mirrored = cumulativeProbability(for: -zScore) else { return nil }
            return 1 - mirrored
        }

        let absoluteValue = abs(zScore)
        let rowValue = floor(absoluteValue * 10) / 10
        let columnValue = (absoluteValue * 100).rounded() / 100 - rowValue
        let rowKey = rowValue < AppConstants.Precision.doubleEqualityEpsilon
            ? "0.0"
            : String(format: "-%.1f", rowValue)

        guard let columns = BabyGrowthPercentileZTable.table["z"],
              let row = BabyGrowthPercentileZTable.table[rowKey] else {
            return nil
        }

        if let exactIndex = columns.firstIndex(where: {
            abs($0 - columnValue) < AppConstants.Precision.doubleEqualityEpsilon
        }) {
            return row[exactIndex]
        }

        guard let nearestIndex = columns.enumerated().min(by: {
            abs($0.element - columnValue) < abs($1.element - columnValue)
        })?.offset else {
            return nil
        }
        return row[nearestIndex]
    }
}
