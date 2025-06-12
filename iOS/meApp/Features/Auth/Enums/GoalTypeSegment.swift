import Foundation

enum GoalTypeSegment: String, CaseIterable, Identifiable {
    case maintain = "Maintain"
    case loseGain = "Lose / Gain"
    
    var id: String { rawValue }
    
    var goalTypeValue: String {
        switch self {
        case .maintain:
            return GoalType.maintain.rawValue
        case .loseGain:
            return GoalType.lose.rawValue // Default to lose when in lose/gain mode
        }
    }
    
    static func fromGoalType(_ value: String) -> GoalTypeSegment {
        switch value {
        case GoalType.maintain.rawValue:
            return .maintain
        default:
            return .loseGain
        }
    }
} 