//
//  BabySnapshot.swift
//  meApp
//
//  Flat, `Sendable` value snapshot of the SwiftData `Baby` model (MOB-386).
//

import Foundation

/// Immutable, `Sendable` mirror of the `Baby` `@Model`, safe to cross actor boundaries and
/// carry on Combine publishers. Follows the project's snapshot-boundary rule — the `@Model`
/// stays inside the owning service.
struct BabySnapshot: Equatable, Sendable, Identifiable {
    let id: String
    let accountId: String
    let name: String
    let deviceId: String?
    let isSynced: Bool
    let birthday: Date?
    let biologicalSex: String?
    let birthLengthInches: Double?
    let birthWeightLbs: Double?
    let birthWeightOz: Double?
}
