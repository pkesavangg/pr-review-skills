import Foundation

struct BathScaleOperationDTO: Codable {
    let accountId: String?
    let bmr: Double?
    let bmi: Double?
    let bodyFat: Double?
    let boneMass: Double?
    let entryTimestamp: String?
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
    let unit: String?
    let visceralFatLevel: Double?
    let water: Double?
    let weight: Double?
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
            unit: unit,
            visceralFatLevel: visceralFatLevel,
            water: water,
            weight: weight
        )
    }    
}
