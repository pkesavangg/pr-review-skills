import Foundation
@testable import meApp
import SwiftData
import SwiftUI
import Testing

@MainActor
extension DashboardLifecycleManagerTests {

    // MARK: - Save Changes Tests

    @Test("saveChanges: clears selected metric label and drag state")
    func saveChangesClearsSelection() {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        store.state.ui.selectedMetricLabel = DashboardStrings.weight

        store.lifecycleManager.saveChanges()

        #expect(store.state.ui.selectedMetricLabel == nil)
    }

    @Test("saveChanges: success flow exits edit mode and clears snapshot")
    func saveChangesSuccessFlow() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "lifecycle-save-success")
        accountService.activeAccount = activeAccount

        store.state.ui.isEditMode = true
        store.state.ui.selectedMetricLabel = DashboardStrings.water
        store.editSessionManager.takeSnapshot(makeSnapshot(for: store))

        store.lifecycleManager.saveChanges()

        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            store.state.ui.isEditMode == false &&
                store.editSessionManager.hasSnapshot == false
        }

        #expect(completed == true)
        #expect(store.state.ui.selectedMetricLabel == nil)
    }

    @Test("saveProgressMetricsToAPI: persists goal and streak order from UI state")
    func saveProgressMetricsToAPIPersistsOrder() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "lifecycle-progress-save")
        // The real save path fetches the account from the local repo by id, so it must
        // exist in storage — not just be set as `activeAccount`.
        sut.accountLocalRepo.seed([AccountTestFixtures.makeAccountModel(id: activeAccount.accountId, isActive: true)])
        accountService.activeAccount = activeAccount

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "lbs/week")
        ]
        store.streakManager.state.streakItems = streaks
        store.state.ui.streakGridOrder = [streaks[2].id.uuidString, streaks[0].id.uuidString, streaks[1].id.uuidString]
        store.state.ui.goalCardPosition = 1
        store.state.ui.isGoalCardRemoved = false
        store.state.ui.removedStreaks = [DashboardStrings.longestStreak]

        try await store.lifecycleManager.saveProgressMetricsToAPI()

        #expect(accountService.activeAccount?.progressMetrics == "weeklyChange,goal,currentStreak")
    }

    @Test("resetDashboard: success flow restores UI defaults and clears snapshot")
    func resetDashboardSuccessFlow() async throws {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        let accountId = "reset-success"
        let activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: accountId)
        accountService.activeAccount = activeAccount
        try persistEntries([
            EntryTestFixtures.makeEntry(
                accountId: accountId,
                timestamp: "2026-03-01T08:00:00Z",
                weight: 1800
            ),
            EntryTestFixtures.makeEntry(
                accountId: accountId,
                timestamp: "2026-03-05T08:00:00Z",
                weight: 1840
            )
        ])
        let notification = makeNotification()

        store.state.ui.isEditMode = true
        store.state.ui.isGoalCardRemoved = true
        store.state.ui.selectedMetricLabel = DashboardStrings.bmi
        store.editSessionManager.takeSnapshot(makeSnapshot(for: store))

        store.lifecycleManager.resetDashboard()

        let completed = await waitUntil(timeoutNanoseconds: 5_000_000_000) {
            store.state.ui.isLoading == false &&
                store.state.ui.isResettingDashboard == false &&
                store.editSessionManager.hasSnapshot == false &&
                store.state.ui.hasLoadedMetricValues == true &&
                notification?.dismissLoaderCalls ?? 0 >= 1
        }

        #expect(completed == true)
        #expect(store.state.ui.isGoalCardRemoved == false)
        #expect(store.state.ui.isEditMode == false)
        #expect(store.state.ui.selectedMetricLabel == nil)
    }

    @Test("resetDashboard: failure flow still clears loading state and snapshot")
    func resetDashboardFailureFlow() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = nil
        let notification = makeNotification()

        store.state.ui.isEditMode = true
        store.editSessionManager.takeSnapshot(makeSnapshot(for: store))

        store.lifecycleManager.resetDashboardEnhanced()

        let completed = await waitUntil(timeoutNanoseconds: 4_000_000_000) {
            store.state.ui.isLoading == false &&
                store.state.ui.isResettingDashboard == false &&
                store.editSessionManager.hasSnapshot == false &&
                store.state.ui.hasLoadedMetricValues == true &&
                notification?.dismissLoaderCalls ?? 0 >= 1
        }

        #expect(completed == true)
        #expect(store.state.ui.isEditMode == false)
    }

    @Test("showResetDashboardAlert: presents reset alert and primary action starts reset flow")
    func showResetDashboardAlertPresentsAlert() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "reset-alert")
        let notification = makeNotification()

        store.lifecycleManager.showResetDashboardAlert()
        notification?.alertData?.buttons.last?.action(nil as String?)

        let completed = await waitUntil(timeoutNanoseconds: 4_000_000_000) {
            notification?.showAlertCalls == 1 &&
                notification?.dismissLoaderCalls ?? 0 >= 1
        }

        #expect(completed == true)
        #expect(notification?.alertData?.title == AlertStrings.ResetDashboardAlert.title)
    }

    @Test("reloadDashboardConfiguration: delegates through sync coordinator and updates state")
    func reloadDashboardConfigurationUpdatesState() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "reload-config")

        await store.lifecycleManager.reloadDashboardConfiguration(fullRefresh: true, updateMetrics: true)

        #expect(store.state.ui.hasLoadedDashboardConfig == true)
    }

    @Test("onAppearActions: updates display metrics when chart is already initialized")
    func onAppearActionsUpdatesDisplayWhenChartReady() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "on-appear-chart")
        store.state.ui.hasInitializedChart = true

        store.lifecycleManager.onAppearActions()

        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            store.state.ui.hasLoadedDashboardConfig == true
        }

        #expect(completed == true)
        #expect(store.state.ui.hasInitializedChart == true)
    }

    @Test("refreshAll: re-runs on-appear flow")
    func refreshAllRerunsOnAppearFlow() async {
        let sut = makeSUT(); let store = sut.store; let accountService = sut.accountService
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(id: "refresh-all")

        await store.lifecycleManager.refreshAll()

        let completed = await waitUntil(timeoutNanoseconds: 3_000_000_000) {
            store.state.ui.hasLoadedDashboardConfig == true
        }

        #expect(completed == true)
    }

    @Test("metric info handlers update UI selection and bindings")
    func metricInfoHandlersUpdateBindings() async {
        let sut = makeSUT(); let store = sut.store
        var selectedEntry: Entry?
        var selectedMetric: BodyMetric?

        let entryBinding = Binding<Entry?>(
            get: { selectedEntry },
            set: { selectedEntry = $0 }
        )
        let metricBinding = Binding<BodyMetric?>(
            get: { selectedMetric },
            set: { selectedMetric = $0 }
        )

        store.lifecycleManager.handleMetricLongPress(
            for: DashboardStrings.bodyFat,
            selectedEntry: entryBinding,
            selectedMetric: metricBinding
        )

        #expect(store.state.ui.selectedMetricLabel == DashboardStrings.bodyFat)
        #expect(selectedEntry != nil)
        #expect(selectedMetric == .bodyFat)

        await store.lifecycleManager.handleSelectedMetricInfoChange(
            DashboardStrings.bmi,
            selectedEntry: entryBinding,
            selectedMetric: metricBinding
        )
        store.lifecycleManager.handleSelectedMetricLabelChange(nil)
        store.lifecycleManager.handleSelectedEntryChange(selectedEntry)
        store.lifecycleManager.handleMetricInfoSheetDismiss(nil)

        #expect(store.state.ui.selectedMetricLabel == nil)
    }
}
