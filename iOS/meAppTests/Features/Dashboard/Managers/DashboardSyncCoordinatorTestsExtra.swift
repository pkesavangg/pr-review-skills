import Foundation
@testable import meApp
import Testing

@MainActor
extension DashboardSyncCoordinatorTests {

    // MARK: - reloadDashboardConfiguration Tests

    @Test("reloadDashboardConfiguration: calls loadConfiguration and schedules UI update")
    func reloadConfigLoadsAndSchedules() async {
        let sut = makeSUT().sut

        var loadConfigCalled = false
        var scheduleUpdateCalled = false

        await sut.reloadDashboardConfiguration(
            fullRefresh: false,
            updateMetrics: false,
            loadConfiguration: {
                loadConfigCalled = true
            },
            updateMetricsForView: {},
            scheduleUIUpdate: {
                scheduleUpdateCalled = true
            },
            refreshDashboardState: {}
        )

        #expect(loadConfigCalled == true)
        #expect(scheduleUpdateCalled == true)
    }

    @Test("reloadDashboardConfiguration: with fullRefresh calls refreshDashboardState")
    func reloadConfigFullRefreshCallsRefresh() async {
        let sut = makeSUT().sut

        var refreshCalled = false

        await sut.reloadDashboardConfiguration(
            fullRefresh: true,
            updateMetrics: false,
            loadConfiguration: {},
            updateMetricsForView: {},
            scheduleUIUpdate: {},
            refreshDashboardState: {
                refreshCalled = true
            }
        )

        #expect(refreshCalled == true)
    }

    @Test("reloadDashboardConfiguration: without fullRefresh does not call refreshDashboardState")
    func reloadConfigNoFullRefreshSkipsRefresh() async {
        let sut = makeSUT().sut

        var refreshCalled = false

        await sut.reloadDashboardConfiguration(
            fullRefresh: false,
            updateMetrics: false,
            loadConfiguration: {},
            updateMetricsForView: {},
            scheduleUIUpdate: {},
            refreshDashboardState: {
                refreshCalled = true
            }
        )

        #expect(refreshCalled == false)
    }

    @Test("reloadDashboardConfiguration: with updateMetrics calls updateMetricsForView")
    func reloadConfigWithUpdateMetrics() async {
        let sut = makeSUT().sut

        var updateMetricsCalled = false

        await sut.reloadDashboardConfiguration(
            fullRefresh: false,
            updateMetrics: true,
            loadConfiguration: {},
            updateMetricsForView: {
                updateMetricsCalled = true
            },
            scheduleUIUpdate: {},
            refreshDashboardState: {}
        )

        #expect(updateMetricsCalled == true)
    }

    @Test("reloadDashboardConfiguration: without updateMetrics skips updateMetricsForView")
    func reloadConfigWithoutUpdateMetrics() async {
        let sut = makeSUT().sut

        var updateMetricsCalled = false

        await sut.reloadDashboardConfiguration(
            fullRefresh: false,
            updateMetrics: false,
            loadConfiguration: {},
            updateMetricsForView: {
                updateMetricsCalled = true
            },
            scheduleUIUpdate: {},
            refreshDashboardState: {}
        )

        #expect(updateMetricsCalled == false)
    }

    // MARK: - refreshAll Tests

    @Test("refreshAll: syncs entries then calls onAppearActions")
    func refreshAllSyncsThenAppears() async {
        let sut = makeSUT().sut

        var callOrder: [String] = []

        await sut.refreshAll(
            syncEntries: {
                callOrder.append("syncEntries")
            },
            onAppearActions: {
                callOrder.append("onAppearActions")
            }
        )

        #expect(callOrder == ["syncEntries", "onAppearActions"])
    }

    // MARK: - API Mapping Helper Tests

