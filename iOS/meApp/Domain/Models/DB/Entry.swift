/// Stores all user entry records with common properties for all device types.
///
/// | Column Name      | Type    | Description                                         |
/// |------------------|---------|-----------------------------------------------------|
/// | id               | int     | Unique entry ID (Primary Key)                       |
/// | accountId        | string  | Foreign key referencing account.accountId           |
/// | entryTimestamp   | string  | Timestamp when the entry was made                   |
/// | serverTimestamp  | string  | Server-generated timestamp of entry receipt         |
/// | opTimestamp      | string  | Operation timestamp                                |
/// | operationType    | string  | Type of operation (e.g., create, delete, note)      |
/// | entryType        | string  | Entry type: "scale", "bpm", or "baby"               |
/// | isSynced         | boolean | Whether entry is synced online                      |

import Foundation
import SwiftData

@Model
final class Entry {
    /// Unique entry ID (PK)
    @Attribute(.unique) var id: UUID
    /// Foreign key referencing account.accountId
    var accountId: String
    /// Timestamp when the entry was made
    var entryTimestamp: String
    /// Server-generated timestamp of entry receipt
    var serverTimestamp: String?
    /// Operation timestamp
    var opTimestamp: String?
    /// Type of operation (eg., 'create', 'delete', 'note')
    var operationType: String
    /// Entry type: "scale" (adult weight), "bpm" (blood pressure), or "baby" (baby scale).
    /// Optional at the schema level so SwiftData's lightweight migration can synthesize
    /// `nil` for rows written before this column existed; consumers that need a concrete
    /// value should coalesce to `EntryType.scale.rawValue` (the original implicit default).
    var entryType: String?
    /// Whether entry is synced online
    var isSynced: Bool
    /// User note for the entry
    var note: String?
    /// Number of attempts to sync the entry
    var attempts: Int
    /// Whether entry is failed to sync
    var isFailedToSync: Bool
    @Relationship var scaleEntry: BathScaleEntry?
    @Relationship var scaleEntryMetric: BathScaleMetric?
    @Relationship var bpmEntry: BPMEntry?
    @Relationship var babyEntry: BabyEntry?

    init(id: UUID = UUID(),
         entryTimestamp: String,
         accountId: String,
         operationType: String,
         opTimestamp: String? = nil,
         serverTimestamp: String? = nil,
         entryType: String = EntryType.scale.rawValue,
         isSynced: Bool = false) {
        self.id = id
        self.entryTimestamp = entryTimestamp
        self.accountId = accountId
        self.operationType = operationType
        self.opTimestamp = opTimestamp
        self.serverTimestamp = serverTimestamp
        self.entryType = entryType
        self.isSynced = isSynced
        self.attempts = 0
        self.isFailedToSync = false
    }

    init(from dto: BathScaleOperationDTO, accountId: String, isSynced: Bool = false) {
            let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
            self.id = UUID()
            self.entryTimestamp = timestamp
            self.accountId = accountId
            self.operationType = dto.operationType ?? ""
            self.serverTimestamp = dto.serverTimestamp
            self.attempts = 0
            self.isFailedToSync = false

            let resolvedEntryType = dto.entryType ?? EntryType.scale.rawValue
            self.entryType = resolvedEntryType
            self.isSynced = isSynced
            self.note = dto.note
            self.scaleEntry = BathScaleEntry(from: dto)
            self.scaleEntryMetric = BathScaleMetric(from: dto)

            switch resolvedEntryType {
            case EntryType.bpm.rawValue:
                self.bpmEntry = BPMEntry(
                    systolic: dto.systolic.map { Int($0) } ?? 0,
                    diastolic: dto.diastolic.map { Int($0) } ?? 0,
                    meanArterial: dto.meanArterial.map { String($0) } ?? "",
                    pulse: dto.pulse.map { Int($0) } ?? 0
                )
            case EntryType.baby.rawValue:
                self.babyEntry = BabyEntry(
                    babyId: dto.babyId ?? "",
                    length: dto.babyLength.map { Int($0) } ?? 0,
                    weight: dto.babyWeight.map { Int($0) } ?? 0,
                    source: dto.source
                )
            default:
                break
            }
    }

    init(from dto: BpmOperationDTO, accountId: String, isSynced: Bool = false) {
        let timestamp = dto.entryTimestamp ?? ISO8601DateFormatter().string(from: Date())
        self.id = UUID()
        self.entryTimestamp = timestamp
        self.accountId = accountId
        self.operationType = dto.operationType ?? ""
        self.opTimestamp = nil
        self.serverTimestamp = dto.serverTimestamp
        self.entryType = EntryType.bpm.rawValue
        self.isSynced = isSynced
        self.attempts = 0
        self.isFailedToSync = false
        self.note = dto.note
        self.scaleEntry = BathScaleEntry(from: dto)
        self.scaleEntryMetric = BathScaleMetric(from: dto)
        self.bpmEntry = BPMEntry(from: dto)
    }

