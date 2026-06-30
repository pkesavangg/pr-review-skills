import Foundation
@testable import meApp

enum ContentViewModelTestFixtures {
    static func makeActiveAccount(id: String = "content-account", lastActiveTime: String? = nil) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: id,
            email: "content@example.com",
            isLoggedIn: true,
            isActiveAccount: true,
            lastActiveTime: lastActiveTime
        )
    }

    static func makeEntries(accountId: String = "content-account", count: Int = 2) -> [Entry] {
        (0..<count).map { index in
            Entry(
                entryTimestamp: "2026-03-03T10:0\(index):00.000Z",
                accountId: accountId,
                operationType: OperationType.create.rawValue
            )
        }
    }

    /// Snapshot equivalents of `makeEntries`. `ContentViewModel` loads entries via
    /// `fetchAllEntrySnapshots()` (snapshot boundary rule), so tests should seed snapshots.
    static func makeEntrySnapshots(accountId: String = "content-account", count: Int = 2) -> [EntrySnapshot] {
        (0..<count).map { index in
            EntryTestFixtures.makeEntrySnapshot(
                accountId: accountId,
                entryTimestamp: "2026-03-03T10:0\(index):00.000Z"
            )
        }
    }

    static func makeEntryNotification(accountId: String = "content-account") -> EntryNotification {
        let dto = BathScaleOperationDTO(
            accountId: accountId,
            bmr: nil,
            bmi: nil,
            bodyFat: nil,
            boneMass: nil,
            entryTimestamp: "2026-03-03T10:00:00.000Z",
            entryType: nil,
            impedance: nil,
            metabolicAge: nil,
            muscleMass: nil,
            operationType: OperationType.create.rawValue,
            proteinPercent: nil,
            pulse: nil,
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
            weight: 175
        )
        return EntryNotification(from: dto)
    }

    static func makeAccountFlag(trigger: String = "login") -> AccountFlag {
        AccountFlag(
            id: "flag-1",
            type: "app-rate-ask",
            trigger: trigger,
            metadata: nil,
            createdAt: "2026-03-03T10:00:00.000Z",
            accountId: "content-account"
        )
    }
}
