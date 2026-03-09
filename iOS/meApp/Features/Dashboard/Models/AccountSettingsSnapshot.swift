import Foundation

/// Snapshot of account settings for consolidated subscription
/// Used to detect changes across multiple settings in a single subscription
struct AccountSettingsSnapshot: Equatable {
    let weightUnit: WeightUnit?
    let isWeightlessOn: Bool?
    let weightlessWeight: Double?
    let goalWeight: Double?
    let initialWeight: Double?
    let goalType: GoalType?

    init(from account: Account?) {
        self.weightUnit = account?.weightSettings?.weightUnit
        self.isWeightlessOn = account?.weightlessSettings?.isWeightlessOn
        self.weightlessWeight = account?.weightlessSettings?.weightlessWeight
        self.goalWeight = account?.goalSettings?.goalWeight
        self.initialWeight = account?.goalSettings?.initialWeight
        self.goalType = account?.goalSettings?.goalType
    }
}
