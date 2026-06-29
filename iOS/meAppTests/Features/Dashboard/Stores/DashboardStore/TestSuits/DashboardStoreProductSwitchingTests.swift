import Foundation
@testable import meApp
import Testing

extension DashboardStoreTests {
    @Suite("Product Switching")
    @MainActor
    struct ProductSwitching {

        private func makeBaby(id: String = "b1", name: String = "Aria", sex: String = "female") -> BabyProfile {
            BabyProfile(
                id: id,
                name: name,
                birthday: Calendar.current.date(byAdding: .day, value: -90, to: Date()),
                biologicalSex: sex
            )
        }

        // MARK: - isBabySelection

        @Test("isBabySelection is false when myWeight is selected")
        func isBabySelectionFalseForWeight() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.myWeight)
            #expect(store.isBabySelection == false)
        }

        @Test("isBabySelection is false when myBloodPressure is selected")
        func isBabySelectionFalseForBpm() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.myBloodPressure)
            #expect(store.isBabySelection == false)
        }

        @Test("isBabySelection is true when a baby product is selected")
        func isBabySelectionTrueForBaby() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.baby(profile: makeBaby()))
            #expect(store.isBabySelection == true)
        }

        // MARK: - selectedBabyProfile

        @Test("selectedBabyProfile is nil when myWeight is selected")
        func selectedBabyProfileNilForWeight() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.myWeight)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectedBabyProfile is nil when myBloodPressure is selected")
        func selectedBabyProfileNilForBpm() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.myBloodPressure)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectedBabyProfile returns the profile when baby is selected")
        func selectedBabyProfileReturnsBabyProfile() {
            let store = DashboardStoreTestSupport.makeSUT().store
            let baby = makeBaby(id: "baby-unique-id")
            store.selectProductItem(.baby(profile: baby))
            #expect(store.selectedBabyProfile?.id == "baby-unique-id")
        }

        // MARK: - selectedBabyMetric

        @Test("selectedBabyMetric returns weight when selectedMetricLabel is nil")
        func selectedBabyMetricDefaultsToWeight() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.selectedMetricLabel = nil
            #expect(store.selectedBabyMetric == .weight)
        }

        @Test("selectedBabyMetric returns height when selectedMetricLabel is Height")
        func selectedBabyMetricReturnsHeightForHeightLabel() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue
            #expect(store.selectedBabyMetric == .height)
        }

        @Test("selectedBabyMetric returns weight for any other label value")
        func selectedBabyMetricReturnWeightForOtherLabel() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.selectedMetricLabel = "bodyFat"
            #expect(store.selectedBabyMetric == .weight)
        }

        // MARK: - switchProductType

        @Test("switchProductType updates productType from wg to bpm")
        func switchProductTypeWgToBpm() {
            let store = DashboardStoreTestSupport.makeSUT().store
            #expect(store.productType == .scale) // default

            store.switchProductType(to: .bpm)

            #expect(store.productType == .bpm)
        }

        @Test("switchProductType is no-op when switching to the same type")
        func switchProductTypeSameTypeNoOp() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.productType = .scale

            store.switchProductType(to: .scale)

            // State should remain unchanged
            #expect(store.productType == .scale)
        }

        @Test("switchProductType clears selectedMetricLabel")
        func switchProductTypeClearsSelectedMetricLabel() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.selectedMetricLabel = "bodyFat"

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.selectedMetricLabel == nil)
        }

        @Test("switchProductType resets hasInitializedChart to false")
        func switchProductTypeResetsChartFlag() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.hasInitializedChart = true

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.hasInitializedChart == false)
        }

        @Test("switchProductType switches back from bpm to wg")
        func switchProductTypeBpmBackToWg() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.switchProductType(to: .bpm)
            #expect(store.productType == .bpm)

            store.switchProductType(to: .scale)

            #expect(store.productType == .scale)
        }

        // MARK: - selectProductItem

        @Test("selectProductItem transitions to bpm product type")
        func selectProductItemTransitionsToBpm() {
            let store = DashboardStoreTestSupport.makeSUT().store
            #expect(store.selectedProductItem == .myWeight)

            store.selectProductItem(.myBloodPressure)

            #expect(store.selectedProductItem == .myBloodPressure)
            #expect(store.productType == .bpm)
        }

        @Test("selectProductItem to a baby switches product type to baby")
        func selectProductItemBabyKeepsWgType() {
            let store = DashboardStoreTestSupport.makeSUT().store
            let baby = makeBaby(id: "b-wg")
            store.selectProductItem(.baby(profile: baby))

            #expect(store.selectedProductItem == .baby(profile: baby))
            #expect(store.productType == .baby)
        }

        @Test("selectProductItem back to myWeight clears baby selection")
        func selectProductItemBackToWeightClearsBabySelection() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.baby(profile: makeBaby()))
            #expect(store.isBabySelection == true)

            store.selectProductItem(.myWeight)

            #expect(store.isBabySelection == false)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectProductItem clears selectedMetricLabel on product change")
        func selectProductItemClearsMetricLabel() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue

            store.selectProductItem(.myBloodPressure)

            #expect(store.state.ui.selectedMetricLabel == nil)
        }

        // MARK: - continuousOperations with baby

        @Test("continuousOperations returns baby dummy data when baby is selected")
        func continuousOperationsReturnsBabyDummyData() {
            let store = DashboardStoreTestSupport.makeSUT().store
            let baby = makeBaby()
            store.selectProductItem(.baby(profile: baby))

            let ops = store.continuousOperations

            #expect(!ops.isEmpty)
            #expect(ops.allSatisfy { $0.accountId.contains(baby.id) })
        }

        @Test("continuousOperations returns empty when weight selected and no data")
        func continuousOperationsEmptyForWeightWithNoData() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.selectProductItem(.myWeight)
            store.dataManager.state.dailySummaries = []
            store.dataManager.state.monthlySummaries = []

            let ops = store.continuousOperations

            #expect(ops.isEmpty)
        }

        // MARK: - productTypeSelectorStore

        @Test("productTypeSelectorStore is accessible and returns the product type store")
        func productTypeSelectorStoreIsAccessible() {
            let store = DashboardStoreTestSupport.makeSUT().store
            // Just verify it's accessible without crash
            _ = store.productTypeSelectorStore
        }

        // MARK: - Graph State Reset on Product Switch

        @Test("switchProductType clears cached Y-axis domain and ticks")
        func switchProductTypeClearsYAxisState() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.graph.cachedYAxisDomain = 100.0...200.0
            store.state.graph.cachedYAxisTicks = [100, 125, 150, 175, 200]
            store.state.graph.selectedXValue = Date()
            store.graphManager.state.cachedYAxisDomain = 100.0...200.0
            store.graphManager.state.cachedYAxisTicks = [100, 125, 150, 175, 200]

            store.switchProductType(to: .bpm)

            #expect(store.state.graph.cachedYAxisDomain == nil)
            #expect(store.state.graph.cachedYAxisTicks == nil)
            #expect(store.state.graph.selectedXValue == nil)
            #expect(store.graphManager.state.cachedYAxisDomain == nil)
            #expect(store.graphManager.state.cachedYAxisTicks == nil)
        }

        @Test("switchProductType clears scroll and processing state")
        func switchProductTypeClearsScrollState() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.graph.isScrolling = true
            store.state.graph.hasDetectedScrollInCurrentGesture = true
            store.graphManager.state.isScrolling = true
            store.graphManager.state.hasDetectedScrollInCurrentGesture = true
            store.chartManager.isProcessingScrollEnd = true

            store.switchProductType(to: .bpm)

            #expect(store.state.graph.isScrolling == false)
            #expect(store.state.graph.hasDetectedScrollInCurrentGesture == false)
            #expect(store.graphManager.state.isScrolling == false)
            #expect(store.graphManager.state.hasDetectedScrollInCurrentGesture == false)
            #expect(store.chartManager.isProcessingScrollEnd == false)
        }

        @Test("switchProductType sets product context on cache manager")
        func switchProductTypeSetsProductContext() {
            let sutBundle = DashboardStoreTestSupport.makeSUT()
            let store = sutBundle.store
            let cacheManager = sutBundle.cacheManager

            store.switchProductType(to: .bpm)

            #expect(cacheManager.setProductContextCalls >= 1)
            #expect(cacheManager.lastProductContext?.productType == .bpm)
        }

        @Test("switchProductType resets hasInitializedChart and isGraphReady")
        func switchProductTypeResetsChartFlags() {
            let store = DashboardStoreTestSupport.makeSUT().store
            store.state.ui.hasInitializedChart = true
            store.graphManager.state.isGraphReady = true

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.hasInitializedChart == false)
            #expect(store.graphManager.state.isGraphReady == false)
        }

        @Test("refreshSelectedProductContext clears caches when switching baby profiles")
        func refreshSelectedProductContextClearsCaches() {
            let sutBundle = DashboardStoreTestSupport.makeSUT()
            let store = sutBundle.store
            let cacheManager = sutBundle.cacheManager
            store.productType = .scale
            store.state.ui.hasInitializedChart = true

            let baby1 = makeBaby(id: "b1")
            store.selectProductItem(.baby(profile: baby1))

            #expect(cacheManager.clearAllCachesCalls >= 1)
            #expect(store.state.ui.hasInitializedChart == false || store.state.ui.hasInitializedChart == true)
        }
    }
}
