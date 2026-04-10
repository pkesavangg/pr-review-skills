//
//  SignupBaby.swift
//  meApp
//

import Foundation

/// Represents a baby added during the signup flow.
struct SignupBaby: Identifiable {
    let id = UUID()
    var name: String
    var birthday: Date
    var sex: Sex?
    var selectedWeightUnit: BabyWeightUnit
    var birthLengthInches: Double?
    var birthWeightLbs: Double?
    var birthWeightOz: Double?
}
