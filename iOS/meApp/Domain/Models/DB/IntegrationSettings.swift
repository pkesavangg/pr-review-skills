//
//  IntegrationSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//

import Foundation
import SwiftData

/// Stores third-party integration preferences & token-validity flags.
///
/// | Column Name         | Type    | Description                                               |
/// |---------------------|---------|-----------------------------------------------------------|
/// | accountId           | string  | Foreign key referencing the associated account            |
/// | isFitbitOn          | boolean | Fitbit integration enabled                                |
/// | isFitbitValid       | boolean | Fitbit token/session is valid                             |
/// | isHealthConnectOn   | boolean | Health Connect integration enabled (Android 13+)          |
/// | isHealthKitOn       | boolean | Apple HealthKit integration enabled                       |
/// | isMfpOn             | boolean | MyFitnessPal integration enabled                          |
/// | isMfpValid          | boolean | MyFitnessPal token/session is valid                       |
/// | isSynced            | boolean | Whether this record is synced with the server             |
@Model
final class IntegrationSettings {
    // MARK: - Stored Properties
    @Attribute(.unique) var id: String
    var accountId: String

    var isFitbitOn: Bool
    var isFitbitValid: Bool
    var isHealthConnectOn: Bool
    var isHealthKitOn: Bool
    var isMfpOn: Bool
    var isMfpValid: Bool
    var isSynced: Bool?

    // MARK: - Init
    init(accountId: String,
         isFitbitOn: Bool = false,
         isFitbitValid: Bool = false,
         isHealthConnectOn: Bool = false,
         isHealthKitOn: Bool = false,
         isMfpOn: Bool = false,
         isMfpValid: Bool = false,
         isSynced: Bool? = false) {

        self.id = UUID().uuidString
        self.accountId = accountId
        self.isFitbitOn = isFitbitOn
        self.isFitbitValid = isFitbitValid
        self.isHealthConnectOn = isHealthConnectOn
        self.isHealthKitOn = isHealthKitOn
        self.isMfpOn = isMfpOn
        self.isMfpValid = isMfpValid
        self.isSynced = isSynced
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
