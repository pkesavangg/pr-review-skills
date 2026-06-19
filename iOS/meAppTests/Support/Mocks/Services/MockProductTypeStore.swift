//
//  MockProductTypeStore.swift
//  meAppTests
//

import Combine
import Foundation
@testable import meApp

@MainActor
final class MockProductTypeStore: ProductTypeStoreProtocol {
    @Published var selectedItem: ProductSelection = .myWeight
    @Published var availableItems: [ProductSelection] = [.myWeight]

    var selectedItemPublisher: Published<ProductSelection>.Publisher { $selectedItem }
    var availableItemsPublisher: Published<[ProductSelection]>.Publisher { $availableItems }

    var hasPersistedSelectionResult: Bool = false
    var hasPersistedSelection: Bool { hasPersistedSelectionResult }

    private(set) var selectCalls = 0
    private(set) var autoSelectBabyCalls = 0
    private(set) var selectLastAddedCalls = 0
    private(set) var resetToDefaultCalls = 0

    func select(_ item: ProductSelection) {
        selectCalls += 1
        selectedItem = item
    }

    func autoSelectBaby(babyId: String) {
        autoSelectBabyCalls += 1
    }

    func selectLastAdded(_ item: ProductSelection) {
        selectLastAddedCalls += 1
        selectedItem = item
    }

    func resetToDefault() {
        resetToDefaultCalls += 1
        selectedItem = .myWeight
    }

    private(set) var clearPersistedSelectionCalls = 0

    func clearPersistedSelection() {
        clearPersistedSelectionCalls += 1
    }
}
