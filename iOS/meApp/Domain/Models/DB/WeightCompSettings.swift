import Foundation
import SwiftData

/// Stores user weight and composition related settings.
///
/// | Column Name     | Type    | Description                                     |
/// | --------------- | ------- | ----------------------------------------------- |
/// | id              | string  | Auto-generated unique identifier                |
/// | accountId       | string  | Reference to the related Account                |
/// | height          | string  | Height of the user                              |
/// | activityLevel   | string  | User's activity level (low, moderate, high)     |
/// | weightUnit      | string  | Unit of weight measurement (kg/lb)              |
/// | isSynced        | boolean | Whether settings are synced with the server     |

@Model
final class WeightCompSettings {
    @Attribute(.unique) var id: String
    var accountId: String
    var height: String?
    var activityLevel: ActivityLevel?
    var weightUnit: WeightUnit?
    var isSynced: Bool?
    
    init(accountId: String, height: String?, activityLevel: ActivityLevel?, weightUnit: WeightUnit?) {
        self.id = UUID().uuidString
        self.accountId = accountId
        self.height = height
        self.activityLevel = activityLevel
        self.weightUnit = weightUnit
        self.isSynced = false
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
