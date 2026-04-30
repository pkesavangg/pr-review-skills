// Integrations.swift
// Struct for integration status flags

import Foundation

struct Integrations: Codable, Equatable {
    /// Indicates if Fitbit integration is enabled
    let isFitbitOn: Bool
    /// Indicates if MyFitnessPal integration is enabled
    let isMFPOn: Bool
    /// Indicates if Fitbit integration is valid
    let isFitbitValid: Bool
    /// Indicates if MyFitnessPal integration is valid
    let isMFPValid: Bool
    /// Indicates if HealthKit integration is enabled
    let isHealthKitOn: Bool
    /// Indicates if Health Connect integration is enabled
    let isHealthConnectOn: Bool
}
