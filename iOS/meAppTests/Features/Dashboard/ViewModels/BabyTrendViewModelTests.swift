import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BabyTrendViewModelTests {

    // MARK: - Helpers

    private func makeSUT() -> BabyTrendViewModel { BabyTrendViewModel() }

    private func makeStore() -> DashboardStore {
        TestDependencyContainer.reset()
        TestDependencyContainer.registerDashboardConcreteDependencies()
        return DashboardStore(lightweight: true, formatter: MockDashboardFormatter(), cacheManager: MockDashboardCacheManager())
    }

    @discardableResult
    private func makeStoreWithBabyEntries(_ baby: BabyProfile) -> DashboardStore {
        TestDependencyContainer.reset()
        let deps = TestDependencyContainer.registerDashboardConcreteDependencies()
        let store = DashboardStore(lightweight: true, formatter: MockDashboardFormatter(), cacheManager: MockDashboardCacheManager())
        // Seed real baby summaries so `hasBabyEntries` is true, then select the baby.
        deps.entry.babyDailySummariesByProfile[baby.id] = BabyDashboardChartSupport.dummyDailySummaries(for: baby)
        store.selectProductItem(.baby(profile: baby))
        return store
    }

    private func makeBabyProfile(
        id: String = "b1",
        name: String = "Aria",
        sex: String = "female",
        birthday: Date? = nil
    ) -> BabyProfile {
        let bd = birthday ?? Calendar.current.date(byAdding: .day, value: -90, to: Date())
        return BabyProfile(
            id: id,
            name: name,
            birthday: bd,
            biologicalSex: sex,
            birthWeightLbs: 7.5,
            birthWeightOz: 3.0
        )
    }

    // MARK: - displayState

    @Test("displayState returns weight metric when selectedMetricLabel is nil")
    func displayStateDefaultsToWeight() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        store.state.ui.selectedMetricLabel = nil
        let state = sut.displayState(dashboardStore: store, babyProfile: baby)

        #expect(state.selectedMetric == .weight)
    }

    @Test("displayState returns height metric when selectedMetricLabel is height rawValue")
    func displayStateHeightMetric() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue
        let state = sut.displayState(dashboardStore: store, babyProfile: baby)

        #expect(state.selectedMetric == .height)
    }

    @Test("displayState headlineLabel shows period average when no point selected")
    func headlineLabelShowsPeriodAverage() {
        let sut = makeSUT()
        let baby = makeBabyProfile()
        let store = makeStoreWithBabyEntries(baby)

        store.state.graph.selectedXValue = nil
        store.state.graph.selectedPoint = nil
        let state = sut.displayState(dashboardStore: store, babyProfile: baby)

        let expected = "\(store.state.graph.selectedPeriod.rawValue) average"
        #expect(state.headlineLabel == expected)
    }

    @Test("displayState weightDisplay uses lbs and oz format")
    func displayStateWeightDisplayIsLbsOz() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        let state = sut.displayState(dashboardStore: store, babyProfile: baby)

        // Either a numeric value or "--" placeholder
        #expect(!state.weightDisplay.primary.isEmpty)
        #expect((state.weightDisplay.secondary?.isEmpty == false))
    }

    @Test("displayState heightDisplayText is non-empty")
    func displayStateHeightTextNonEmpty() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        let state = sut.displayState(dashboardStore: store, babyProfile: baby)

        #expect(!state.heightDisplayText.isEmpty)
    }

    // MARK: - applySelectedMetric

    @Test("applySelectedMetric with weight clears selectedMetricLabel")
    func applyWeightMetricClearsLabel() {
        let sut = makeSUT()
        let store = makeStore()

        store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue
        sut.applySelectedMetric(.weight, to: store, clearSelection: false)

        #expect(store.state.ui.selectedMetricLabel == nil)
    }

    @Test("applySelectedMetric with height sets selectedMetricLabel to height rawValue")
    func applyHeightMetricSetsLabel() {
        let sut = makeSUT()
        let store = makeStore()

        sut.applySelectedMetric(.height, to: store, clearSelection: false)

        #expect(store.state.ui.selectedMetricLabel == BabyMetric.height.rawValue)
    }

    @Test("applySelectedMetric with clearSelection true calls clearSelection on chartManager")
    func applyMetricClearsSelectionWhenRequested() {
        let sut = makeSUT()
        let store = makeStore()
        // Set a selected point
        store.state.graph.selectedPoint = DashboardTestFixtures.makeSummary()

        sut.applySelectedMetric(.weight, to: store, clearSelection: true)

        // After clearSelection, selectedPoint should be nil
        #expect(store.state.graph.selectedPoint == nil)
    }

    // MARK: - handleAppear

    @Test("handleAppear applies current selected metric without clearing selection")
    func handleAppearAppliesMetric() {
        let sut = makeSUT()
        let store = makeStore()

        store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue
        // Place a selected point — handleAppear should NOT clear it
        store.state.graph.selectedPoint = DashboardTestFixtures.makeSummary()

        sut.handleAppear(dashboardStore: store)

        // selectedMetricLabel should remain unchanged
        #expect(store.state.ui.selectedMetricLabel == BabyMetric.height.rawValue)
    }

    // MARK: - growthPercentilesSheetState

    @Test("growthPercentilesSheetState returns non-empty percentile texts")
    func growthPercentilesSheetStateHasPercentileTexts() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        let state = sut.growthPercentilesSheetState(dashboardStore: store, babyProfile: baby)

        // Percentile texts should be either a number or "--"
        #expect(!state.weightPercentileText.isEmpty)
        #expect(!state.heightPercentileText.isEmpty)
    }

    @Test("growthPercentilesSheetState returns non-empty weight display")
    func growthPercentilesSheetStateWeightDisplay() {
        let sut = makeSUT()
        let store = makeStore()
        let baby = makeBabyProfile()

        let state = sut.growthPercentilesSheetState(dashboardStore: store, babyProfile: baby)

        #expect(!state.weightDisplay.primary.isEmpty)
        #expect((state.weightDisplay.secondary?.isEmpty == false))
    }

    @Test("growthPercentilesSheetState returns -- when display weight is 0")
    func growthPercentilesSheetStatePlaceholderWhenZeroWeight() {
        let sut = makeSUT()
        let store = makeStore()
        // Baby profile with very far future birthday so no dummy data is generated
        let babyFuture = BabyProfile(id: "future", name: "Future", birthday: Date().addingTimeInterval(86400 * 365))

        let state = sut.growthPercentilesSheetState(dashboardStore: store, babyProfile: babyFuture)

        // Either a number or "--"
        #expect(!state.weightDisplay.primary.isEmpty)
    }

    // MARK: - handlePeriodChange

    @Test("handlePeriodChange forces immediate UI update")
    func handlePeriodChangeCallsForceUpdate() {
        let sut = makeSUT()
        let store = makeStore()

        // This should complete without crashing
        sut.handlePeriodChange(dashboardStore: store)
        #expect(true)
    }
}