    @Test("mapAPIValueToStreakLabel: maps all known API values correctly")
    func mapAPIValueToStreakLabelMapsCorrectly() {
        let sut = makeSUT().sut

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "1.2 lbs/week"),
            DashboardTestFixtures.makeMetricItem(label: "5.0 lbs/month"),
            DashboardTestFixtures.makeMetricItem(label: "10.0 lbs/year"),
            DashboardTestFixtures.makeMetricItem(label: "-3.0 lbs/total")
        ]

        #expect(sut.mapAPIValueToStreakLabel("currentStreak", allStreaks: streaks) == DashboardStrings.currentStreak)
        #expect(sut.mapAPIValueToStreakLabel("longestStreak", allStreaks: streaks) == DashboardStrings.longestStreak)
        #expect(sut.mapAPIValueToStreakLabel("weeklyChange", allStreaks: streaks) == "1.2 lbs/week")
        #expect(sut.mapAPIValueToStreakLabel("monthlyChange", allStreaks: streaks) == "5.0 lbs/month")
        #expect(sut.mapAPIValueToStreakLabel("yearlyChange", allStreaks: streaks) == "10.0 lbs/year")
        #expect(sut.mapAPIValueToStreakLabel("totalChange", allStreaks: streaks) == "-3.0 lbs/total")
    }

    @Test("mapAPIValueToStreakLabel: returns nil for unknown API value")
    func mapAPIValueToStreakLabelReturnsNilForUnknown() {
        let sut = makeSUT().sut

        #expect(sut.mapAPIValueToStreakLabel("unknownValue", allStreaks: []) == nil)
    }

    @Test("mapStreakLabelToAPI: maps all known streak labels correctly")
    func mapStreakLabelToAPIMapsCorrectly() {
        let sut = makeSUT().sut

        #expect(sut.mapStreakLabelToAPI(DashboardStrings.currentStreak) == "currentStreak")
        #expect(sut.mapStreakLabelToAPI(DashboardStrings.longestStreak) == "longestStreak")
        #expect(sut.mapStreakLabelToAPI("1.2 lbs/week") == "weeklyChange")
        #expect(sut.mapStreakLabelToAPI("5.0 lbs/month") == "monthlyChange")
        #expect(sut.mapStreakLabelToAPI("10.0 lbs/year") == "yearlyChange")
        #expect(sut.mapStreakLabelToAPI("-3.0 lbs/total") == "totalChange")
    }

    @Test("mapStreakLabelToAPI: returns nil for unknown streak label")
    func mapStreakLabelToAPIReturnsNilForUnknown() {
        let sut = makeSUT().sut

        #expect(sut.mapStreakLabelToAPI("unknown label") == nil)
    }

    // MARK: - loadDashboardConfigurationFromAPI Tests

    @Test("loadDashboardConfigurationFromAPI: calls callbacks in order on success")
    func loadConfigCallsCallbacksInOrder() async {
        let sut = makeSUT().sut

        var callOrder: [String] = []

        let config = DashboardConfigurationLoadConfig(
            refreshAccount: {
                callOrder.append("refreshAccount")
            },
            syncEntries: {
                callOrder.append("syncEntries")
            },
            loadMetricsFromAPI: {
                callOrder.append("loadMetricsFromAPI")
            },
            refreshStreakData: {
                callOrder.append("refreshStreakData")
            },
            loadProgressMetrics: {
                callOrder.append("loadProgressMetrics")
            },
            loadGoalData: {
                callOrder.append("loadGoalData")
            },
            onMetricsLoaded: {
                callOrder.append("onMetricsLoaded")
            },
            onProgressMetricsLoaded: {
                callOrder.append("onProgressMetricsLoaded")
            },
            onError: { _ in
                callOrder.append("onError")
            }
        )

        await sut.loadDashboardConfigurationFromAPI(config: config)

        #expect(callOrder.contains("syncEntries"))
        #expect(callOrder.contains("loadMetricsFromAPI"))
        #expect(callOrder.contains("onMetricsLoaded"))
        #expect(callOrder.contains("refreshStreakData"))
        #expect(callOrder.contains("loadProgressMetrics"))
        #expect(callOrder.contains("onProgressMetricsLoaded"))
        #expect(!callOrder.contains("onError"))
    }

    @Test("loadDashboardConfigurationFromAPI: calls onError when loadMetricsFromAPI throws")
    func loadConfigCallsOnErrorOnFailure() async {
        let sut = makeSUT().sut

        var errorCalled = false
        var progressMetricsLoaded = false

        let config = DashboardConfigurationLoadConfig(
            refreshAccount: {},
            syncEntries: {},
            loadMetricsFromAPI: {
                throw DashboardTestError.simulatedFailure
            },
            refreshStreakData: {},
            loadProgressMetrics: {},
            loadGoalData: {},
            onMetricsLoaded: {},
            onProgressMetricsLoaded: {
                progressMetricsLoaded = true
            },
            onError: { _ in
                errorCalled = true
            }
        )

        await sut.loadDashboardConfigurationFromAPI(config: config)

        #expect(errorCalled == true)
        #expect(progressMetricsLoaded == false)
    }

    @Test("loadDashboardConfigurationFromAPI: tolerates refreshAccount failure")
    func loadConfigToleratesRefreshAccountFailure() async {
        let sut = makeSUT().sut

        var metricsLoaded = false
        var errorCalled = false

        let config = DashboardConfigurationLoadConfig(
            refreshAccount: {
                throw DashboardTestError.simulatedFailure
            },
            syncEntries: {},
            loadMetricsFromAPI: {},
            refreshStreakData: {},
            loadProgressMetrics: {},
            loadGoalData: {},
            onMetricsLoaded: {
                metricsLoaded = true
            },
            onProgressMetricsLoaded: {},
            onError: { _ in
                errorCalled = true
            }
        )

        await sut.loadDashboardConfigurationFromAPI(config: config)

        // refreshAccount uses try? so its failure should be tolerated
        #expect(metricsLoaded == true)
        #expect(errorCalled == false)
    }

    // MARK: - loadProgressMetricsFromAccount Tests

    @Test("loadProgressMetricsFromAccount: no account falls back to default order")
    func loadProgressMetricsFromAccountNoAccountUsesDefaults() async {
        let sut = makeSUT().sut
        let streaks = makeDefaultStreaks()
        var activeCount = streaks.count
        var defaultCalled = false

        await sut.loadProgressMetricsFromAccount(
            activeAccount: nil,
            allStreaks: streaks,
            streakManagerActiveCount: &activeCount,
            onProgressMetricsLoaded: { _, _, _, _ in
                Issue.record("Expected default order fallback")
            },
            setupDefaultOrder: {
                defaultCalled = true
            }
        )

        #expect(defaultCalled == true)
    }

    @Test("loadProgressMetricsFromAccount: missing saved progress metrics falls back to default order")
    func loadProgressMetricsFromAccountMissingValueUsesDefaults() async {
        let sut = makeSUT().sut
        let streaks = makeDefaultStreaks()
        let account = DashboardStoreTestSupport.makeActiveAccount(progressMetrics: nil)
        var activeCount = streaks.count
        var defaultCalled = false

        await sut.loadProgressMetricsFromAccount(
            activeAccount: account,
            allStreaks: streaks,
            streakManagerActiveCount: &activeCount,
            onProgressMetricsLoaded: { _, _, _, _ in
                Issue.record("Expected default order fallback")
            },
            setupDefaultOrder: {
                defaultCalled = true
            }
        )

        #expect(defaultCalled == true)
    }

    @Test("loadProgressMetricsFromAccount: all removed state returns removed goal and zero active count")
    func loadProgressMetricsFromAccountAllRemovedState() async {
        let sut = makeSUT().sut
        UserDefaults.standard.set(true, forKey: "dashboard.allProgressMetricsRemoved") // swiftlint:disable:this no_direct_userdefaults
        let streaks = makeDefaultStreaks()
        let account = DashboardStoreTestSupport.makeActiveAccount(
            progressMetrics: "goal,currentStreak,longestStreak,weeklyChange,monthlyChange,yearlyChange,totalChange"
        )
        var activeCount = streaks.count
        var captured: (Int, Bool, [String], Set<String>)? // swiftlint:disable:this large_tuple

        await sut.loadProgressMetricsFromAccount(
            activeAccount: account,
            allStreaks: streaks,
            streakManagerActiveCount: &activeCount,
            onProgressMetricsLoaded: { goalPosition, isGoalRemoved, order, removed in
                captured = (goalPosition, isGoalRemoved, order, removed)
            },
            setupDefaultOrder: {}
        )

        #expect(captured?.0 == 0)
        #expect(captured?.1 == true)
        #expect(captured?.2 == [])
        #expect(captured?.3 == Set(streaks.map(\.label)))
        #expect(activeCount == 0)
    }

    @Test("loadProgressMetricsFromAccount: maps saved goal and streak ordering")
    func loadProgressMetricsFromAccountMapsSavedOrdering() async {
        let sut = makeSUT().sut
        UserDefaults.standard.removeObject(forKey: "dashboard.allProgressMetricsRemoved") // swiftlint:disable:this no_direct_userdefaults
        let streaks = makeDefaultStreaks()
        let account = DashboardStoreTestSupport.makeActiveAccount(
            progressMetrics: "weeklyChange,goal,currentStreak"
        )
        var activeCount = streaks.count
        var captured: (Int, Bool, [String], Set<String>)? // swiftlint:disable:this large_tuple

        await sut.loadProgressMetricsFromAccount(
            activeAccount: account,
            allStreaks: streaks,
            streakManagerActiveCount: &activeCount,
            onProgressMetricsLoaded: { goalPosition, isGoalRemoved, order, removed in
                captured = (goalPosition, isGoalRemoved, order, removed)
            },
            setupDefaultOrder: {}
        )

        #expect(captured?.0 == 1)
        #expect(captured?.1 == false)
        #expect(captured?.2 == [streaks[2].id.uuidString, streaks[0].id.uuidString])
        #expect(captured?.3.contains(DashboardStrings.longestStreak) == true)
        #expect(activeCount == 2)
    }

    @Test("loadProgressMetricsFromAccount: empty streak collection falls back to default order")
    func loadProgressMetricsFromAccountWithNoStreaksUsesDefaults() async {
        let sut = makeSUT().sut
        let account = DashboardStoreTestSupport.makeActiveAccount(
            progressMetrics: "goal,currentStreak"
        )
        var activeCount = 0
        var defaultCalled = false

        await sut.loadProgressMetricsFromAccount(
            activeAccount: account,
            allStreaks: [],
            streakManagerActiveCount: &activeCount,
            onProgressMetricsLoaded: { _, _, _, _ in
                Issue.record("Expected default order fallback")
            },
            setupDefaultOrder: {
                defaultCalled = true
            }
        )

        #expect(defaultCalled == true)
    }

    // MARK: - loadMetricsFromLocalAccount Tests

    @Test("loadMetricsFromLocalAccount: updates dashboard type from account")
    func loadMetricsFromLocalAccountUpdatesDashboardType() async {
        let sut = makeSUT().sut
        let account = DashboardStoreTestSupport.makeActiveAccount(dashboardType: "dashboard4")

        var updatedType: DashboardType?
        var metricsLoaded = false

        await sut.loadMetricsFromLocalAccount(
            activeAccount: account,
            updateDashboardType: { type in
                updatedType = type
            },
            updateMetricsOrder: { _ in },
            setupInitialMetrics: {},
            onMetricsLoaded: {
                metricsLoaded = true
            }
        )

        #expect(updatedType == .dashboard4)
        #expect(metricsLoaded == true)
    }

    @Test("loadMetricsFromLocalAccount: updates metrics order when account has dashboard metrics")
    func loadMetricsFromLocalAccountUpdatesMetricsOrder() async {
        let sut = makeSUT().sut
        let account = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "weight,bodyFat,bmi"
        )

        var capturedOrder: [String]?

        await sut.loadMetricsFromLocalAccount(
            activeAccount: account,
            updateDashboardType: { _ in },
            updateMetricsOrder: { order in
                capturedOrder = order
            },
            setupInitialMetrics: {},
            onMetricsLoaded: {}
        )

        #expect(capturedOrder == ["weight", "bodyFat", "bmi"])
    }

    @Test("loadMetricsFromLocalAccount: calls setupInitialMetrics when no dashboard metrics")
    func loadMetricsFromLocalAccountCallsSetupWhenNoMetrics() async {
        let sut = makeSUT().sut
        let account = DashboardStoreTestSupport.makeActiveAccount(dashboardMetrics: nil)

        var setupCalled = false

        await sut.loadMetricsFromLocalAccount(
            activeAccount: account,
            updateDashboardType: { _ in },
            updateMetricsOrder: { _ in },
            setupInitialMetrics: {
                setupCalled = true
            },
            onMetricsLoaded: {}
        )

        #expect(setupCalled == true)
    }

    @Test("loadMetricsFromLocalAccount: calls setupInitialMetrics when no account")
    func loadMetricsFromLocalAccountCallsSetupWhenNoAccount() async {
        let sut = makeSUT().sut

        var setupCalled = false
        var metricsLoaded = false

        await sut.loadMetricsFromLocalAccount(
            activeAccount: nil,
            updateDashboardType: { _ in },
            updateMetricsOrder: { _ in },
            setupInitialMetrics: {
                setupCalled = true
            },
            onMetricsLoaded: {
                metricsLoaded = true
            }
        )

        #expect(setupCalled == true)
        #expect(metricsLoaded == true)
    }
}
