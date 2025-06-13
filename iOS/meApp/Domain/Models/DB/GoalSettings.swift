//
//  GoalSettings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//


import Foundation
import SwiftData

/// Stores user's goal-related information.
///
/// | Column Name   | Type    | Description                                     |
/// | ------------- | ------- | ----------------------------------------------- |
/// | id            | string  | Auto-generated unique identifier                |
/// | accountId     | string  | Reference to the related Account (foreign key) |
/// | goalType      | string  | Type of goal (e.g., weight loss, maintenance)  |
/// | initialWeight        | Double   | Initial or current weight                      |
/// | goalWeight    | string  | Target weight                                   |
/// | goalPercent   | float   | Percentage of goal progress                    |
/// | isSynced      | boolean | Whether this record is synced with server      |

@Model
final class GoalSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var goalType: GoalType?
    var initialWeight: Double?
    var goalWeight: String?
    var goalPercent: Double?
    var isSynced: Bool?

    init(accountId: String, goalType: GoalType?, initialWeight: Double?, goalWeight: String?, goalPercent: Double? = nil, isSynced: Bool? = false) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.goalType = goalType
        self.initialWeight = initialWeight
        self.goalWeight = goalWeight
        self.goalPercent = goalPercent
        self.isSynced = isSynced
    }
}

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension GoalSettings: @unchecked Sendable {}
