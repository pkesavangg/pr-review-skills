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

/// Enum for integration operation types
enum OperationType: String, Codable {
    case save = "save"
    case remove = "remove"
}
