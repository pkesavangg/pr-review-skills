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

    /// The primary color for this pressure classification. All colors are asset-catalog
    /// backed (light + dark variants) so the scale renders correctly in Dark Mode.
    func color(theme: AppColors.Palette) -> Color {
        switch self {
        case .normal:             return theme.statusSuccess
        case .elevated:           return ColorTokens.bpElevated
        case .hypertensionStage1: return ColorTokens.bpStage1
        case .hypertensionStage2: return theme.statusError
        case .hypertensiveCrisis: return ColorTokens.bpCrisis
        }
    }

    /// Asset-backed color that doesn't require the theme (for previews / non-themed contexts).
    var fallbackColor: Color {
        switch self {
        case .normal:             return ColorTokens.green800
        case .elevated:           return ColorTokens.bpElevated
        case .hypertensionStage1: return ColorTokens.bpStage1
        case .hypertensionStage2: return ColorTokens.red800
        case .hypertensiveCrisis: return ColorTokens.bpCrisis
        }
    }
}
