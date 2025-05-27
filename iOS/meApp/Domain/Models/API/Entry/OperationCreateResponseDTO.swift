import Foundation

struct OperationCreateResponseDTO: Codable {
    let operationType: String?
    let entryTimestamp: String?
    let serverTimestamp: String?
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
}
