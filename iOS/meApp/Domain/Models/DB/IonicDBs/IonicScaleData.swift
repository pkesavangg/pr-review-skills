//
//  IonicScaleData.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 08/08/25.
//

import Foundation
// MARK: - Ionic Scale Data Models
/// Model representing scale data structure from Ionic app
struct IonicScaleData: Codable {
    let id: String?
    let nickname: String?
    let type: String?
    let createdAt: String?
    let userNumber: Int?
    let scaleToken: String?
    let mac: String?
    let broadcastId: Int?
    let password: Int?
    let sku: String?
    let name: String?
    let peripheralIdentifier: String?
    let preference: IonicR4ScalePreference?
    let latestVersion: String?
    let isDeleted: Bool?
    let isTemporary: Bool?
}

/// Model representing R4 scale preference data from Ionic app
struct IonicR4ScalePreference: Codable {
    let tzOffset: Int
    let timeFormat: String
    let displayName: String
    let displayMetrics: [String]
    let shouldMeasurePulse: Bool
    let shouldMeasureImpedance: Bool
    let shouldFactoryReset: Bool
    let wifiFotaScheduleTime: Int?
}
