//
//  BabyProfile.swift
//  meApp
//

import Foundation

/// Read-only domain view of a baby, used in ProductSelection and the UI.
/// Derived from the `Baby` SwiftData model — not stored separately.
///
/// NOTE: This is a preliminary/minimal struct. Properties will be refined
/// and expanded as baby scale requirements are finalized in future tasks.
struct BabyProfile: Identifiable, Equatable, Hashable {
    let id: String
    let name: String
    let deviceId: String?
}
