//
//  AppleHealthIntegrationState.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//

enum AppleHealthIntegrationState {
    case permissionsAllowed
    case permissionsNotAllowed
    case integrationComplete
    case integrationFailed
    case userConflict
}

// Conform to `Identifiable` & `Hashable` so the enum can be used with
// SwiftUI `.sheet(item:)` / `.fullScreenCover(item:)` APIs.
extension AppleHealthIntegrationState: Identifiable, Hashable {
    public var id: String {
        switch self {
        case .permissionsAllowed:     return "permissionsAllowed"
        case .permissionsNotAllowed:  return "permissionsNotAllowed"
        case .integrationComplete:    return "integrationComplete"
        case .integrationFailed:      return "integrationFailed"
        case .userConflict:           return "userConflict"
        }
    }
}
