import Foundation

enum BodyMetric: String, Codable, Equatable {
    case weight = "weight"
    case bmi = "bmi"
    case bodyFat = "bodyFat"
    case muscleMass = "muscleMass"
    case water = "water"
    case pulse = "pulse"
    case boneMass = "boneMass"
    case visceralFatLevel = "visceralFatLevel"
    case subcutaneousFatPercent = "subcutaneousFatPercent"
    case proteinPercent = "proteinPercent"
    case skeletalMusclePercent = "skeletalMusclePercent"
    case bmr = "bmr"
    case metabolicAge = "metabolicAge"
}

extension BodyMetric: CaseIterable, Identifiable, Hashable {
    public var id: String { rawValue }
}
