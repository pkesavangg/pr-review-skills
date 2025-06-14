//
//  GoalTypeSegment.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/06/25.
//

import Foundation

enum GoalTypeSegment: String, CaseIterable, Identifiable {
    case maintain = "Maintain"
    case loseGain = "Lose / Gain"
    
    var id: String { rawValue }
    
    // Use a constant for losegain
    static let losegainValue = "losegain"
    
    var goalTypeValue: String {
        switch self {
        case .maintain:
            return GoalType.maintain.rawValue
        case .loseGain:
            return GoalTypeSegment.losegainValue
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
