import Foundation

/// Snapshot of account settings for consolidated subscription
/// Used to detect changes across multiple settings in a single subscription
struct AccountSettingsSnapshot: Equatable {
    let weightUnit: WeightUnit?
    /// Kids' units — tracked so a "My Kids" unit change re-renders the baby dashboard the same way
    /// an adult unit change re-renders the weight dashboard.
    let measurementUnits: MeasurementUnits?
    let isWeightlessOn: Bool?
    let weightlessWeight: Double?
    let goalWeight: Double?
    let initialWeight: Double?
    let goalType: GoalType?

    init(from account: AccountSnapshot?) {
        self.weightUnit = account?.weightUnit
        self.measurementUnits = account?.babyMeasurementUnits
        self.isWeightlessOn = account?.isWeightlessOn
        self.weightlessWeight = account?.weightlessWeight
        self.goalWeight = account?.goalWeight
        self.initialWeight = account?.initialWeight
        self.goalType = account?.goalType
    }
}
