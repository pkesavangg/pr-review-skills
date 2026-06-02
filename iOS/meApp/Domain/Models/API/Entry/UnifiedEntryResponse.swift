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

// MARK: - Legacy DTO Bridging (MOB-385)

extension UnifiedEntryResult {
    /// Projects a flat unified entry onto the legacy `BathScaleOperationDTO`.
    ///
    /// The sync/merge engine and dashboard aggregation predate the unified flat shape and
    /// consume `BathScaleOperationDTO`. Fields absent from the unified read response (bmr,
    /// metabolicAge, the percentage body-comp metrics) map to `nil`; `category` becomes the
    /// local persistence `entryType`.
    func toOperationDTO() -> BathScaleOperationDTO {
        BathScaleOperationDTO(
            accountId: nil,
            bmr: nil,
            bmi: bmi.map(Double.init),
            bodyFat: bodyFat.map(Double.init),
            boneMass: boneMass.map(Double.init),
            entryTimestamp: entryTimestamp,
            entryType: Self.entryType(forCategory: category),
            impedance: impedance.map(Double.init),
            metabolicAge: nil,
            muscleMass: muscleMass.map(Double.init),
            operationType: operationType,
            proteinPercent: nil,
            pulse: pulse.map(Double.init),
            serverTimestamp: serverTimestamp,
            skeletalMusclePercent: nil,
            source: source,
            subcutaneousFatPercent: nil,
            systolic: systolic.map(Double.init),
            diastolic: diastolic.map(Double.init),
            meanArterial: nil,
            unit: unit,
            visceralFatLevel: nil,
            water: water.map(Double.init),
            weight: weight.map(Double.init),
            note: note
        )
    }

    /// Inverse projection used by tests and legacy callers that build a unified result from a DTO.
    init(from dto: BathScaleOperationDTO) {
        self.init(
            category: Self.category(forEntryType: dto.entryType),
            entryId: nil,
            operationType: dto.operationType,
            entryTimestamp: dto.entryTimestamp,
            serverTimestamp: dto.serverTimestamp,
            source: dto.source,
            weight: dto.weight.map { Int($0) },
            bodyFat: dto.bodyFat.map { Int($0) },
            muscleMass: dto.muscleMass.map { Int($0) },
            water: dto.water.map { Int($0) },
            bmi: dto.bmi.map { Int($0) },
            boneMass: dto.boneMass.map { Int($0) },
            impedance: dto.impedance.map { Int($0) },
            unit: dto.unit,
            systolic: dto.systolic.map { Int($0) },
            diastolic: dto.diastolic.map { Int($0) },
            pulse: dto.pulse.map { Int($0) },
            note: dto.note
        )
    }

    /// Maps the unified `category` discriminator onto the local persistence `entryType`.
    static func entryType(forCategory category: String?) -> String? {
        switch category {
        case EntryCategory.weight.rawValue: return EntryType.scale.rawValue
        case EntryCategory.bp.rawValue: return EntryType.bpm.rawValue
        case EntryCategory.baby.rawValue: return EntryType.baby.rawValue
        default: return nil
        }
    }

    /// Maps the local persistence `entryType` onto the unified `category` discriminator.
    /// Legacy weight entries (nil/empty `entryType`) resolve to `weight`.
    static func category(forEntryType entryType: String?) -> String? {
        switch entryType {
        case EntryType.bpm.rawValue: return EntryCategory.bp.rawValue
        case EntryType.baby.rawValue: return EntryCategory.baby.rawValue
        default: return EntryCategory.weight.rawValue
        }
    }
}
