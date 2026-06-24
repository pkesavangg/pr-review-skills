//
//  BabyLengthUnit.swift
//  meApp
//

import Foundation

/// Length unit options for the baby manual entry toggle.
enum BabyLengthUnit: String, CaseIterable, Identifiable, Hashable {
    case inches = "IN"
    case cm = "CM"

    var id: String { rawValue }
}
