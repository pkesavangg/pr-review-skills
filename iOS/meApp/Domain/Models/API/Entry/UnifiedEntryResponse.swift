//
//  UnifiedEntryResponse.swift
//  meApp
//
//  Response model for the unified entries endpoint (MOB-384).
//

import Foundation

/// Response envelope returned by `POST /v3/entries/` (and the sync mode of
/// `GET /v3/entries/`). Entries are flat with server-side nulls stripped, so
/// every category-specific field is optional. `timestamp` is the server clock
/// captured for the batch; clients persist it as the next sync cursor.
struct UnifiedEntryResponse: Codable, Equatable {
    let entries: [UnifiedEntryResult]
    let timestamp: String?
}

/// A single flat entry returned by the unified entries endpoint.
///
/// The shape is shared between the POST write response and the GET read response.
/// MOB-384 only consumes the write response; nulls are stripped server-side, so
/// only the fields relevant to the entry's `category` are populated.
struct UnifiedEntryResult: Codable, Equatable {
    // MARK: - Common
    let category: String?
    let entryId: String?
    let operationType: String?
    let entryTimestamp: String?
    let serverTimestamp: String?
    let source: String?

    // MARK: - Weight fields
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?
    let boneMass: Int?
    let impedance: Int?
    let unit: String?

    // MARK: - BP fields
    let systolic: Int?
    let diastolic: Int?
    let pulse: Int?
    let note: String?
}
