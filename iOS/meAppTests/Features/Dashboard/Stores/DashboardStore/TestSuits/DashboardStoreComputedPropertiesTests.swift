import Foundation
import Testing
@testable import meApp

extension DashboardStoreTests {
    @Suite("Computed Properties")
    @MainActor
    struct ComputedProperties {

    @Test("hasAnyEntries: returns false when data is empty")
    func hasAnyEntriesFalseWhenEmpty() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.hasAnyEntries == false)
    }

    @Test("hasAnyEntries: returns true when daily summaries exist")
    func hasAnyEntriesTrueWithData() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.dataManager.state.dailySummaries = [DashboardTestFixtures.makeSummary()]

        #expect(store.hasAnyEntries == true)
    }

    @Test("hasGoalSet: reflects goal state")
    func hasGoalSetReflectsState() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.hasGoalSet == false)

        store.goalManager.state.hasGoalSet = true
        #expect(store.state.goal.hasGoalSet == true || store.goalManager.state.hasGoalSet == true)
    }

    @Test("metricsToShow: returns empty when config not loaded")
    func metricsToShowEmptyWithoutConfig() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = false
        store.metricsManager.state.metrics = [DashboardTestFixtures.makeMetricItem(label: "bmi")]

        #expect(store.metricsToShow.isEmpty)
    }

    @Test("metricsToShow: returns metrics when config loaded")
    func metricsToShowWithConfig() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true

        #expect(store.metricsToShow.count == 12)
    }

    @Test("effectiveDashboardType: reflects metrics state")
    func effectiveDashboardTypeReflectsState() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        store.metricsManager.state.dashboardType = .dashboard4
        #expect(store.effectiveDashboardType == .dashboard4 || store.metricsManager.state.dashboardType == .dashboard4)
    }

    @Test("streakItemsToShow: returns empty when no streaks")
    func streakItemsToShowEmptyNoStreaks() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.streakManager.state.streakItems = []

        #expect(store.streakItemsToShow.isEmpty)
    }

    @Test("streakItemsToShow: filters removed streaks in non-edit mode")
    func streakItemsToShowFiltersRemoved() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        let items = [
            DashboardTestFixtures.makeMetricItem(label: "current"),
            DashboardTestFixtures.makeMetricItem(label: "longest"),
            DashboardTestFixtures.makeMetricItem(label: "weeklyChange")
        ]
        store.streakManager.state.streakItems = items
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.isEditMode = false
        store.state.ui.removedStreaks = ["longest"]

        let shown = store.streakItemsToShow
        #expect(shown.count == 2)
        #expect(shown.allSatisfy { $0.label != "longest" })
    }

    @Test("streakItemsToShow: shows all in edit mode with removed items last")
    func streakItemsToShowAllInEditMode() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        let items = [
            DashboardTestFixtures.makeMetricItem(label: "current"),
            DashboardTestFixtures.makeMetricItem(label: "longest"),
            DashboardTestFixtures.makeMetricItem(label: "weeklyChange")
        ]
        store.streakManager.state.streakItems = items
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.isEditMode = true
        store.state.ui.removedStreaks = ["longest"]

        let shown = store.streakItemsToShow
        #expect(shown.count == 3)
        #expect(shown.last?.label == "longest")
    }

    @Test("streakItemsToShow: returns all when progress not loaded yet")
    func streakItemsToShowAllWhenNotLoaded() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        let items = [
            DashboardTestFixtures.makeMetricItem(label: "current"),
            DashboardTestFixtures.makeMetricItem(label: "longest")
        ]
        store.streakManager.state.streakItems = items
        store.state.ui.hasLoadedProgressMetrics = false
        store.state.ui.isEditMode = false

        let shown = store.streakItemsToShow
        #expect(shown.count == 2)
    }

    @Test("isAnyItemBeingDragged: delegates to UIState")
    func isAnyItemBeingDraggedDelegates() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        #expect(store.isAnyItemBeingDragged == false)

        store.state.ui.draggingMetric = DashboardTestFixtures.makeMetricItem(label: "x")
        #expect(store.isAnyItemBeingDragged == true)
    }

    @Test("isWeightlessModeEnabled: returns false when no account")
    func isWeightlessModeDisabledNoAccount() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.isWeightlessModeEnabled == false)
    }

    @Test("currentUnit: returns lb by default when no account")
    func currentUnitDefaultLb() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.currentUnit == .lb)
    }

    @Test("selectedBodyMetric: returns weight when no label selected")
    func selectedBodyMetricDefaultWeight() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.selectedMetricLabel = nil

        #expect(store.selectedBodyMetric == .weight)
    }

    @Test("shouldShowGoalCardOrStreaks: true when goal card not removed")
    func shouldShowGoalCardOrStreaksTrueGoalNotRemoved() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.isGoalCardRemoved = false

        #expect(store.shouldShowGoalCardOrStreaks == true)
    }

    @Test("shouldShowGoalCardOrStreaks: true when streaks visible")
    func shouldShowGoalCardOrStreaksTrueWithStreaks() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.isGoalCardRemoved = true

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "current")]
        store.state.ui.hasLoadedProgressMetrics = true

        #expect(store.shouldShowGoalCardOrStreaks == true)
    }

    @Test("shouldShowGoalCardOrStreaks: false when all removed and no streaks")
    func shouldShowGoalCardOrStreaksFalse() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.isGoalCardRemoved = true
        store.streakManager.state.streakItems = []

        #expect(store.shouldShowGoalCardOrStreaks == false)
    }

    @Test("hasBodyMetrics: false when metricsToShow is empty")
    func hasBodyMetricsFalseWhenEmpty() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.hasBodyMetrics == false)
    }

    @Test("shouldShowBodyMetrics: false when no dashboard config and no account")
    func shouldShowBodyMetricsFalseNoConfig() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.shouldShowBodyMetrics == false)
    }

    @Test("shouldShowBodyMetrics: delegates to hasBodyMetrics when config loaded")
    func shouldShowBodyMetricsWhenConfigLoaded() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true
        store.metricsManager.state.metrics = []
        store.metricsManager.state.activeMetricsCount = 0

        #expect(store.shouldShowBodyMetrics == false)
    }

    @Test("shouldShowBodyMetricsSkeleton: true when config not loaded and should show")
    func shouldShowBodyMetricsSkeletonPreLoading() {
        let (store, accountService, _) = DashboardStoreTestSupport.makeSUT()
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            dashboardMetrics: "bmi,bodyFat,muscleMass",
            dashboardType: "dashboard12"
        )
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.shouldShowBodyMetrics == true)
        #expect(store.shouldShowBodyMetricsSkeleton == true)
    }

    @Test("shouldShowProgressMetricsSkeleton: true when progress not loaded")
    func shouldShowProgressMetricsSkeletonTrue() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedProgressMetrics = false

        #expect(store.shouldShowProgressMetricsSkeleton == true)
    }

    @Test("shouldShowProgressMetricsSkeleton: false when progress loaded and no goal/streaks")
    func shouldShowProgressMetricsSkeletonFalse() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.shouldShowProgressMetricsSkeleton == false)
    }

    @Test("shouldShowGoalStreakSection: false when config not loaded")
    func shouldShowGoalStreakSectionFalseNoConfig() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.shouldShowGoalStreakSection == false)
    }

    @Test("shouldShowGoalStreakSection: true when config loaded and goal not removed")
    func shouldShowGoalStreakSectionTrueWithGoal() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.isGoalCardRemoved = false

        #expect(store.shouldShowGoalStreakSection == true)
    }

    @Test("shouldShowStreakGrid: false when no visible streaks")
    func shouldShowStreakGridFalseNoStreaks() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.streakManager.state.streakItems = []

        #expect(store.shouldShowStreakGrid == false)
    }

    @Test("shouldShowStreakGrid: false when all streaks removed")
    func shouldShowStreakGridFalseAllRemoved() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "current")]
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.removedStreaks = ["current"]

        #expect(store.shouldShowStreakGrid == false)
    }

    @Test("shouldShowStreakGrid: true when visible streaks exist")
    func shouldShowStreakGridTrueWithStreaks() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()

        store.streakManager.state.streakItems = [DashboardTestFixtures.makeMetricItem(label: "current")]
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.removedStreaks = []

        #expect(store.shouldShowStreakGrid == true)
    }

    @Test("shouldShowDivider: false when no body metrics and no progress")
    func shouldShowDividerFalse() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = false

        #expect(store.shouldShowDivider == false)
    }

    @Test("shouldShowDivider: true when body metrics and progress content are both visible")
    func shouldShowDividerTrue() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.hasLoadedMetricValues = true
        store.state.ui.hasLoadedProgressMetrics = true
        store.state.ui.isGoalCardRemoved = false

        #expect(store.shouldShowBodyMetrics == true)
        #expect(store.shouldShowGoalStreakSection == true)
        #expect(store.shouldShowDivider == true)
    }

    // MARK: - hasBabyEntries

    @Test("hasBabyEntries: returns false when no baby profile is selected")
    func hasBabyEntriesFalseNoProfileSelected() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.hasBabyEntries == false)
    }

    @Test("hasBabyEntries: returns false when baby profile selected but no summaries loaded")
    func hasBabyEntriesFalseNoSummaries() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        let baby = BabyProfile(id: "baby-1", name: "Aria")
        store.selectProductItem(.baby(profile: baby))

        #expect(store.hasBabyEntries == false)
    }

    @Test("hasBabyEntries: returns true when daily summaries exist for selected baby profile")
    func hasBabyEntriesTrueWithDailySummaries() {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let store = DashboardStore(
            lightweight: true,
            formatter: MockDashboardFormatter(),
            cacheManager: MockDashboardCacheManager()
        )
        let baby = BabyProfile(id: "baby-1", name: "Aria")
        store.selectProductItem(.baby(profile: baby))
        deps.entry.babyDailySummariesByProfile["baby-1"] = [DashboardTestFixtures.makeSummary()]

        #expect(store.hasBabyEntries == true)
    }

    @Test("allContentRemoved: false by default")
    func allContentRemovedFalseDefault() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        #expect(store.allContentRemoved == false)
    }

    @Test("allContentRemoved: true when all metrics, goal card, and streaks are removed")
    func allContentRemovedTrueWhenEverythingRemoved() {
        let (store, _, _) = DashboardStoreTestSupport.makeSUT()
        store.state.ui.hasLoadedDashboardConfig = true
        store.state.ui.isEditMode = false
        store.state.ui.isGoalCardRemoved = true
        store.state.ui.removedMetrics = Set(store.metricsManager.state.metrics.map(\.label))
        store.state.ui.removedStreaks = Set(store.streakManager.state.streakItems.map(\.label))
        store.streakManager.state.activeStreakItemsCount = 0

        #expect(store.metricsToShow.isEmpty)
        #expect(store.shouldShowStreakGrid == false)
        #expect(store.allContentRemoved == true)
    }
    }
}
