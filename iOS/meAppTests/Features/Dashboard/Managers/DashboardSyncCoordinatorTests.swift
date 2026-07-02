import Foundation
@testable import meApp
import Testing

struct DashboardSyncCoordinatorTestsSUT {
    let sut: DashboardSyncCoordinator
    let accountService: AccountService
    let entryService: EntryService
}

@Suite(.serialized)
@MainActor
struct DashboardSyncCoordinatorTests {

    // MARK: - SUT Factory

    func makeSUT() -> DashboardSyncCoordinatorTestsSUT {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let sut = DashboardSyncCoordinator()
        return DashboardSyncCoordinatorTestsSUT(sut: sut, accountService: deps.account, entryService: deps.entry)
    }

    func makeDefaultStreaks() -> [MetricItem] {
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
        let sut = makeSUT().sut

        // Should complete without throwing or crashing
        await sut.syncEntries()
    }

    // MARK: - saveChanges Tests

    @Test("saveChanges success: calls save metrics, save progress, load progress, then onSuccess")
    func saveChangesSuccessPath() async {
        let sut = makeSUT().sut

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
        let sut = makeSUT().sut
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
        let sut = makeSUT().sut

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
        let sut = makeSUT().sut

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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let env = makeSUT()
        let sut = env.sut
        let accountService = env.accountService
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
        let sut = makeSUT().sut
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
        let sut = makeSUT().sut
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
}
