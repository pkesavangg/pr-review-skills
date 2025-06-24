//  UserItemInfo.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 24/06/25.
//
import Foundation
import SwiftUI  

// MARK: - User Item Info
/// Immutable data model representing a single account row in the account-switching list.
/// `Identifiable` & `Hashable` so it can be used directly in `ForEach` & stored in collections.
struct UserItemInfo: Identifiable, Hashable {
    /// Unique identifier for diffing in lists.
    let id: UUID = .init()

    /// Display name of the account holder.
    let name: String

    /// E-mail address associated with the account.
    let email: String

    /// `true` when the row is currently selected.
    var isSelected: Bool = false

    /// `true` when the row should appear disabled & not react to taps.
    var isDisabled: Bool = false

    /// If `false`, the right-hand side selection indicator (circle / checkmark) is hidden.
    var canShowSelection: Bool = true
}
