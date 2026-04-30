//
//  IonicAccountData.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/08/25.
//

import Foundation
// MARK: - Ionic Account Data Model

/// Represents the account data structure from the Ionic app
struct IonicAccountData: Codable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
    let id: String
    let email: String
    let firstName: String
    let lastName: String?
    let gender: String
    let zipcode: String?
    let weightUnit: String
    let isWeightlessOn: Bool?
    let preferredInputMethod: String?
    let height: Int
    let activityLevel: String
    let dob: String
    let weightlessBodyFat: Double?
    let weightlessMuscle: Double?
    let weightlessTimestamp: String?
    let weightlessWeight: Int?
    let isStreakOn: Bool?
    let dashboardType: String
    let dashboardMetrics: [String]
    let goalType: String? // was non-optional
    let goalWeight: Int?
    let initialWeight: Int?
    let shouldSendEntryNotifications: Bool
    let shouldSendWeightInEntryNotifications: Bool
    let isGoogleFitOn: Bool?
    let isGoogleFitValid: Bool?
    let isFitbitOn: Bool?
    let isFitbitValid: Bool?
    let isMFPOn: Bool?
    let isMFPValid: Bool?
    let isUAOn: Bool?
    let isUAValid: Bool?
    let isHealthConnectOn: Bool?
    let isHealthKitOn: Bool?
    let type: String?
}
