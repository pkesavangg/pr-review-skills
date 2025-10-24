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
        case .weight: return MetricStrings.weight
        case .bmi: return MetricStrings.bmi
        case .bodyFat: return MetricStrings.bodyFat
        case .muscleMass: return MetricStrings.muscleMass
        case .water: return MetricStrings.bodyWater
        case .pulse: return MetricStrings.heartRate
        case .boneMass: return MetricStrings.boneMass
        case .visceralFatLevel: return MetricStrings.visceralFat
        case .subcutaneousFatPercent: return MetricStrings.subcutaneousFat
        case .proteinPercent: return MetricStrings.protein
        case .skeletalMusclePercent: return MetricStrings.skeletalMuscles
        case .bmr: return MetricStrings.basalMetabolicRate
        case .metabolicAge: return MetricStrings.metabolicAge
        }
    }
}
