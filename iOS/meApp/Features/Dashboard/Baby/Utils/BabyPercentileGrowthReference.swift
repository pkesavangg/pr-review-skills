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

    enum JSONFile {
        static let boyWeightMeasurements = "BoyWeightDecigrams"
        static let girlWeightMeasurements = "GirlWeightDecigrams"
        static let boyWeightPercentileLines = "BoyWeightDecigramsLine"
        static let girlWeightPercentileLines = "GirlWeightDecigramsLine"
        static let zTable = "ZTable"
        static let measurementsSubfolder = "measurements"
        static let percentileLinesSubfolder = "percentileLines"
    }

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
