//
//  Baby.swift
//  meApp
//

/// Stores child profiles registered under a user account.
/// Each baby appears as a separate item in the product-type header dropdown.
///
/// | Column Name       | Type    | Description                                    |
/// |-------------------|---------|------------------------------------------------|
/// | id                | string  | Unique baby ID (PK — client UUID until first sync, then server-assigned) |
/// | accountId         | string  | FK to the parent account                       |
/// | name              | string  | Baby's display name ("Tammy Thompson")         |
/// | deviceId          | string? | FK to the linked baby scale Device (optional)  |
/// | isSynced          | bool    | Whether the local record's pending changes are pushed to the server |
/// | isServerCreated   | bool    | Whether this profile exists on the server (POST done / came from server) |
/// | isDeleted         | bool    | Soft-delete tombstone: deleted locally, DELETE not yet pushed   |
/// | attempts          | int     | Failed sync attempts for the pending operation |
/// | isFailedToSync    | bool    | Pending op abandoned after exceeding the retry cap |
/// | birthday          | date?   | Baby's date of birth                           |
/// | biologicalSex     | string? | "male" / "female" / "private"                  |
/// | birthLengthInches | double? | Birth length in inches                         |
/// | birthWeightLbs    | double? | Birth weight pounds component                  |
/// | birthWeightOz     | double? | Birth weight ounces component                  |
///
/// Offline-first (MOB-1527): create/edit/delete write locally first and are reconciled by
/// `BabyService.syncBabies(for:)`. The pending server call is inferred from the flags:
/// - `!isServerCreated && !isDeleted` → `POST /v3/baby/` (then remap client id → server id)
/// - `isServerCreated && !isDeleted && !isSynced` → `PUT /v3/baby/{id}`
/// - `isServerCreated && isDeleted` → `DELETE /v3/baby/{id}` then purge locally
/// - `!isServerCreated && isDeleted` → never reached (purged locally at delete time, no server call)

import Foundation
import SwiftData

@Model
final class Baby {
    @Attribute(.unique) var id: String
    var accountId: String
    var name: String
    var deviceId: String?
    var isSynced: Bool
    /// True once the profile exists on the server (a create was confirmed, or the record was
    /// built from a server response). Drives POST-vs-PUT and purge-vs-tombstone on delete.
    var isServerCreated: Bool
    /// Soft-delete tombstone kept so an offline delete of a server profile can be pushed on
    /// reconnect. Rows with `isDeleted == true` are excluded from the published `babies` list.
    var isDeleted: Bool
    /// Number of failed attempts to push this record's pending operation.
    var attempts: Int
    /// Whether the pending operation was abandoned after exceeding the retry cap.
    var isFailedToSync: Bool
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
         isServerCreated: Bool = false,
         isDeleted: Bool = false,
         attempts: Int = 0,
         isFailedToSync: Bool = false,
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
        self.isServerCreated = isServerCreated
        self.isDeleted = isDeleted
        self.attempts = attempts
        self.isFailedToSync = isFailedToSync
        self.birthday = birthday
        self.biologicalSex = biologicalSex
        self.birthLengthInches = birthLengthInches
        self.birthWeightLbs = birthWeightLbs
        self.birthWeightOz = birthWeightOz
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
