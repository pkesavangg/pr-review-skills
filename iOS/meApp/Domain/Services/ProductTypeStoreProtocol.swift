//
//  ProductTypeStoreProtocol.swift
//  meApp
//

import Combine
import Foundation

/// Protocol for the global product type state manager.
/// Holds the currently selected product type / baby profile and the
/// ordered list of available items in the header dropdown.
@MainActor
protocol ProductTypeStoreProtocol: AnyObject {
    /// The item currently selected in the header dropdown.
    var selectedItem: ProductSelection { get }

    /// Publisher for observing selection changes.
    var selectedItemPublisher: Published<ProductSelection>.Publisher { get }

    /// Publisher for observing item list changes.
    var availableItemsPublisher: Published<[ProductSelection]>.Publisher { get }

    /// Ordered list of items for the dropdown.
    var availableItems: [ProductSelection] { get }

    /// Select a specific item in the dropdown.
    func select(_ item: ProductSelection)

    /// Auto-selects the baby matching the given Baby.id.
    func autoSelectBaby(babyId: String)

    /// Sets the active selection to the last added device / baby.
    func selectLastAdded(_ item: ProductSelection)

    /// Restore to the first available item (typically "My Weight").
    func resetToDefault()

    /// Returns true when the current account has a persisted product selection in local storage.
    /// Used to decide whether to redirect returning users straight to the product detail dashboard.
    var hasPersistedSelection: Bool { get }

    /// Removes the persisted product selection for the current account.
    /// Call this when the user explicitly navigates back to the snapshot overview so the
    /// next cold launch shows the snapshot instead of jumping straight to the product dashboard.
    func clearPersistedSelection()
}
