//
//  ProductTypeStoreHasPersistedSelectionTests.swift
//  meAppTests
//
//  Tests for ProductTypeStore.hasPersistedSelection and
//  MockProductTypeStore.hasPersistedSelection protocol conformance.
//

import Foundation
import Testing
@testable import meApp

// MARK: - MockProductTypeStore.hasPersistedSelection

/// Verifies that MockProductTypeStore correctly exposes the hasPersistedSelection
/// protocol requirement and that callers depending on it behave as expected.
@Suite("MockProductTypeStore hasPersistedSelection")
@MainActor
struct MockProductTypeStoreHasPersistedSelectionTests {

    @Test("defaults to false when no result is configured")
    func defaultsToFalse() {
        let mock = MockProductTypeStore()
        #expect(mock.hasPersistedSelection == false)
    }

    @Test("returns true when hasPersistedSelectionResult is set to true")
    func returnsTrueWhenConfigured() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = true
        #expect(mock.hasPersistedSelection == true)
    }

    @Test("returns false after toggling back to false")
    func returnsFalseAfterToggle() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = true
        mock.hasPersistedSelectionResult = false
        #expect(mock.hasPersistedSelection == false)
    }

    @Test("protocol-typed reference also reflects the result")
    func protocolTypedReferenceReflectsResult() {
        let mock = MockProductTypeStore()
        let store: ProductTypeStoreProtocol = mock

        mock.hasPersistedSelectionResult = true
        #expect(store.hasPersistedSelection == true)

        mock.hasPersistedSelectionResult = false
        #expect(store.hasPersistedSelection == false)
    }
}

// MARK: - applyInitialProductRedirectIfNeeded logic

/// These tests exercise the redirect decision rule:
///   isInProductDashboard = store.productTypeSelectorStore.hasPersistedSelection
/// by simulating the logic directly (the DashboardScreen is a SwiftUI View and
/// cannot be unit-instantiated; we verify the behaviour through the mock store).
@Suite("applyInitialProductRedirectIfNeeded redirect logic")
@MainActor
struct ApplyInitialProductRedirectLogicTests {

    // Mirrors the decision made inside applyInitialProductRedirectIfNeeded and the
    // onChange(of: canShowSnapshotOverview) handler in DashboardScreen.
    private func resolveIsInProductDashboard(
        hasInitializedProductRedirect: Bool,
        canShowSnapshotOverview: Bool,
        productTypeStore: ProductTypeStoreProtocol
    ) -> Bool? {
        // Guard mirrors the guard in applyInitialProductRedirectIfNeeded
        guard !hasInitializedProductRedirect, canShowSnapshotOverview else { return nil }
        return productTypeStore.hasPersistedSelection
    }

    @Test("redirect is skipped when already initialized")
    func redirectSkippedWhenAlreadyInitialized() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = true

        let result = resolveIsInProductDashboard(
            hasInitializedProductRedirect: true,
            canShowSnapshotOverview: true,
            productTypeStore: mock
        )

        // nil means the guard returned early — no redirect applied
        #expect(result == nil)
    }

    @Test("redirect is skipped when canShowSnapshotOverview is false")
    func redirectSkippedWhenOverviewUnavailable() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = true

        let result = resolveIsInProductDashboard(
            hasInitializedProductRedirect: false,
            canShowSnapshotOverview: false,
            productTypeStore: mock
        )

        #expect(result == nil)
    }

    @Test("returning user with persisted selection is redirected to product dashboard")
    func returningUserRedirectedToProductDashboard() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = true

        let result = resolveIsInProductDashboard(
            hasInitializedProductRedirect: false,
            canShowSnapshotOverview: true,
            productTypeStore: mock
        )

        #expect(result == true)
    }

    @Test("new user with no persisted selection lands on snapshot overview")
    func newUserLandsOnSnapshotOverview() {
        let mock = MockProductTypeStore()
        mock.hasPersistedSelectionResult = false

        let result = resolveIsInProductDashboard(
            hasInitializedProductRedirect: false,
            canShowSnapshotOverview: true,
            productTypeStore: mock
        )

        #expect(result == false)
    }
}

