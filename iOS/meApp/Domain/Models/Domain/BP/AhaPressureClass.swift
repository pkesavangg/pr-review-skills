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
        case .normal:             return BpmDashboardStrings.ahaNormal
        case .elevated:           return BpmDashboardStrings.ahaElevated
        case .hypertensionStage1: return BpmDashboardStrings.ahaHypertensionStage1
        case .hypertensionStage2: return BpmDashboardStrings.ahaHypertensionStage2
        case .hypertensiveCrisis: return BpmDashboardStrings.ahaHypertensiveCrisis
        }
    }

    var systolicRange: String {
        switch self {
        case .normal:             return BpmDashboardStrings.systolicNormal
        case .elevated:           return BpmDashboardStrings.systolicElevated
        case .hypertensionStage1: return BpmDashboardStrings.systolicStage1
        case .hypertensionStage2: return BpmDashboardStrings.systolicStage2
        case .hypertensiveCrisis: return BpmDashboardStrings.systolicCrisis
        }
    }

    var diastolicRange: String {
        switch self {
        case .normal:             return BpmDashboardStrings.diastolicNormal
        case .elevated:           return BpmDashboardStrings.diastolicElevated
        case .hypertensionStage1: return BpmDashboardStrings.diastolicStage1
        case .hypertensionStage2: return BpmDashboardStrings.diastolicStage2
        case .hypertensiveCrisis: return BpmDashboardStrings.diastolicCrisis
        }
    }

    // MARK: - Colors

    /// The primary color for this pressure classification.
    /// Replace inline colors with asset catalog colors once design tokens are added.
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
