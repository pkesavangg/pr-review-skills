import Foundation

struct OperationDTO: Codable {
    let accountId: String?
    let operationType: String?
    let entryTimestamp: String?
    let weight: Double?
    let bodyFat: Double?
    let muscleMass: Double?
    let boneMass: Double?
    let water: Double?
    let bmi: Double?
    let source: String?
    let unit: String?
    let impedance: Double?
    let pulse: Double?
    let visceralFatLevel: Double?
    let subcutaneousFatPercent: Double?
    let proteinPercent: Double?
    let skeletalMusclePercent: Double?
    let bmr: Double?
    let metabolicAge: Double?
    let serverTimestamp: String?
}
