//
//  EntriesCSVRequest.swift
//  meApp
//
//  Query parameters for the unified `GET /v3/entries/csv` export endpoint (MOB-385).
//

import Foundation

/// Encapsulates the query parameters for the unified CSV export endpoint.
///
/// `GET /v3/entries/csv` exports entries across all products or scoped to a single
/// `category`. When `download` is `true` the server streams a CSV file; otherwise it
/// emails the report and responds with `{ "sent": true }`.
struct EntriesCSVRequest: Equatable {
    /// `weight`, `bp`, or `baby`. Omit (`nil`) to export every product.
    let category: String?
    /// Required when `category == "baby"` — scopes the export to one baby profile.
    let babyId: String?
    /// `true` streams a downloadable file; `false` (default) emails the report.
    let download: Bool
    /// Timezone offset in minutes applied to the exported `Date/Time` column. Defaults to 0.
    let utcOffset: Int
    /// Baby-only sub-type filter (e.g. `weight`, `feedingBottle`).
    let entryType: String?

    init(
        category: String? = nil,
        babyId: String? = nil,
        download: Bool = false,
        utcOffset: Int = 0,
        entryType: String? = nil
    ) {
        self.category = category
        self.babyId = babyId
        self.download = download
        self.utcOffset = utcOffset
        self.entryType = entryType
    }
}
