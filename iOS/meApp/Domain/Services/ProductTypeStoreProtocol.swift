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
}
