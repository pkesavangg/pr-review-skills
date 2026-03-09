import Foundation
import SwiftData

final class BathScaleWeightSummary: Identifiable, Equatable {
    var id: UUID
    var accountId: String
    var period: String       // "YYYY-MM-DD"
    var entryTimestamp: String // ISO8601 of start of day
    var date: Date           // normalized start of day

    var count: Int
    var weight: Double

    var bodyFat: Double?
    var muscleMass: Double?
    var water: Double?
    var bmi: Double?
    var bmr: Double?
    var metabolicAge: Double?
    var proteinPercent: Double?
    var pulse: Double?
    var skeletalMusclePercent: Double?
    var subcutaneousFatPercent: Double?
    var visceralFatLevel: Double?
    var boneMass: Double?
    var impedance: Double?

    init(
        id: UUID = UUID(),
        accountId: String,
        period: String,
        entryTimestamp: String,
        date: Date,
        count: Int,
        weight: Double,
        bodyFat: Double? = nil,
        muscleMass: Double? = nil,
        water: Double? = nil,
        bmi: Double? = nil,
        bmr: Double? = nil,
        metabolicAge: Double? = nil,
        proteinPercent: Double? = nil,
        pulse: Double? = nil,
        skeletalMusclePercent: Double? = nil,
        subcutaneousFatPercent: Double? = nil,
        visceralFatLevel: Double? = nil,
        boneMass: Double? = nil,
        impedance: Double? = nil
    ) {
        self.id = id
        self.accountId = accountId
        self.period = period
        self.entryTimestamp = entryTimestamp
        self.date = date
        self.count = count
        self.weight = weight
        self.bodyFat = bodyFat
        self.muscleMass = muscleMass
        self.water = water
        self.bmi = bmi
        self.bmr = bmr
        self.metabolicAge = metabolicAge
        self.proteinPercent = proteinPercent
        self.pulse = pulse
        self.skeletalMusclePercent = skeletalMusclePercent
        self.subcutaneousFatPercent = subcutaneousFatPercent
        self.visceralFatLevel = visceralFatLevel
        self.boneMass = boneMass
        self.impedance = impedance
    }
    
    // MARK: - Equatable
    static func == (lhs: BathScaleWeightSummary, rhs: BathScaleWeightSummary) -> Bool {
        return lhs.id == rhs.id &&
               lhs.period == rhs.period &&
               lhs.weight == rhs.weight &&
               lhs.date == rhs.date &&
               lhs.count == rhs.count &&
               lhs.accountId == rhs.accountId &&
               lhs.entryTimestamp == rhs.entryTimestamp &&
               lhs.bodyFat == rhs.bodyFat &&
               lhs.muscleMass == rhs.muscleMass &&
               lhs.water == rhs.water &&
               lhs.bmi == rhs.bmi &&
               lhs.bmr == rhs.bmr &&
               lhs.metabolicAge == rhs.metabolicAge &&
               lhs.proteinPercent == rhs.proteinPercent &&
               lhs.pulse == rhs.pulse &&
               lhs.skeletalMusclePercent == rhs.skeletalMusclePercent &&
               lhs.subcutaneousFatPercent == rhs.subcutaneousFatPercent &&
               lhs.visceralFatLevel == rhs.visceralFatLevel &&
               lhs.boneMass == rhs.boneMass &&
               lhs.impedance == rhs.impedance
    }
}
