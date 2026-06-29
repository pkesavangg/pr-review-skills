//
//  BabyEntryRequest.swift
//  meApp
//
//  Builds baby `UnifiedEntryRequest`s for the unified `POST /v3/entries/` endpoint (MOB-386).
//
//  Baby entries share the unified entries endpoint with weight/BP but use the baby wire
//  fields (`babyId`, `entryId`, `entryType`, `babyWeightDecigrams`/`babyLengthMillimeters`,
//  `entryNote`). The local `BabyEntry` stores weight and length together, whereas the API
//  models them as separate per-`entryType` rows — so one local baby entry can expand into a
//  `weight` request and a `measureLength` request.
//

import Foundation

/// Baby entry sub-types in scope per the Baby App audit (the others are UI stubs that never persist).
enum BabyEntryType: String, Codable, Equatable {
    case weight
    case measureLength
}

enum BabyEntryRequest {
    /// Suffix appended to the entry id of the length row so it doesn't collide with the
    /// weight row produced from the same local baby entry.
    static let lengthEntryIdSuffix = "-length"

    /// Builds the unified request(s) for a baby entry.
    ///
    /// - create: a `weight` request when `weightDecigrams > 0` and a `measureLength` request
    ///   when `lengthMillimeters > 0`.
    /// - delete: a single baby delete keyed by `entryId`.
    static func makeRequests( // swiftlint:disable:this function_parameter_count
        babyId: String,
        entryId: String,
        operationType: String,
        entryTimestamp: String,
        weightDecigrams: Int,
        lengthMillimeters: Int,
        source: String?,
        note: String?
    ) -> [UnifiedEntryRequest] {
        let category = EntryCategory.baby.rawValue

        guard operationType != OperationType.delete.rawValue else {
            return [
                UnifiedEntryRequest(
                    category: category,
                    operationType: operationType,
                    entryTimestamp: entryTimestamp,
                    babyId: babyId,
                    entryId: entryId,
                    entryType: BabyEntryType.weight.rawValue
                )
            ]
        }

        var requests: [UnifiedEntryRequest] = []
        if weightDecigrams > 0 {
            requests.append(
                UnifiedEntryRequest(
                    category: category,
                    operationType: operationType,
                    entryTimestamp: entryTimestamp,
                    source: source,
                    babyId: babyId,
                    entryId: entryId,
                    entryType: BabyEntryType.weight.rawValue,
                    babyWeightDecigrams: weightDecigrams,
                    entryNote: note
                )
            )
        }
        if lengthMillimeters > 0 {
            requests.append(
                UnifiedEntryRequest(
                    category: category,
                    operationType: operationType,
                    entryTimestamp: entryTimestamp,
                    source: source,
                    babyId: babyId,
                    entryId: entryId + lengthEntryIdSuffix,
                    entryType: BabyEntryType.measureLength.rawValue,
                    babyLengthMillimeters: lengthMillimeters,
                    entryNote: note
                )
            )
        }
        return requests
    }

    /// Convenience builder from a stored operation DTO + the owning entry's id.
    /// Returns `[]` for non-baby DTOs or when the baby id is missing.
    static func makeRequests(from dto: BathScaleOperationDTO, entryId: String, note: String?) -> [UnifiedEntryRequest] {
        guard dto.entryType == EntryType.baby.rawValue, let babyId = dto.babyId, !babyId.isEmpty else {
            return []
        }
        return makeRequests(
            babyId: babyId,
            entryId: entryId,
            operationType: dto.operationType ?? OperationType.create.rawValue,
            entryTimestamp: dto.entryTimestamp ?? "",
            weightDecigrams: dto.babyWeight.map { Int($0) } ?? 0,
            lengthMillimeters: dto.babyLength.map { Int($0) } ?? 0,
            source: dto.source,
            note: note
        )
    }
}
