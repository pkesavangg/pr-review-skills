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
    public var displayName: String {
        switch self {
        case .weight: return "Weight"
        case .bmi: return "BMI"
        case .bodyFat: return "Body Fat"
        case .muscleMass: return "Muscle Mass"
        case .water: return "Body Water"
        case .pulse: return "Pulse"
        case .boneMass: return "Bone Mass"
        case .visceralFatLevel: return "Visceral Fat Level"
        case .subcutaneousFatPercent: return "Subcutaneous Fat"
        case .proteinPercent: return "Protein"
        case .skeletalMusclePercent: return "Skeletal Muscle"
        case .bmr: return "BMR"
        case .metabolicAge: return "Metabolic Age"
        }
    }
}
