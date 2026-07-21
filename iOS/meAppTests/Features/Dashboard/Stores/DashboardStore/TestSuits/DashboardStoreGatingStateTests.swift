import Foundation
@testable import meApp
import Testing

/// MOB-1726 review coverage: the two `DashboardStore` gating flags the baby-graph loading fixes hinge on.
/// - `hasResolvedInitialProduct` holds a neutral skeleton until the async persisted product selection lands,
///   so the default weight scaffold never flashes before baby/BPM on launch.
/// - `hasCompletedInitialSync` scopes the MOB-516 first-login skeleton guard to the initial sync, so a later
///   per-switch sync of a genuinely-empty product doesn't re-show the skeleton.
///
/// The `DashboardStore` resolves the `MockProductTypeStore` via the `ProductTypeStoreProtocol` key
/// (see `TestDependencyContainer.reset`), so `persistedSelectionIdResult` / `selectedItem` drive the gate.
extension DashboardStoreTests {
    @Suite("Initial-product & sync gating (MOB-1726)")
    @MainActor
    struct GatingState {

        private func mockProductStore() -> MockProductTypeStore {
            // Force-cast is intentional: `reset()` always registers a `MockProductTypeStore` under the protocol.
            // swiftlint:disable:next force_cast
            DependencyContainer.shared.resolve(ProductTypeStoreProtocol.self) as! MockProductTypeStore
        }

        private func makeStore() -> DashboardStore {
            DashboardStore(formatter: MockDashboardFormatter(), cacheManager: MockDashboardCacheManager())
        }

        // MARK: - hasResolvedInitialProduct

        @Test("resolved immediately at init when nothing is persisted")
        func resolvedAtInitWhenNothingPersisted() {
            TestDependencyContainer.reset()
            mockProductStore().persistedSelectionIdResult = nil

            let store = makeStore()

            #expect(store.hasResolvedInitialProduct == true)
        }

        @Test("pending at init when a persisted product hasn't been restored yet")
        func pendingAtInitWhenPersistedSelectionNotYetApplied() {
            TestDependencyContainer.reset()
            let mock = mockProductStore()
            mock.persistedSelectionIdResult = "baby_xyz" // persisted product is a baby
            mock.selectedItem = .myWeight                 // …but the selection is still the default (restore pending)

            let store = makeStore()

            // Checked synchronously — before the bounded 1.5s fallback can flip it.
            #expect(store.hasResolvedInitialProduct == false)
        }

        @Test("resolves once the persisted product selection lands")
        func resolvesWhenPersistedSelectionArrives() async {
            TestDependencyContainer.reset()
            let mock = mockProductStore()
            mock.persistedSelectionIdResult = "baby_xyz"
            mock.selectedItem = .myWeight

            let store = makeStore()
            #expect(store.hasResolvedInitialProduct == false)

            // The async restore delivers the persisted baby (ProductSelection id == "baby_xyz").
            mock.selectedItem = .baby(profile: BabyProfile(id: "xyz", name: "Ava"))

            await DashboardTestFixtures.waitUntil { store.hasResolvedInitialProduct }
            #expect(store.hasResolvedInitialProduct == true)
        }

        @Test("an explicit product pick resolves the gate immediately")
        func explicitSelectionResolvesGate() {
            TestDependencyContainer.reset()
            let mock = mockProductStore()
            mock.persistedSelectionIdResult = "baby_xyz"
            mock.selectedItem = .myWeight

            let store = makeStore()
            #expect(store.hasResolvedInitialProduct == false)

            store.selectProductItem(.myWeight)

            #expect(store.hasResolvedInitialProduct == true) // set synchronously by selectProductItem
        }

        // MARK: - hasCompletedInitialSync

        @Test("false until the first sync completes, true after isSyncing goes true→false")
        func completesOnFirstSyncTrueToFalse() async {
            let store = DashboardStoreTestSupport.makeSUT(lightweight: false).store
            // Synchronous check — the deferred first sync is 1.5s out, so it's still false here.
            #expect(store.hasCompletedInitialSync == false)

            let entryService = DependencyContainer.shared.resolve(EntryService.self)
            #expect(entryService != nil)

            entryService?.isSyncing = true
            await DashboardTestFixtures.waitUntil { store.isSyncing }
            entryService?.isSyncing = false

            await DashboardTestFixtures.waitUntil { store.hasCompletedInitialSync }
            #expect(store.hasCompletedInitialSync == true)
        }
    }
}
