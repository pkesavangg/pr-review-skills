/// Stores baby scale measurement data for each baby entry.
///
/// | Column Name | Type    | Description                              |
/// |-------------|---------|------------------------------------------|
/// | babyId      | string  | FK to Baby.id                            |
/// | length      | int     | Baby length in millimeters                |
/// | weight      | int     | Baby weight in decigrams                  |
/// | source      | string? | Scale SKU (e.g. "0220", "0222") or nil for manual |

import Foundation
import SwiftData

@Model
final class BabyEntry {
    /// FK to Baby.id
    var babyId: String
    /// Baby length in millimeters (e.g. 615 = 24.2 inches)
    var length: Int
    /// Baby weight in decigrams (e.g. 545660 = 54.566 kg)
    var weight: Int
    /// Scale SKU that produced the measurement (e.g. "0220", "0222"), nil for manual entries
    var source: String?

    init(
        babyId: String,
        length: Int,
        weight: Int,
        source: String? = nil
    ) {
        self.babyId = babyId
        self.length = length
        self.weight = weight
        self.source = source
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts.
