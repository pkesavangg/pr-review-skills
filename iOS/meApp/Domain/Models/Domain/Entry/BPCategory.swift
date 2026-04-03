//
//  BPCategory.swift
//  meApp
//

import SwiftUI

/// Blood pressure classification based on AHA guidelines.
enum BPCategory {
    case normal
    case elevated
    case highStage1
    case highStage2

    /// Classify a blood pressure reading.
    static func classify(systolic: Int, diastolic: Int) -> BPCategory {
        if systolic >= 140 || diastolic >= 90 {
            return .highStage2
        } else if systolic >= 130 || diastolic >= 80 {
            return .highStage1
        } else if systolic >= 120 && diastolic < 80 {
            return .elevated
        } else {
            return .normal
        }
    }

    /// Returns the appropriate theme color for this BP category.
    func color(theme: AppColors.Palette) -> Color {
        switch self {
        case .normal:
            return theme.actionSuccess
        case .elevated:
            return theme.statusError
        case .highStage1, .highStage2:
            return theme.actionError
        }
    }
}
