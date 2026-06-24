//
//  BabyWeightUnit.swift
//  meApp
//

import Foundation

/// Weight unit options for baby manual entry conversion logic.
enum BabyWeightUnit: String, CaseIterable, Identifiable, Hashable {
    case lbsOz = "LBS/OZ"
    case lb = "LB"
    case kg = "KG"

    var id: String { rawValue }

    /// User-facing label for the weight-unit selector ("lbs", "lbs/oz", "kg").
    /// Distinct from `rawValue`, which is the canonical storage/logic value.
    var displayName: String {
        switch self {
        case .lbsOz: return "lbs/oz"
        case .lb: return "lbs"
        case .kg: return "kg"
        }
    }
}
