//
//  Streaks.swift
//  meApp
//
//  Created by Lakshmi Priya on 15/07/25.
//

import Foundation
import SwiftUI

enum StreakTrend {
    case broken
    case starting
    case building
    case record

    var description: String {
        switch self {
        case .broken: return "Streak broken"
        case .starting: return "Getting started"
        case .building: return "Building momentum"
        case .record: return "Record streak!"
        }
    }

    var color: Color {
        switch self {
        case .broken: return .red
        case .starting: return .orange
        case .building: return .blue
        case .record: return .green
        }
    }
}

enum StreakMomentum {
    case accelerating
    case steady
    case slowing

    var description: String {
        switch self {
        case .accelerating: return "Accelerating"
        case .steady: return "Steady progress"
        case .slowing: return "Slowing down"
        }
    }

    var icon: String {
        switch self {
        case .accelerating: return "arrow.up.circle.fill"
        case .steady: return "arrow.right.circle.fill"
        case .slowing: return "arrow.down.circle.fill"
        }
    }
}

