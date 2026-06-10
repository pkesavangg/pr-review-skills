import Foundation

struct BathScaleOperationDTO: Codable, Sendable, Equatable {
    let accountId: String?
    let bmr: Double?
    let bmi: Double?
    let bodyFat: Double?
    let boneMass: Double?
    let entryTimestamp: String?
    let entryType: String?
    let impedance: Double?
    let metabolicAge: Double?
    let muscleMass: Double?
    let operationType: String?
    let proteinPercent: Double?
    let pulse: Double?
    let serverTimestamp: String?
    let skeletalMusclePercent: Double?
    let source: String?
    let subcutaneousFatPercent: Double?
    let systolic: Double?
    let diastolic: Double?
    let meanArterial: Double?
    let unit: String?
    let visceralFatLevel: Double?
    let water: Double?
    let weight: Double?
    // Baby scale fields
    var babyId: String? = nil
    var babyWeight: Double? = nil
    var babyLength: Double? = nil
    // BP free-text note. Carried so the unified read path (MOB-385) can round-trip the
    // BP `note` field through the sync/merge engine into the local entry.
    var note: String? = nil
}

// Extend BathScaleOperationDTO to conform to Identifiable and provide a computed date property
// This extension allows BathScaleOperationDTO to be used easily in SwiftUI's ForEach and Charts,
// enabling the struct to be utilized directly in the graph view.
extension BathScaleOperationDTO: Identifiable {
    var id: String { entryTimestamp ?? UUID().uuidString }
    var date: Date? {
        guard let entryTimestamp = entryTimestamp else { return nil }
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: entryTimestamp)
    }
    
    func copy(with newTimestamp: String) -> BathScaleOperationDTO {
        .init(
            accountId: accountId,
            bmr: bmr,
            bmi: bmi,
            bodyFat: bodyFat,
            boneMass: boneMass,
            entryTimestamp: newTimestamp,
            entryType: entryType,
            impedance: impedance,
            metabolicAge: metabolicAge,
            muscleMass: muscleMass,
            operationType: operationType,
            proteinPercent: proteinPercent,
            pulse: pulse,
            serverTimestamp: serverTimestamp,
            skeletalMusclePercent: skeletalMusclePercent,
            source: source,
            subcutaneousFatPercent: subcutaneousFatPercent,
            systolic: systolic,
            diastolic: diastolic,
            meanArterial: meanArterial,
            unit: unit,
            visceralFatLevel: visceralFatLevel,
            water: water,
            weight: weight,
            babyId: babyId,
            babyWeight: babyWeight,
            babyLength: babyLength,
            note: note
        )
    }
}

extension BathScaleOperationDTO {
    /// Initialize from BathScaleWeightSummary
    init(from summary: BathScaleWeightSummary) {
        self.accountId = summary.accountId
        self.bmr = summary.bmr
        self.bmi = summary.bmi
        self.bodyFat = summary.bodyFat
        self.boneMass = summary.boneMass
        self.entryTimestamp = summary.entryTimestamp
        self.entryType = summary.entryType
        self.impedance = summary.impedance
        self.metabolicAge = summary.metabolicAge
        self.muscleMass = summary.muscleMass
        self.operationType = nil
        self.proteinPercent = summary.proteinPercent
        self.pulse = summary.pulse
        self.serverTimestamp = nil
        self.skeletalMusclePercent = summary.skeletalMusclePercent
        self.source = nil
        self.subcutaneousFatPercent = summary.subcutaneousFatPercent
        self.systolic = summary.systolic
        self.diastolic = summary.diastolic
        self.meanArterial = summary.meanArterial
        self.unit = nil
        self.visceralFatLevel = summary.visceralFatLevel
        self.water = summary.water
        self.weight = summary.weight
        self.babyId = nil
        self.babyWeight = nil
        self.babyLength = nil
        self.note = nil
    }
}
