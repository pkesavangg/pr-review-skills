// Integrations.swift
// Struct for integration status flags

import Foundation

struct Integrations: Codable, Equatable {
    /// Indicates if Fitbit integration is enabled
    let isFitbitOn: Bool
    /// Indicates if Google Fit integration is enabled
    let isGoogleFitOn: Bool
    /// Indicates if MyFitnessPal integration is enabled
    let isMFPOn: Bool
    /// Indicates if Under Armour integration is enabled
    let isUAOn: Bool
    /// Indicates if Fitbit integration is valid
    let isFitbitValid: Bool
    /// Indicates if Google Fit integration is valid
    let isGoogleFitValid: Bool
    /// Indicates if MyFitnessPal integration is valid
    let isMFPValid: Bool
    /// Indicates if Under Armour integration is valid
    let isUAValid: Bool
    /// Indicates if HealthKit integration is enabled
    let healthkit: Bool
    /// Indicates if Health Connect integration is enabled
    let isHealthConnectOn: Bool
}