// MARK: - KV storage key correctness

/// Validates that KvStorageKeys.selectedProductTypeKey produces account-scoped
/// keys so that two different accounts never share the same storage slot.
@Suite("KvStorageKeys.selectedProductTypeKey")
struct KvStorageKeysProductTypeKeyTests {

    @Test("key is scoped to the given accountId")
    func keyScopedToAccountId() {
        let key = KvStorageKeys.selectedProductTypeKey(for: "acct-42")
        #expect(key.hasPrefix("acct-42"))
    }

    @Test("keys for different accounts are distinct")
    func keysForDifferentAccountsAreDistinct() {
        let key1 = KvStorageKeys.selectedProductTypeKey(for: "acct-1")
        let key2 = KvStorageKeys.selectedProductTypeKey(for: "acct-2")
        #expect(key1 != key2)
    }

    @Test("same accountId always produces the same key")
    func sameAccountAlwaysProducesSameKey() {
        let key1 = KvStorageKeys.selectedProductTypeKey(for: "acct-99")
        let key2 = KvStorageKeys.selectedProductTypeKey(for: "acct-99")
        #expect(key1 == key2)
    }
}

// MARK: - hasPersistedSelection via MockKvStorageService

/// These tests exercise the hasPersistedSelection logic directly using
/// MockKvStorageService and MockAccountService, bypassing the singleton
/// ProductTypeStore (which has a private init).
@Suite("hasPersistedSelection via KV storage")
@MainActor
struct HasPersistedSelectionKvStorageTests {

    // Helper that replicates the production logic:
    //   guard let accountId = accountService.activeAccount?.accountId else { return false }
    //   let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
    //   return kvStorage.getValue(forKey: key) != nil
    private func hasPersistedSelection(
        kvStorage: MockKvStorageService,
        accountId: String?
    ) -> Bool {
        guard let accountId else { return false }
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        return kvStorage.getValue(forKey: key) != nil
    }

    @Test("returns false when account is not active (nil accountId)")
    func returnsFalseWhenNoActiveAccount() {
        let kv = MockKvStorageService()
        #expect(hasPersistedSelection(kvStorage: kv, accountId: nil) == false)
    }

    @Test("returns false when no selection is persisted for the account")
    func returnsFalseWhenNoPersistedSelection() {
        let kv = MockKvStorageService()
        #expect(hasPersistedSelection(kvStorage: kv, accountId: "acct-1") == false)
    }

    @Test("returns true when a selection key exists in KV storage for the active account")
    func returnsTrueWhenSelectionPersistedForAccount() {
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        kv.setValue("myWeight", forKey: key)

        #expect(hasPersistedSelection(kvStorage: kv, accountId: accountId) == true)
    }

    @Test("returns false when selection is persisted for a different account")
    func returnsFalseWhenSelectionPersistedForDifferentAccount() {
        let kv = MockKvStorageService()
        let otherKey = KvStorageKeys.selectedProductTypeKey(for: "acct-other")
        kv.setValue("myWeight", forKey: otherKey)

        // Active account is "acct-1", but only "acct-other" has a persisted key
        #expect(hasPersistedSelection(kvStorage: kv, accountId: "acct-1") == false)
    }

    @Test("returns false after the persisted selection is cleared")
    func returnsFalseAfterClearingSelection() {
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)
        kv.setValue("myBloodPressure", forKey: key)
        #expect(hasPersistedSelection(kvStorage: kv, accountId: accountId) == true)

        kv.clearValue(forKey: key)
        #expect(hasPersistedSelection(kvStorage: kv, accountId: accountId) == false)
    }

    @Test("returns true regardless of the persisted product type value")
    func returnsTrueForAnyPersistedValue() {
        let kv = MockKvStorageService()
        let accountId = "acct-1"
        let key = KvStorageKeys.selectedProductTypeKey(for: accountId)

        for productId in ["myWeight", "myBloodPressure", "baby_someId"] {
            kv.setValue(productId, forKey: key)
            #expect(hasPersistedSelection(kvStorage: kv, accountId: accountId) == true)
        }
    }
}
