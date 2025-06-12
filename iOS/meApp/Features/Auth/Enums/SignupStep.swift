import Foundation

/// Represents each step in the signup flow
enum SignupStep: Int, CaseIterable {
    case name = 0
    case dateOfBirth
    case sex
    case height
    case goal
    case email
    case password

    var index: Int { rawValue }
}
