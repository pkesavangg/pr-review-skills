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

    enum JSONFile {
        static let boyWeightMeasurements = "BoyWeightDecigrams"
        static let girlWeightMeasurements = "GirlWeightDecigrams"
        static let boyWeightPercentileLines = "BoyWeightDecigramsLine"
        static let girlWeightPercentileLines = "GirlWeightDecigramsLine"
        static let zTable = "ZTable"
        static let measurementsSubfolder = "measurements"
        static let percentileLinesSubfolder = "percentileLines"
    }

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
    private static func loadPercentileEntries(biologicalSex: String?, bundle: Bundle = .main) -> [BabyPercentileLineEntry] {
        let isMale = biologicalSex?.lowercased() != "female"
        let jsonName = isMale ? JSONFile.boyWeightPercentileLines : JSONFile.girlWeightPercentileLines
        guard let url = bundledJSONURL(name: jsonName, subdirectory: JSONFile.percentileLinesSubfolder, bundle: bundle),
              let data = try? Data(contentsOf: url),
              let entries = try? JSONDecoder().decode([BabyPercentileLineEntry].self, from: data)
        else { return [] }
        return entries
    }

    /// Returns WHO percentile reference points filtered to a date range and converted to display units.
    ///
    /// - Parameters:
    ///   - biologicalSex: `"male"` / `"female"` (defaults to male when `nil` or unrecognized).
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
        let entries = loadPercentileEntries(biologicalSex: biologicalSex)
        guard !entries.isEmpty else { return [] }

        let startDay = max(0, calendar.dateComponents([.day], from: birthday, to: dateRange.lowerBound).day ?? 0)
        let endDay = max(0, calendar.dateComponents([.day], from: birthday, to: dateRange.upperBound).day ?? 0)

        // Include one boundary entry outside each edge for smooth line continuity.
        let filtered = entries.filter { $0.day >= startDay - 8 && $0.day <= endDay + 8 }

        let lines = BabyPercentileLine.allCases
        var points: [BabyPercentileChartPoint] = []
        points.reserveCapacity(filtered.count * lines.count)

        for entry in filtered {
            guard let date = calendar.date(byAdding: .day, value: entry.day, to: birthday) else { continue }
            for line in lines {
                points.append(BabyPercentileChartPoint(
                    date: date,
                    value: convertDecigramsToDisplay(entry.value(for: line)),
                    line: line
                ))
            }
        }
        return points
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
}
