//
//  GoalTrend.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation

enum GoalTrend {
    case improving
    case neutral
    case declining

    var description: String {
        switch self {
        case .improving: return "On track"
        case .neutral: return "Stable"
        case .declining: return "Needs attention"
        }
    }
}
