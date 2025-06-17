//
//  StreaksSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//


import Foundation
import SwiftData

/// Stores user's streak tracking settings.
///
/// | Column Name       | Type    | Description                                   |
/// | ----------------- | ------- | --------------------------------------------- |
/// | id                | string  | Auto-generated unique identifier              |
/// | accountId         | string  | Reference to the related Account (foreign key)|
/// | isStreakOn        | boolean | Whether streak tracking is enabled            |
/// | streakTimestamp   | string  | Timestamp representing last streak update     |
/// | isSynced          | boolean | Whether this data is synced with the server   |

@Model
final class StreaksSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var isStreakOn: Bool
    var streakTimestamp: String?
    var isSynced: Bool?

    init(accountId: String, isStreakOn: Bool, streakTimestamp: String? = nil, isSynced: Bool? = false) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.isStreakOn = isStreakOn
        self.streakTimestamp = streakTimestamp
        self.isSynced = isSynced
    }
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension StreaksSettings: @unchecked Sendable {}
