//
//  BabyProfile.swift
//  meApp
//

import Foundation

/// Read-only domain view of a baby, used in ProductSelection and the UI.
/// Derived from the `Baby` SwiftData model — not stored separately.
struct BabyProfile: Identifiable, Equatable, Hashable {
    static let pendingSelectionId = "pending-baby-profile"

    let id: String
    let name: String
    let deviceId: String?
    let birthday: Date?
    let biologicalSex: String?
    let birthLengthInches: Double?
    let birthWeightLbs: Double?
    let birthWeightOz: Double?

    init(id: String,
         name: String,
         deviceId: String? = nil,
         birthday: Date? = nil,
         biologicalSex: String? = nil,
         birthLengthInches: Double? = nil,
         birthWeightLbs: Double? = nil,
         birthWeightOz: Double? = nil) {
        self.id = id
        self.name = name
        self.deviceId = deviceId
        self.birthday = birthday
        self.biologicalSex = biologicalSex
        self.birthLengthInches = birthLengthInches
        self.birthWeightLbs = birthWeightLbs
        self.birthWeightOz = birthWeightOz
    }

    var isPendingSelection: Bool {
        id == Self.pendingSelectionId
    }

    /// Name to show in headers/selectors, falling back to the generic "Baby Scale"
    /// label when a profile has no name yet (MOB-1164).
    var displayName: String {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? ProductTypeStrings.babyScale : trimmed
    }
}
