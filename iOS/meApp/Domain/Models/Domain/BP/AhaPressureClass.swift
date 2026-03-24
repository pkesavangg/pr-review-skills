//
//  AhaPressureClass.swift
//  meApp
//

import SwiftUI

/// AHA (American Heart Association) blood pressure classification.
///
/// Colors show where a reading falls — from Normal to Hypertensive Crisis —
/// based on American Heart Association guidelines.
enum AhaPressureClass: String, CaseIterable, Identifiable {
    case normal
    case elevated
    case hypertensionStage1
    case hypertensionStage2
    case hypertensiveCrisis

    var id: String { rawValue }

    // MARK: - Classification

    /// Classifies a blood pressure reading per AHA guidelines.
    static func classify(systolic: Int, diastolic: Int) -> AhaPressureClass {
        if systolic > 180 || diastolic > 120 { return .hypertensiveCrisis }
        if systolic >= 140 || diastolic >= 90 { return .hypertensionStage2 }
        if systolic >= 130 || diastolic >= 80 { return .hypertensionStage1 }
        if systolic >= 120 && diastolic < 80 { return .elevated }
        return .normal
    }

    // MARK: - Display

    var label: String {
        switch self {
        case .normal:             return "Normal"
        case .elevated:           return "Elevated"
        case .hypertensionStage1: return "Hypertension Stage 1"
        case .hypertensionStage2: return "Hypertension Stage 2"
        case .hypertensiveCrisis: return "Hypertensive Crisis"
        }
    }

    var systolicRange: String {
        switch self {
        case .normal:             return "Less than 120"
        case .elevated:           return "120-129"
        case .hypertensionStage1: return "130-139"
        case .hypertensionStage2: return "140 or higher"
        case .hypertensiveCrisis: return "Higher than 180"
        }
    }

    var diastolicRange: String {
        switch self {
        case .normal:             return "Less than 80"
        case .elevated:           return "Less than 80"
        case .hypertensionStage1: return "80-89"
        case .hypertensionStage2: return "90 or higher"
        case .hypertensiveCrisis: return "Higher than 120"
        }
    }

    // MARK: - Colors

    /// The primary color for this pressure classification.
    /// TODO: Replace inline colors with asset catalog colors once design tokens are added.
    func color(theme: AppColors.Palette) -> Color {
        switch self {
        case .normal:             return theme.statusSuccess
        case .elevated:           return Color(red: 0.8, green: 0.68, blue: 0.0)   // yellow-600 placeholder
        case .hypertensionStage1: return Color(red: 0.66, green: 0.5, blue: 0.0)   // yellow-800 placeholder
        case .hypertensionStage2: return theme.statusError
        case .hypertensiveCrisis: return Color(red: 0.6, green: 0.0, blue: 0.0)    // red-900 placeholder
        }
    }

    /// Fallback color that doesn't require the theme (for previews / non-themed contexts).
    var fallbackColor: Color {
        switch self {
        case .normal:             return .green
        case .elevated:           return .yellow
        case .hypertensionStage1: return .orange
        case .hypertensionStage2: return .red
        case .hypertensiveCrisis: return Color(red: 0.6, green: 0, blue: 0)
        }
    }
}
