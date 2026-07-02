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

    // MARK: - Baby fields (MOB-386)
    let babyId: String?
    /// Baby entry sub-type (`weight`, `measureLength`).
    let entryType: String?
    let babyWeightDecigrams: Int?
    let babyLengthMillimeters: Int?
    let entryNote: String?

    /// Explicit member-wise initializer. Baby fields default to `nil` so the many existing
    /// weight/BP call sites (and decoders) need not supply them.
    init(
        category: String?,
        entryId: String?,
        operationType: String?,
        entryTimestamp: String?,
        serverTimestamp: String?,
        source: String?,
        weight: Int?,
        bodyFat: Int?,
        muscleMass: Int?,
        water: Int?,
        bmi: Int?,
        boneMass: Int?,
        impedance: Int?,
        unit: String?,
        systolic: Int?,
        diastolic: Int?,
        pulse: Int?,
        note: String?,
        babyId: String? = nil,
        entryType: String? = nil,
        babyWeightDecigrams: Int? = nil,
        babyLengthMillimeters: Int? = nil,
        entryNote: String? = nil
    ) {
        self.category = category
        self.entryId = entryId
        self.operationType = operationType
        self.entryTimestamp = entryTimestamp
        self.serverTimestamp = serverTimestamp
        self.source = source
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.water = water
        self.bmi = bmi
        self.boneMass = boneMass
        self.impedance = impedance
        self.unit = unit
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = pulse
        self.note = note
        self.babyId = babyId
        self.entryType = entryType
        self.babyWeightDecigrams = babyWeightDecigrams
        self.babyLengthMillimeters = babyLengthMillimeters
        self.entryNote = entryNote
    }

    private enum CodingKeys: String, CodingKey {
        case category, entryId, operationType, entryTimestamp, serverTimestamp, source
        case weight, bodyFat, muscleMass, water, bmi, boneMass, impedance, unit
        case systolic, diastolic, pulse, note
        case babyId, entryType, babyWeightDecigrams, babyLengthMillimeters, entryNote
    }

    /// Custom decoder to tolerate the server sending `entryId` as a JSON **number**
    /// (the read side of `GET /v3/entries/` returns it numeric, while the write side is a string).
    /// A numeric id is stringified; every other field decodes as normal. Without this, the
    /// synthesized decoder throws `typeMismatch` on `entryId` and the whole entries response
    /// fails to decode — which silently empties History.
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)

        if let numericId = try? container.decode(Int.self, forKey: .entryId) {
            self.entryId = String(numericId)
        } else {
            self.entryId = try container.decodeIfPresent(String.self, forKey: .entryId)
        }

        self.category = try container.decodeIfPresent(String.self, forKey: .category)
        self.operationType = try container.decodeIfPresent(String.self, forKey: .operationType)
        self.entryTimestamp = try container.decodeIfPresent(String.self, forKey: .entryTimestamp)
        self.serverTimestamp = try container.decodeIfPresent(String.self, forKey: .serverTimestamp)
        self.source = try container.decodeIfPresent(String.self, forKey: .source)

        self.weight = try container.decodeIfPresent(Int.self, forKey: .weight)
        self.bodyFat = try container.decodeIfPresent(Int.self, forKey: .bodyFat)
        self.muscleMass = try container.decodeIfPresent(Int.self, forKey: .muscleMass)
        self.water = try container.decodeIfPresent(Int.self, forKey: .water)
        self.bmi = try container.decodeIfPresent(Int.self, forKey: .bmi)
        self.boneMass = try container.decodeIfPresent(Int.self, forKey: .boneMass)
        self.impedance = try container.decodeIfPresent(Int.self, forKey: .impedance)
        self.unit = try container.decodeIfPresent(String.self, forKey: .unit)

        self.systolic = try container.decodeIfPresent(Int.self, forKey: .systolic)
        self.diastolic = try container.decodeIfPresent(Int.self, forKey: .diastolic)
        self.pulse = try container.decodeIfPresent(Int.self, forKey: .pulse)
        self.note = try container.decodeIfPresent(String.self, forKey: .note)

        self.babyId = try container.decodeIfPresent(String.self, forKey: .babyId)
        self.entryType = try container.decodeIfPresent(String.self, forKey: .entryType)
        self.babyWeightDecigrams = try container.decodeIfPresent(Int.self, forKey: .babyWeightDecigrams)
        self.babyLengthMillimeters = try container.decodeIfPresent(Int.self, forKey: .babyLengthMillimeters)
        self.entryNote = try container.decodeIfPresent(String.self, forKey: .entryNote)
    }
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
            // Carry the server-assigned entryId so the merge engine can key on entry
            // identity instead of entryTimestamp (distinct entries can share a timestamp).
            serverEntryId: entryId,
            // Baby read entries arrive per sub-type; carry whichever measurement is present.
            babyId: babyId,
            babyWeight: babyWeightDecigrams.map(Double.init),
            babyLength: babyLengthMillimeters.map(Double.init),
            note: entryNote ?? note
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
            note: dto.note,
            babyId: dto.babyId,
            babyWeightDecigrams: dto.babyWeight.map { Int($0) },
            babyLengthMillimeters: dto.babyLength.map { Int($0) }
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
