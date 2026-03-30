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
    var birthLength: String
    var birthWeight: String
}
