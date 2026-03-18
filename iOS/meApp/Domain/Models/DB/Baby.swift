//
//  Baby.swift
//  meApp
//

/// Stores child profiles registered under a user account.
/// Each baby appears as a separate item in the product-type header dropdown.
///
/// | Column Name   | Type    | Description                                    |
/// |---------------|---------|------------------------------------------------|
/// | id            | string  | Unique baby ID (PK, server-assigned or UUID)   |
/// | accountId     | string  | FK to the parent account                       |
/// | name          | string  | Baby's display name ("Tammy Thompson")         |
/// | deviceId      | string? | FK to the linked baby scale Device (optional)  |
/// | isSynced      | bool    | Whether this record is synced to the server    |
///
/// NOTE: This is a preliminary schema. Fields will be altered/extended in
/// future tasks as baby profile requirements are fully decided (e.g., date
/// of birth, gender, birth weight may be added later).

import Foundation
import SwiftData

@Model
final class Baby {
    @Attribute(.unique) var id: String
    var accountId: String
    var name: String
    var deviceId: String?
    var isSynced: Bool

    init(id: String = UUID().uuidString,
         accountId: String,
         name: String,
         deviceId: String? = nil,
         isSynced: Bool = false) {
        self.id = id
        self.accountId = accountId
        self.name = name
        self.deviceId = deviceId
        self.isSynced = isSynced
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
