//
//  BabyListItem.swift
//  meApp
//

import Foundation

/// Lightweight data struct for displaying a baby in a list.
/// Used by both the signup flow and the baby scale setup flow.
struct BabyListItem: Identifiable {
    let id: UUID
    let accountID: String
    let name: String
}
