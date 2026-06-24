import Foundation
@testable import meApp

enum AppSyncTabStoreTestFixtures {
    static func makeActiveAccount(id: String = "appsync-account", unit: WeightUnit = .lb, height: String = "170") -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            isActiveAccount: true,
            weightUnit: unit,
            weightHeight: height
        )
    }

    static func makeMetrics(
        storedWeight: Int = 1800,
        storedBMI: Int? = 250,
        storedBodyFat: Int? = 150,
        storedWaterWeight: Int? = 550,
        storedMuscleMass: Int? = 380,
        isMetric: Bool = false,
        rawDisplayWeightKg: Double? = 81.65
    ) -> AppSyncEntryMetrics {
        AppSyncEntryMetrics(
            storedWeight: storedWeight,
            storedBMI: storedBMI,
            storedBodyFat: storedBodyFat,
            storedWaterWeight: storedWaterWeight,
            storedMuscleMass: storedMuscleMass,
            isMetric: isMetric,
            rawDisplayWeightKg: rawDisplayWeightKg
        )
    }
}
