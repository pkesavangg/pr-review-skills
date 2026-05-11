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
}