    func toBpmOperationDTO() -> BpmOperationDTO {
        return BpmOperationDTO(
            accountId: self.accountId,
            systolic: self.bpmEntry.map { Double($0.systolic) },
            diastolic: self.bpmEntry.map { Double($0.diastolic) },
            pulse: self.bpmEntry.map { Double($0.pulse) },
            meanArterial: self.bpmEntry?.meanArterial,
            note: self.note,
            source: self.scaleEntry?.source,
            unit: self.scaleEntryMetric?.unit,
            entryTimestamp: self.entryTimestamp,
            operationType: self.operationType,
            serverTimestamp: self.serverTimestamp
        )
    }

    func toOperationDTO() -> BathScaleOperationDTO {
        let dtoSystolic: Double? = bpmEntry.map { Double($0.systolic) } ?? scaleEntry?.systolic.map { Double($0) }
        let dtoDiastolic: Double? = bpmEntry.map { Double($0.diastolic) } ?? scaleEntry?.diastolic.map { Double($0) }
        let dtoPulse: Double? = bpmEntry.map { Double($0.pulse) } ?? scaleEntryMetric?.pulse.map { Double($0) }
        let dtoMeanArterial: Double? = bpmEntry.flatMap { Double($0.meanArterial) } ?? scaleEntry?.meanArterial.flatMap { Double($0) }

        return BathScaleOperationDTO(
            accountId: self.accountId,
            bmr: self.scaleEntryMetric?.bmr.map { Double($0) },
            bmi: self.scaleEntry?.bmi.map { Double($0) },
            bodyFat: self.scaleEntry?.bodyFat.map { Double($0) },
            boneMass: self.scaleEntryMetric?.boneMass.map { Double($0) },
            entryTimestamp: self.entryTimestamp,
            entryType: self.entryType,
            impedance: self.scaleEntryMetric?.impedance.map { Double($0) },
            metabolicAge: self.scaleEntryMetric?.metabolicAge.map { Double($0) },
            muscleMass: self.scaleEntry?.muscleMass.map { Double($0) },
            operationType: self.operationType,
            proteinPercent: self.scaleEntryMetric?.proteinPercent.map { Double($0) },
            pulse: dtoPulse,
            serverTimestamp: self.serverTimestamp,
            skeletalMusclePercent: self.scaleEntryMetric?.skeletalMusclePercent.map { Double($0) },
            source: self.babyEntry?.source ?? self.scaleEntry?.source,
            subcutaneousFatPercent: self.scaleEntryMetric?.subcutaneousFatPercent.map { Double($0) },
            systolic: dtoSystolic,
            diastolic: dtoDiastolic,
            meanArterial: dtoMeanArterial,
            unit: self.scaleEntryMetric?.unit,
            visceralFatLevel: self.scaleEntryMetric?.visceralFatLevel.map { Double($0) },
            water: self.scaleEntry?.water.map { Double($0) },
            weight: self.scaleEntry?.weight.map { Double($0) },
            babyId: self.babyEntry?.babyId,
            babyWeight: self.babyEntry.map { Double($0.weight) },
            babyLength: self.babyEntry.map { Double($0.length) }
        )
    }

    /// Returns an array of (value, metric) pairs for all available body metrics in this entry.
    var metricItems: [(value: Int, metric: BodyMetric)] {
        var arr: [(Int, BodyMetric)] = []
        if let bmi = scaleEntry?.bmi {
            arr.append((bmi, .bmi))
        }
        if let bodyFat = scaleEntry?.bodyFat, bodyFat != 0 {
            arr.append((bodyFat, .bodyFat))
        }
        if let muscleMass = scaleEntry?.muscleMass, muscleMass != 0 {
            arr.append((muscleMass, .muscleMass))
        }
        if let water = scaleEntry?.water, water != 0 {
            arr.append((water, .water))
        }
        if let heartRate = scaleEntryMetric?.pulse, heartRate != 0 {
            arr.append((heartRate, .pulse))
        }
        if let boneMass = scaleEntryMetric?.boneMass, boneMass != 0 {
            arr.append((boneMass, .boneMass))
        }
        if let visceralFat = scaleEntryMetric?.visceralFatLevel, visceralFat != 0 {
            arr.append((visceralFat, .visceralFatLevel))
        }
        if let subcutaneousFat = scaleEntryMetric?.subcutaneousFatPercent, subcutaneousFat != 0 {
            arr.append((subcutaneousFat, .subcutaneousFatPercent))
        }
        if let proteinPercent = scaleEntryMetric?.proteinPercent, proteinPercent != 0 {
            arr.append((proteinPercent, .proteinPercent))
        }
        if let skeletalMuscles = scaleEntryMetric?.skeletalMusclePercent, skeletalMuscles != 0 {
            arr.append((skeletalMuscles, .skeletalMusclePercent))
        }
        if let bmr = scaleEntryMetric?.bmr, bmr != 0 {
            arr.append((bmr, .bmr))
        }
        if let metabolicAge = scaleEntryMetric?.metabolicAge, metabolicAge != 0 {
            arr.append((metabolicAge, .metabolicAge))
        }
        return arr
    }
}

// NOTE: SwiftData models are NOT thread-safe. Do not mark as Sendable.
// Use PersistentIdentifier to pass references between contexts, and use
// SwiftDataWorker or MainActor.run to safely access model properties.
