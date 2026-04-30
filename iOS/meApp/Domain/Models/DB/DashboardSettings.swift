//
//  DashboardSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//


import Foundation
import SwiftData

/// Stores user's dashboard configuration.
///
/// | Column Name         | Type    | Description                                           |
/// |--------------------|---------|-------------------------------------------------------|
/// | accountId          | string  | Foreign key referencing the associated Account       |
/// | dashboardMetrics   | string  | Comma-separated or encoded list of dashboard metrics |
/// | progressMetrics    | string  | Comma-separated list of progress metrics             |
/// | dashboardType      | string  | Type or style of dashboard view                      |
/// | isSynced           | boolean | Whether the settings are synced with the server      |

@Model
final class DashboardSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var dashboardMetrics: String?
    var progressMetrics: String?
    var dashboardType: String?
    var isSynced: Bool?

    init(accountId: String,
         dashboardMetrics: String? = nil,
         progressMetrics: String? = nil,
         dashboardType: String? = nil,
         isSynced: Bool? = false) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.dashboardMetrics = dashboardMetrics
        self.progressMetrics = progressMetrics
        self.dashboardType = dashboardType
        self.isSynced = isSynced
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
