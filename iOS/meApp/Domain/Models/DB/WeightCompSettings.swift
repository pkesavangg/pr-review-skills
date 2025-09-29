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
    var height: String?Task 181: Fatal error: Never access a full future backing data - PersistentIdentifier(id: SwiftData.PersistentIdentifier.ID(backing: SwiftData.PersistentIdentifier.PersistentIdentifierBacking.managedObjectID(0xa64ae59331c1ec5e <x-coredata://BF0B61D9-CC32-4B45-A876-01D1E2739C86/WeightCompSettings/p1>))) with nil
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

/// Marked @unchecked Sendable due to SwiftData's built-in thread safety, allowing async/concurrent use.
extension WeightCompSettings: @unchecked Sendable {}
