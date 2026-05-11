//
//  BathScaleOperationRequest.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//
import Foundation

struct BathScaleOperationRequest: Codable {
    let userId: String?
    let bmr: Double?
    let bmi: Double?
    let bodyFat: Double?
    let boneMass: Double?
    let entryTimestamp: String?
    let entryType: String?
    let metabolicAge: Double?
    let muscleMass: Double?
    let operationType: String?
    let proteinPercent: Double?
    let pulse: Double?
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
}
