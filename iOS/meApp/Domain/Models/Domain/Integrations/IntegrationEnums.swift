// IntegrationEnums.swift
// All enums related to integrations and health services

import Foundation

/// Enum for integration types
enum IntegrationType: String, Codable {
    case google = "google"
    case fitbit = "fitbit"
    case myFitnessPal = "mfp"
    case underArmour = "ua"
    case healthConnect = "healthconnect"
    case healthKit = "healthkit"
}

enum Keys {
    static let integrationInfo = "integration_info_"
    static let integrationKeys = "integration_keys"
}
