//
//  BabyGrowthPercentileZTable.swift
//  meApp
//
//  Loads the standard normal lookup table from bundled JSON only (no duplicate in-source copy).
//  Source of truth: `Resources/Data/BabyGrowthPercentile/measurements/ZTable.json`
//  (same values as GGBluetoothPackageTest `ZTABLE.swift`).
//

import Foundation

enum BabyGrowthPercentileZTable {
    /// Row keys like `"-1.0"`, column fractions from row `"z"`.
    static let table: [String: [Double]] = {
        guard
            let url = BabyPercentileGrowthReference.bundledJSONURL(
                name: BabyPercentileGrowthReference.JSONFile.zTable,
                subdirectory: BabyPercentileGrowthReference.JSONFile.measurementsSubfolder
            ),
            let data = try? Data(contentsOf: url),
            let decoded = try? JSONDecoder().decode([String: [Double]].self, from: data)
        else {
            assertionFailure("BabyGrowthPercentile: ZTable.json missing or invalid")
            return [:]
        }
        return decoded
    }()
}
