import Foundation
@testable import meApp
import SwiftData

@MainActor
enum DashboardManagerTestSupport {
    // Test SUT alias; labeled tuple is clearer than a one-off struct.
    // swiftlint:disable:next large_tuple
    typealias StoreSUT = (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService
    )

    static func makeStore(
        cacheManager: DashboardCacheManagerProtocol,
        formatter: DashboardFormatterProtocol
    ) -> StoreSUT {
        let sut = makeStoreWithRepo(cacheManager: cacheManager, formatter: formatter)
        return (sut.store, sut.accountService, sut.entryService)
    }

    typealias StoreSUTWithRepo = (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService,
        accountLocalRepo: MockAccountRepository
    )

    /// Same as `makeStore` but also surfaces the account's local repo so tests that
    /// exercise the real save path can seed the active account into storage.
    static func makeStoreWithRepo(
        cacheManager: DashboardCacheManagerProtocol,
        formatter: DashboardFormatterProtocol
    ) -> StoreSUTWithRepo {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let store = DashboardStore(lightweight: true, formatter: formatter, cacheManager: cacheManager)

        // Pin the concrete dashboard dependency graph on the store and the
        // managers that expose injectable concrete services so full-suite DI
        // churn cannot change behavior after lazy resolution.
        store.accountService = deps.account
        store.logger = deps.logger
        store.chartManager.accountService = deps.account
        store.chartManager.logger = deps.logger
        store.displayManager.accountService = deps.account
        store.displayManager.logger = deps.logger
        store.lifecycleManager.accountService = deps.account
        store.lifecycleManager.logger = deps.logger

        return (store, deps.account, deps.entry, deps.accountLocalRepo)
    }

    static func syncStoreGraphState(_ store: DashboardStore) {
        store.state.graph = store.graphManager.state
    }

    static func loadData(
        into store: DashboardStore,
        entryService: EntryService,
        daily: [BathScaleWeightSummary] = [],
        monthly: [BathScaleWeightSummary] = []
    ) async {
        entryService.dailySummaries = daily
        entryService.monthlySummaries = monthly

        func matches(_ lhs: [BathScaleWeightSummary], _ rhs: [BathScaleWeightSummary]) -> Bool {
            guard lhs.count == rhs.count else { return false }
            return zip(lhs, rhs).allSatisfy { left, right in
                left.period == right.period &&
                left.entryTimestamp == right.entryTimestamp &&
                left.weight == right.weight
            }
        }

        await DashboardTestFixtures.waitUntil {
            matches(store.state.data.dailySummaries.compactMap { $0 }, daily) &&
            matches(store.state.data.monthlySummaries.compactMap { $0 }, monthly)
        }
    }

    static func makeEntryContext() -> ModelContext {
        let config = ModelConfiguration(isStoredInMemoryOnly: true)
        do {
            let container = try ModelContainer(
                for: Entry.self,
                BathScaleEntry.self,
                BathScaleMetric.self,
                configurations: config
            )
            return ModelContext(container)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }
}
