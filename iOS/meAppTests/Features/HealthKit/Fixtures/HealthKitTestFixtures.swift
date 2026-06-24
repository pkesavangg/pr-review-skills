import Foundation
@testable import meApp

enum HealthKitTestFixtures {
    static func makeIntegrationInfo(
        type: IntegrationType = .healthKit,
        isIntegrated: Bool = true,
        assignedTo: String? = "account-101"
    ) -> IntegrationInfo {
        IntegrationInfo(
            type: type,
            isIntegrated: isIntegrated,
            assignedTo: assignedTo,
            deIntegrated: nil
        )
    }

    /// Builds an EntryNotification from a DTO for testing syncNewData/deleteEntry.
    static func makeEntryNotification(
        accountId: String = "account-101",
        entryTimestamp: String = "2026-01-15T12:00:00.000Z",
        weight: Int? = 70000,
        bodyFat: Int? = 20,
        muscleMass: Int? = 35000,
        bmi: Int? = 2200,
        pulse: Int? = 72
    ) -> EntryNotification {
        let dto = BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: bmi.map(Double.init),
            bodyFat: bodyFat.map(Double.init),
            boneMass: nil,
            entryTimestamp: entryTimestamp,
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: muscleMass.map(Double.init),
            operationType: OperationType.create.rawValue,
            proteinPercent: nil,
            pulse: pulse.map(Double.init),
            serverTimestamp: nil,
            skeletalMusclePercent: nil,
            source: nil,
            subcutaneousFatPercent: nil,
            systolic: nil,
            diastolic: nil,
            meanArterial: nil,
            unit: nil,
            visceralFatLevel: nil,
            water: nil,
            weight: weight.map(Double.init)
        )
        return EntryNotification(from: dto)
    }

    static func makeEntry(
        accountId: String = "account-101",
        timestamp: String = "2026-01-15T12:00:00.000Z",
        weight: Int? = 70000,
        bodyFat: Int? = 20,
        muscleMass: Int? = 35000,
        bmi: Int? = 2200,
        pulse: Int? = 72
    ) -> Entry {
        let entry = Entry(
            entryTimestamp: timestamp,
            accountId: accountId,
            operationType: OperationType.create.rawValue
        )
        entry.scaleEntry = BathScaleEntry(
            weight: weight,
            bodyFat: bodyFat,
            muscleMass: muscleMass,
            bmi: bmi
        )
        entry.scaleEntryMetric = BathScaleMetric(pulse: pulse)
        return entry
    }
}
