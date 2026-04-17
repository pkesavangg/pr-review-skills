import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct DashboardSyncCoordinatorTests {

    // MARK: - SUT Factory

    private func makeSUT() -> (
        sut: DashboardSyncCoordinator,
        accountService: AccountService,
        entryService: EntryService
    ) {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let sut = DashboardSyncCoordinator()
        return (sut, deps.account, deps.entry)
    }

    private func makeDefaultStreaks() -> [MetricItem] {
        [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak),
            DashboardTestFixtures.makeMetricItem(label: "1.2 lbs/week"),
            DashboardTestFixtures.makeMetricItem(label: "5.0 lbs/month"),
            DashboardTestFixtures.makeMetricItem(label: "10.0 lbs/year"),
            DashboardTestFixtures.makeMetricItem(label: "-3.0 lbs/total")
        ]
    }

    private func makeNotification() -> MockNotificationHelperService? {
        DependencyContainer.shared.resolve(MockNotificationHelperService.self)
    }

    // MARK: - syncEntries Tests

    @Test("syncEntries: completes without error")
    func syncEntriesCompletes() async {
        let (sut, _, _) = makeSUT()

        // Should complete without throwing or crashing
        await sut.syncEntries()
    }

    // MARK: - saveChanges Tests

    @Test("saveChanges success: calls save metrics, save progress, load progress, then onSuccess")
    func saveChangesSuccessPath() async {
        let (sut, _, _) = makeSUT()

        var callOrder: [String] = []
        var onSuccessCalled = false
        var onErrorCalled = false

        sut.saveChanges(
            saveMetrics: {
                callOrder.append("saveMetrics")
            },
            saveProgressMetrics: {
                callOrder.append("saveProgressMetrics")
            },
            loadProgressMetrics: {
                callOrder.append("loadProgressMetrics")
            },
            onSuccess: {
                onSuccessCalled = true
                callOrder.append("onSuccess")
            },
            onError: { _ in
                onErrorCalled = true
            }
        )

        // saveChanges spawns a Task, so wait for it
        await DashboardTestFixtures.waitUntil {
            onSuccessCalled || onErrorCalled
        }

        #expect(onSuccessCalled == true)
        #expect(onErrorCalled == false)
        #expect(callOrder == ["saveMetrics", "saveProgressMetrics", "loadProgressMetrics", "onSuccess"])
    }

    @Test("saveChanges success: shows and dismisses loader")
    func saveChangesTogglesLoader() async {
        let (sut, _, _) = makeSUT()
        let notification = makeNotification()
        var completed = false

        sut.saveChanges(
            saveMetrics: {},
            saveProgressMetrics: {},
            loadProgressMetrics: {},
            onSuccess: {
                completed = true
            },
            onError: { _ in
                Issue.record("saveChanges should succeed")
            }
        )

        await DashboardTestFixtures.waitUntil {
            completed && (notification?.dismissLoaderCalls ?? 0) == 1
        }

        #expect(notification?.showLoaderCalls == 1)
        #expect(notification?.dismissLoaderCalls == 1)
    }

    @Test("saveChanges failure in saveMetrics: calls onError")
    func saveChangesMetricsFailure() async {
        let (sut, _, _) = makeSUT()

        var onSuccessCalled = false
        var capturedError: Error?

        sut.saveChanges(
            saveMetrics: {
                throw DashboardTestError.simulatedFailure
            },
            saveProgressMetrics: {
                Issue.record("saveProgressMetrics should not be called on metrics save failure")
            },
            loadProgressMetrics: {
                Issue.record("loadProgressMetrics should not be called on metrics save failure")
            },
            onSuccess: {
                onSuccessCalled = true
            },
            onError: { error in
                capturedError = error
            }
        )

        await DashboardTestFixtures.waitUntil {
            capturedError != nil || onSuccessCalled
        }

        #expect(onSuccessCalled == false)
        #expect(capturedError != nil)
        #expect(capturedError is DashboardTestError)
    }

    @Test("saveChanges failure in saveProgressMetrics: calls onError")
    func saveChangesProgressMetricsFailure() async {
        let (sut, _, _) = makeSUT()

        var onSuccessCalled = false
        var capturedError: Error?

        sut.saveChanges(
            saveMetrics: {
                // Succeeds
            },
            saveProgressMetrics: {
                throw DashboardTestError.simulatedFailure
            },
            loadProgressMetrics: {
                Issue.record("loadProgressMetrics should not be called on progress save failure")
            },
            onSuccess: {
                onSuccessCalled = true
            },
            onError: { error in
                capturedError = error
            }
        )

        await DashboardTestFixtures.waitUntil {
            capturedError != nil || onSuccessCalled
        }

        #expect(onSuccessCalled == false)
        #expect(capturedError != nil)
    }

    // MARK: - saveProgressMetricsToAPI Tests

    @Test("saveProgressMetricsToAPI: throws noActiveAccount when no account is set")
    func saveProgressMetricsNoAccount() async {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = nil

        var thrownError: DashboardError?
        do {
            try await sut.saveProgressMetricsToAPI(
                streakItems: [],
                streakOrder: [],
                goalCardPosition: 0,
                isGoalCardRemoved: false,
                removedStreaks: []
            ) { _ in }
        } catch let error as DashboardError {
            thrownError = error
        } catch {}

        #expect(thrownError == .noActiveAccount)
    }

    @Test("saveProgressMetricsToAPI: builds correct order with goal card and streaks")
    func saveProgressMetricsBuildsCorrectOrder() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak)
        ]

        var capturedMetrics: [String] = []

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: false,
            removedStreaks: []
        ) { metrics in
            capturedMetrics = metrics
        }

        // Goal at position 0, then currentStreak, longestStreak
        #expect(capturedMetrics.first == "goal")
        #expect(capturedMetrics.contains("currentStreak"))
        #expect(capturedMetrics.contains("longestStreak"))
    }

    @Test("saveProgressMetricsToAPI: excludes removed streaks")
    func saveProgressMetricsExcludesRemovedStreaks() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak)
        ]

        var capturedMetrics: [String] = []

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: false,
            removedStreaks: Set([DashboardStrings.longestStreak])
        ) { metrics in
            capturedMetrics = metrics
        }

        #expect(capturedMetrics.contains("currentStreak"))
        #expect(!capturedMetrics.contains("longestStreak"))
    }

    @Test("saveProgressMetricsToAPI: excludes goal card when removed")
    func saveProgressMetricsExcludesGoalWhenRemoved() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)
        ]

        var capturedMetrics: [String] = []

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: true,
            removedStreaks: []
        ) { metrics in
            capturedMetrics = metrics
        }

        #expect(!capturedMetrics.contains("goal"))
        #expect(capturedMetrics.contains("currentStreak"))
    }

    @Test("saveProgressMetricsToAPI: filters out invalid metric values")
    func saveProgressMetricsFiltersInvalidValues() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        // A streak with an unmappable label should be excluded
        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: "unknownMetric"),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)
        ]

        var capturedMetrics: [String] = []

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: true,
            removedStreaks: []
        ) { metrics in
            capturedMetrics = metrics
        }

        #expect(capturedMetrics == ["currentStreak"])
    }

    @Test("saveProgressMetricsToAPI: preserves streak order and clamps goal position")
    func saveProgressMetricsUsesSavedOrderAndClampsGoalPosition() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak),
            DashboardTestFixtures.makeMetricItem(label: "1.2 lbs/week"),
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.longestStreak)
        ]

        var capturedMetrics: [String] = []

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [streaks[1].id.uuidString, streaks[0].id.uuidString],
            goalCardPosition: 99,
            isGoalCardRemoved: false,
            removedStreaks: []
        ) { metrics in
            capturedMetrics = metrics
        }

        #expect(capturedMetrics == ["weeklyChange", "currentStreak", "longestStreak", "goal"])
    }

    @Test("saveProgressMetricsToAPI: marks all progress metrics removed when all content is removed")
    func saveProgressMetricsMarksAllRemoved() async throws {
        let (sut, accountService, _) = makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount()
        UserDefaults.standard.removeObject(forKey: "dashboard.allProgressMetricsRemoved") // swiftlint:disable:this no_direct_userdefaults

        let streaks = [
            DashboardTestFixtures.makeMetricItem(label: DashboardStrings.currentStreak)
        ]
        var capturedMetrics: [String] = ["placeholder"]

        try await sut.saveProgressMetricsToAPI(
            streakItems: streaks,
            streakOrder: [],
            goalCardPosition: 0,
            isGoalCardRemoved: true,
            removedStreaks: [DashboardStrings.currentStreak]
        ) { metrics in
            capturedMetrics = metrics
        }

        #expect(capturedMetrics.isEmpty)
        #expect(UserDefaults.standard.bool(forKey: "dashboard.allProgressMetricsRemoved") == true) // swiftlint:disable:this no_direct_userdefaults
    }

    // MARK: - loadDashboardConfigurationFromAPI Tests

    @Test("loadDashboardConfigurationFromAPI: success path calls all callbacks")
    func loadDashboardConfigurationFromAPISuccessPath() async {
        let (sut, _, _) = makeSUT()
        var callOrder: [String] = []
        var metricsLoaded = false
        var progressLoaded = false
        var onErrorCalled = false

        await sut.loadDashboardConfigurationFromAPI(
            config: DashboardConfigurationLoadConfig(
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
                    metricsLoaded = true
                },
                onProgressMetricsLoaded: {
                    progressLoaded = true
                },
                onError: { _ in
                    onErrorCalled = true
                }
            )
        )

        #expect(callOrder == [
            "refreshAccount",
            "syncEntries",
            "loadMetricsFromAPI",
            "refreshStreakData",
            "loadProgressMetrics",
            "loadGoalData"
        ])
        #expect(metricsLoaded == true)
        #expect(progressLoaded == true)
        #expect(onErrorCalled == false)
    }

    @Test("loadDashboardConfigurationFromAPI: failure path reports error")
    func loadDashboardConfigurationFromAPIFailurePath() async {
        let (sut, _, _) = makeSUT()
        var metricsLoaded = false
        var progressLoaded = false
        var capturedError: Error?

        await sut.loadDashboardConfigurationFromAPI(
            config: DashboardConfigurationLoadConfig(
                refreshAccount: {},
                syncEntries: {},
                loadMetricsFromAPI: {
                    throw DashboardTestError.simulatedFailure
                },
                refreshStreakData: {},
                loadProgressMetrics: {},
                loadGoalData: {},
                onMetricsLoaded: {
                    metricsLoaded = true
                },
                onProgressMetricsLoaded: {
                    progressLoaded = true
                },
                onError: { error in
                    capturedError = error
                }
            )
        )

        #expect(metricsLoaded == false)
        #expect(progressLoaded == false)
        #expect(capturedError is DashboardTestError)
    }

    // MARK: - loadProgressMetricsFromAccount Tests

    @Test("loadProgressMetricsFromAccount: no account falls back to default order")
    func loadProgressMetricsFromAccountNoAccountUsesDefaults() async {
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()
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
        let (sut, _, _) = makeSUT()

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

    // MARK: - reloadDashboardConfiguration Tests

    @Test("reloadDashboardConfiguration: calls loadConfiguration and schedules UI update")
    func reloadConfigLoadsAndSchedules() async {
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

        #expect(sut.mapAPIValueToStreakLabel("unknownValue", allStreaks: []) == nil)
    }

    @Test("mapStreakLabelToAPI: maps all known streak labels correctly")
    func mapStreakLabelToAPIMapsCorrectly() {
        let (sut, _, _) = makeSUT()

        #expect(sut.mapStreakLabelToAPI(DashboardStrings.currentStreak) == "currentStreak")
        #expect(sut.mapStreakLabelToAPI(DashboardStrings.longestStreak) == "longestStreak")
        #expect(sut.mapStreakLabelToAPI("1.2 lbs/week") == "weeklyChange")
        #expect(sut.mapStreakLabelToAPI("5.0 lbs/month") == "monthlyChange")
        #expect(sut.mapStreakLabelToAPI("10.0 lbs/year") == "yearlyChange")
        #expect(sut.mapStreakLabelToAPI("-3.0 lbs/total") == "totalChange")
    }

    @Test("mapStreakLabelToAPI: returns nil for unknown streak label")
    func mapStreakLabelToAPIReturnsNilForUnknown() {
        let (sut, _, _) = makeSUT()

        #expect(sut.mapStreakLabelToAPI("unknown label") == nil)
    }

    // MARK: - loadDashboardConfigurationFromAPI Tests

    @Test("loadDashboardConfigurationFromAPI: calls callbacks in order on success")
    func loadConfigCallsCallbacksInOrder() async {
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
        let (sut, _, _) = makeSUT()

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
}
