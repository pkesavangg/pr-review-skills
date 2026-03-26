//
//  Baby.swift
//  meApp
//

/// Stores child profiles registered under a user account.
/// Each baby appears as a separate item in the product-type header dropdown.
///
/// | Column Name       | Type    | Description                                    |
/// |-------------------|---------|------------------------------------------------|
/// | id                | string  | Unique baby ID (PK, server-assigned or UUID)   |
/// | accountId         | string  | FK to the parent account                       |
/// | name              | string  | Baby's display name ("Tammy Thompson")         |
/// | deviceId          | string? | FK to the linked baby scale Device (optional)  |
/// | isSynced          | bool    | Whether this record is synced to the server    |
/// | birthday          | date?   | Baby's date of birth                           |
/// | biologicalSex     | string? | "male" / "female"                              |
/// | birthLengthInches | double? | Birth length in inches                         |
/// | birthWeightLbs    | double? | Birth weight pounds component                  |
/// | birthWeightOz     | double? | Birth weight ounces component                  |

import Foundation
import SwiftData

@Model
final class Baby {
    @Attribute(.unique) var id: String
    var accountId: String
    var name: String
    var deviceId: String?
    var isSynced: Bool
    var birthday: Date?
    var biologicalSex: String?
    var birthLengthInches: Double?
    var birthWeightLbs: Double?
    var birthWeightOz: Double?

    init(id: String = UUID().uuidString,
         accountId: String,
         name: String,
         deviceId: String? = nil,
         isSynced: Bool = false,
         birthday: Date? = nil,
         biologicalSex: String? = nil,
         birthLengthInches: Double? = nil,
         birthWeightLbs: Double? = nil,
         birthWeightOz: Double? = nil) {
        self.id = id
        self.accountId = accountId
        self.name = name
        self.deviceId = deviceId
        self.isSynced = isSynced
        self.birthday = birthday
        self.biologicalSex = biologicalSex
        self.birthLengthInches = birthLengthInches
        self.birthWeightLbs = birthWeightLbs
        self.birthWeightOz = birthWeightOz
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
