//
//  NotificationSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//

import Foundation
import SwiftData

/// Stores user's notification preferences.
///
/// | Column Name                   | Type    | Description                                              |
/// |------------------------------|---------|----------------------------------------------------------|
/// | accountId                    | string  | Foreign key to reference related account                |
/// | shouldSendEntryNotifications    | boolean | Whether entry notifications are turned on               |
/// | shouldSendWeightInEntryNotifications   | boolean | Whether to show weight value in notifications           |
/// | isSynced                     | boolean | Whether this record is synced with the server           |

@Model
final class NotificationSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var shouldSendEntryNotifications: Bool
    var shouldSendWeightInEntryNotifications: Bool
    var isSynced: Bool?

    init(accountId: String,
         shouldSendEntryNotifications: Bool = true,
         shouldSendWeightInEntryNotifications: Bool = false,
         isSynced: Bool? = false) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.shouldSendEntryNotifications = shouldSendEntryNotifications
        self.shouldSendWeightInEntryNotifications = shouldSendWeightInEntryNotifications
        self.isSynced = isSynced
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
