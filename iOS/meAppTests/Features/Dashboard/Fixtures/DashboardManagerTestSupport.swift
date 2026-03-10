import Foundation
import SwiftData
@testable import meApp

@MainActor
enum DashboardManagerTestSupport {
    typealias StoreSUT = (
        store: DashboardStore,
        accountService: AccountService,
        entryService: EntryService
    )

    static func makeStore(
        cacheManager: DashboardCacheManagerProtocol,
        formatter: DashboardFormatterProtocol
    ) -> StoreSUT {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let store = DashboardStore(lightweight: true, formatter: formatter, cacheManager: cacheManager)
        return (store, deps.account, deps.entry)
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
        let container = try! ModelContainer(
            for: Entry.self, BathScaleEntry.self, BathScaleMetric.self,
            configurations: config
        )
        return ModelContext(container)
    }
}
