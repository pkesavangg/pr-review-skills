import Combine
import Foundation
@testable import meApp
import Testing

extension DashboardStoreTests {
    @Suite("Utility And Derived Data")
    @MainActor
    struct UtilityAndDerivedData {

    @Test("invalidateContinuousOperationsCache: delegates to cache manager")
    func invalidateCacheDelegate() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let cacheManager = sutBundle.cacheManager

        store.invalidateContinuousOperationsCache()

        #expect(cacheManager.invalidateContinuousOpsCalls == 1)
    }

    @Test("invalidateContinuousOperationsCache: multiple calls increment counter")
    func invalidateCacheMultipleCalls() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let cacheManager = sutBundle.cacheManager

        store.invalidateContinuousOperationsCache()
        store.invalidateContinuousOperationsCache()
        store.invalidateContinuousOperationsCache()

        #expect(cacheManager.invalidateContinuousOpsCalls == 3)
    }

    @Test("forceImmediateUIUpdate: triggers objectWillChange")
    func forceImmediateUIUpdateTriggersChange() {
        let store = DashboardStoreTestSupport.makeSUT().store

        var didReceiveUpdate = false
        let cancellable = store.objectWillChange.sink { didReceiveUpdate = true }

        store.forceImmediateUIUpdate()

        #expect(didReceiveUpdate == true)
        cancellable.cancel()
    }

    @Test("scheduleUIUpdate: eventually triggers objectWillChange")
    func scheduleUIUpdateTriggersChange() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        var didReceiveUpdate = false
        let cancellable = store.objectWillChange.sink { didReceiveUpdate = true }

        store.scheduleUIUpdate()

        await DashboardTestFixtures.waitUntil { didReceiveUpdate }

        #expect(didReceiveUpdate == true)
        cancellable.cancel()
    }

    @Test("scheduleUIUpdate: debounces multiple rapid calls")
    func scheduleUIUpdateDebounces() async {
        let store = DashboardStoreTestSupport.makeSUT().store

        var updateCount = 0
        let cancellable = store.objectWillChange.sink { updateCount += 1 }

        store.scheduleUIUpdate()
        store.scheduleUIUpdate()
        store.scheduleUIUpdate()

        await DashboardTestFixtures.waitUntil(timeoutNanoseconds: 500_000_000) { updateCount >= 1 }

        #expect(updateCount >= 1)
        cancellable.cancel()
    }

    @Test("allowedNumericCharacters: contains expected characters")
    func allowedNumericCharactersContainsExpected() {
        let allowed = DashboardStore.allowedNumericCharacters
        #expect(allowed.isSuperset(of: CharacterSet(charactersIn: "0123456789.-")))
    }

    @Test("allowedNumericCharacters: does not contain letters")
    func allowedNumericCharactersNoLetters() {
        let allowed = DashboardStore.allowedNumericCharacters
        #expect(!allowed.isSuperset(of: CharacterSet(charactersIn: "abc")))
    }

    @Test("continuousOperations: returns data from cache manager")
    func continuousOperationsFromCache() {
        let store = DashboardStoreTestSupport.makeSUT().store

        let result = store.continuousOperations
        #expect(result.isEmpty)
    }

    @Test("dataChangeRevision increments when dashboard data changes")
    func dataChangeRevisionIncrementsOnDataUpdate() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let entryService = DependencyContainer.shared.resolve(EntryService.self)

        #expect(entryService != nil)
        #expect(store.dataChangeRevision == 0)

        entryService?.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()

        await DashboardTestFixtures.waitUntil { store.dataChangeRevision == 1 }
        #expect(store.dataChangeRevision == 1)
    }

    @Test("continuousOperations: returns sorted daily summaries from data manager")
    func continuousOperationsReturnsSortedData() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let entryService = DependencyContainer.shared.resolve(EntryService.self)

        #expect(entryService != nil)

        entryService?.dailySummaries = DashboardTestFixtures.makeUnsortedDailySummaries()
        await DashboardTestFixtures.waitUntil { store.continuousOperations.count == 5 }

        let result = store.continuousOperations

        #expect(result.count == 5)
        #expect(result.map(\.period) == ["2026-03-01", "2026-03-02", "2026-03-03", "2026-03-04", "2026-03-05"])
    }

    @Test("visibleOperations: returns data from cache manager")
    func visibleOperationsFromCache() {
        let store = DashboardStoreTestSupport.makeSUT().store

        let result = store.visibleOperations
        #expect(result.isEmpty)
    }

    @Test("chartSeriesData: returns empty when no continuous operations exist")
    func chartSeriesDataEmptyWithoutOperations() {
        let store = DashboardStoreTestSupport.makeSUT().store

        #expect(store.chartSeriesData.isEmpty)
    }

    @Test("visibleDomainLength: returns expected values for supported periods")
    func visibleDomainLengthForPeriods() {
        let store = DashboardStoreTestSupport.makeSUT().store

        #expect(store.visibleDomainLength(for: .week) > 0)
        #expect(store.visibleDomainLength(for: .month) > store.visibleDomainLength(for: .week))
        #expect(store.visibleDomainLength(for: .year) > store.visibleDomainLength(for: .month))
    }

    @Test("weightlessAnchorWeight: returns nil when no account")
    func weightlessAnchorWeightNilNoAccount() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.weightlessAnchorWeight == nil)
    }

    @Test("weightlessAnchorWeight: returns converted anchor weight when enabled")
    func weightlessAnchorWeightReturnsConvertedValue() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightUnit: .lb,
            weightlessOn: true,
            weightlessWeight: 1850
        )

        #expect(store.weightlessAnchorWeight == 185.0)
        #expect(store.isWeightlessModeEnabled == true)
        #expect(store.currentWeightlessMode == true)
    }

    @Test("goalWeightForDisplay: returns nil when no goal set")
    func goalWeightForDisplayNilNoGoal() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.goalWeightForDisplay == nil)
    }

    @Test("goalWeightForDisplay: returns displayed goal weight from active account")
    func goalWeightForDisplayFromActiveAccount() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(goalWeight: 1900)

        #expect(store.goalWeightForDisplay == 190.0)
    }

    @Test("goalWeightForDisplay: subtracts anchor weight in weightless mode")
    func goalWeightForDisplayWeightlessMode() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            goalWeight: 1900,
            weightlessOn: true,
            weightlessWeight: 1850
        )

        #expect(store.weightlessAnchorWeight == 185.0)
        #expect(store.goalWeightForDisplay == 5.0)
    }

    @Test("hasEntriesButNoneInCurrentPeriod: false when no entries")
    func hasEntriesButNoneInCurrentPeriodFalseNoEntries() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.hasEntriesButNoneInCurrentPeriod == false)
    }

    @Test("hasEntriesButNoneInCurrentPeriod: true when continuous operations exist but visible operations are empty")
    func hasEntriesButNoneInCurrentPeriodTrueWhenVisibleEmpty() async {
        let store = DashboardStoreTestSupport.makeSUT().store
        let entryService = DependencyContainer.shared.resolve(EntryService.self)

        #expect(entryService != nil)

        store.graphManager.state.selectedPeriod = .week
        store.graphManager.state.xScrollPosition = DateTimeTools.getDateFromDateString("2030-01-01", format: "yyyy-MM-dd")
        entryService?.dailySummaries = DashboardTestFixtures.makeSortedDailySummaries()

        await DashboardTestFixtures.waitUntil { !store.continuousOperations.isEmpty }

        #expect(!store.continuousOperations.isEmpty)
        #expect(store.visibleOperations.isEmpty)
        #expect(store.hasEntriesButNoneInCurrentPeriod == true)
    }

    @Test("currentUnitString: returns rawValue of unit")
    func currentUnitStringReturnsRawValue() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.currentUnitString == store.currentUnit.rawValue)
    }

    @Test("currentUnitText: defaults to lbs when there is no active account")
    func currentUnitTextDefaultsToLbs() {
        let store = DashboardStoreTestSupport.makeSUT().store
        #expect(store.currentUnitText == "lbs")
    }

    @Test("current unit properties: reflect active account settings")
    func currentUnitPropertiesReflectActiveAccount() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: .kg)

        #expect(store.currentUnit == .kg)
        #expect(store.currentUnitString == "kg")
        #expect(store.currentUnitText == "kg")
    }

    @Test("unitText: delegates to goal manager")
    func unitTextDelegatesToGoalManager() {
        let sutBundle = DashboardStoreTestSupport.makeSUT()
        let store = sutBundle.store
        let accountService = sutBundle.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(weightUnit: .kg)

        #expect(store.unitText == "kg")
    }

    @Test("loaderData: returns nil when not loading and no override")
    func loaderDataNilWhenNotLoading() {
        let store = DashboardStoreTestSupport.makeSUT().store
        store.state.ui.isLoading = false
        store.state.ui.loaderOverride = nil

        #expect(store.loaderData.wrappedValue == nil)
    }

    @Test("loaderData: returns loader when isLoading is true")
    func loaderDataReturnsWhenLoading() {
        let store = DashboardStoreTestSupport.makeSUT().store
        store.state.ui.isLoading = true

        #expect(store.loaderData.wrappedValue != nil)
    }

    @Test("loaderData: returns override when set")
    func loaderDataReturnsOverride() {
        let store = DashboardStoreTestSupport.makeSUT().store
        store.state.ui.loaderOverride = LoaderModel(text: "Custom")

        #expect(store.loaderData.wrappedValue?.text == "Custom")
    }

    @Test("store state remains consistent after rapid state mutations")
    func stateConsistencyAfterRapidMutations() {
        let store = DashboardStoreTestSupport.makeSUT().store

        for i in 0..<100 {
            store.state.ui.isLoading = (i % 2 == 0)
            store.state.graph.dataChangeTrigger = i
            store.state.goal.goalProgress = CGFloat(i) / 100.0
        }

        #expect(store.state.ui.isLoading == false)
        #expect(store.state.graph.dataChangeTrigger == 99)
        #expect(store.state.goal.goalProgress == 0.99)
    }
    }
}
