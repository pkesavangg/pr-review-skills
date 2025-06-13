//
//  WeightlessSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//


import Foundation
import SwiftData

/// Stores weightless mode settings per user account.
///
/// | Column Name           | Type    | Description                                             |
/// |----------------------|---------|---------------------------------------------------------|
/// | accountId            | string  | Foreign key to reference related account               |
/// | isWeightlessOn       | boolean | Whether weightless mode is enabled                     |
/// | weightlessTimestamp  | string  | Timestamp of the last weightless mode update           |
/// | weightlessWeight     | Double   | Associated weight for weightless mode                 |
/// | isSynced             | boolean | Whether this record is synced with the server          |

@Model
final class WeightlessSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var isWeightlessOn: Bool
    var weightlessTimestamp: String?
    var weightlessWeight: Double?
    var isSynced: Bool?

    // Relationship back to Account
    @Relationship var account: Account?

    init(accountId: String,
         isWeightlessOn: Bool = false,
         weightlessTimestamp: String? = nil,
         weightlessWeight: Double? = nil,
         isSynced: Bool? = false) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.isWeightlessOn = isWeightlessOn
        self.weightlessTimestamp = weightlessTimestamp
        self.weightlessWeight = weightlessWeight
        self.isSynced = isSynced
    }
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension WeightlessSettings: @unchecked Sendable {}
