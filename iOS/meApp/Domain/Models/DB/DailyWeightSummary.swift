import Foundation
import SwiftData

final class BathScaleWeightSummary {
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
}


