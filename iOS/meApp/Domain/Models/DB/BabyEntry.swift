/// Stores baby scale measurement data for each baby entry.
///
/// | Column Name | Type   | Description                              |
/// |-------------|--------|------------------------------------------|
/// | babyId      | string | FK to Baby.id                            |
/// | length      | int    | Baby length measurement                  |
/// | weight      | int    | Baby weight measurement                  |
/// | note        | string | User note for the entry                  |

import Foundation
import SwiftData

@Model
final class BabyEntry {
    /// FK to Baby.id
    var babyId: String
    /// Baby length measurement
    var length: Int
    /// Baby weight measurement
    var weight: Int
    /// User note for the entry
    var note: String

    init(
        babyId: String,
        length: Int,
        weight: Int,
        note: String
    ) {
        self.babyId = babyId
        self.length = length
        self.weight = weight
        self.note = note
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
