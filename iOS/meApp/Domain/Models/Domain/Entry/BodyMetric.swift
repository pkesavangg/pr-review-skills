import Foundation

enum BodyMetric: String, Codable, Equatable {
    case weight
    case bmi
    case bodyFat
    case muscleMass
    case water
    case pulse
    case boneMass
    case visceralFatLevel
    case subcutaneousFatPercent
    case proteinPercent
    case skeletalMusclePercent
    case bmr
    case metabolicAge
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
