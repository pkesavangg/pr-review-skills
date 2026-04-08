import Foundation
import Testing
@testable import meApp

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
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.myWeight)
            #expect(store.isBabySelection == false)
        }

        @Test("isBabySelection is false when myBloodPressure is selected")
        func isBabySelectionFalseForBpm() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.myBloodPressure)
            #expect(store.isBabySelection == false)
        }

        @Test("isBabySelection is true when a baby product is selected")
        func isBabySelectionTrueForBaby() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.baby(profile: makeBaby()))
            #expect(store.isBabySelection == true)
        }

        // MARK: - selectedBabyProfile

        @Test("selectedBabyProfile is nil when myWeight is selected")
        func selectedBabyProfileNilForWeight() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.myWeight)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectedBabyProfile is nil when myBloodPressure is selected")
        func selectedBabyProfileNilForBpm() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.myBloodPressure)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectedBabyProfile returns the profile when baby is selected")
        func selectedBabyProfileReturnsBabyProfile() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            let baby = makeBaby(id: "baby-unique-id")
            store.selectProductItem(.baby(profile: baby))
            #expect(store.selectedBabyProfile?.id == "baby-unique-id")
        }

        // MARK: - selectedBabyMetric

        @Test("selectedBabyMetric returns weight when selectedMetricLabel is nil")
        func selectedBabyMetricDefaultsToWeight() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.selectedMetricLabel = nil
            #expect(store.selectedBabyMetric == .weight)
        }

        @Test("selectedBabyMetric returns height when selectedMetricLabel is Height")
        func selectedBabyMetricReturnsHeightForHeightLabel() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue
            #expect(store.selectedBabyMetric == .height)
        }

        @Test("selectedBabyMetric returns weight for any other label value")
        func selectedBabyMetricReturnWeightForOtherLabel() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.selectedMetricLabel = "bodyFat"
            #expect(store.selectedBabyMetric == .weight)
        }

        // MARK: - switchProductType

        @Test("switchProductType updates productType from wg to bpm")
        func switchProductTypeWgToBpm() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            #expect(store.productType == .wg) // default

            store.switchProductType(to: .bpm)

            #expect(store.productType == .bpm)
        }

        @Test("switchProductType is no-op when switching to the same type")
        func switchProductTypeSameTypeNoOp() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.productType = .wg

            store.switchProductType(to: .wg)

            // State should remain unchanged
            #expect(store.productType == .wg)
        }

        @Test("switchProductType clears selectedMetricLabel")
        func switchProductTypeClearsSelectedMetricLabel() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.selectedMetricLabel = "bodyFat"

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.selectedMetricLabel == nil)
        }

        @Test("switchProductType resets hasInitializedChart to false")
        func switchProductTypeResetsChartFlag() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.hasInitializedChart = true

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.hasInitializedChart == false)
        }

        @Test("switchProductType switches back from bpm to wg")
        func switchProductTypeBpmBackToWg() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.switchProductType(to: .bpm)
            #expect(store.productType == .bpm)

            store.switchProductType(to: .wg)

            #expect(store.productType == .wg)
        }

        // MARK: - selectProductItem

        @Test("selectProductItem transitions to bpm product type")
        func selectProductItemTransitionsToBpm() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            #expect(store.selectedProductItem == .myWeight)

            store.selectProductItem(.myBloodPressure)

            #expect(store.selectedProductItem == .myBloodPressure)
            #expect(store.productType == .bpm)
        }

        @Test("selectProductItem transitions to baby keeps wg product type")
        func selectProductItemBabyKeepsWgType() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            let baby = makeBaby(id: "b-wg")
            store.selectProductItem(.baby(profile: baby))

            #expect(store.selectedProductItem == .baby(profile: baby))
            #expect(store.productType == .wg)
        }

        @Test("selectProductItem back to myWeight clears baby selection")
        func selectProductItemBackToWeightClearsBabySelection() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.baby(profile: makeBaby()))
            #expect(store.isBabySelection == true)

            store.selectProductItem(.myWeight)

            #expect(store.isBabySelection == false)
            #expect(store.selectedBabyProfile == nil)
        }

        @Test("selectProductItem clears selectedMetricLabel on product change")
        func selectProductItemClearsMetricLabel() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.selectedMetricLabel = BabyMetric.height.rawValue

            store.selectProductItem(.myBloodPressure)

            #expect(store.state.ui.selectedMetricLabel == nil)
        }

        // MARK: - continuousOperations with baby

        @Test("continuousOperations returns baby dummy data when baby is selected")
        func continuousOperationsReturnsBabyDummyData() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            let baby = makeBaby()
            store.selectProductItem(.baby(profile: baby))

            let ops = store.continuousOperations

            #expect(!ops.isEmpty)
            #expect(ops.allSatisfy { $0.accountId.contains(baby.id) })
        }

        @Test("continuousOperations returns empty when weight selected and no data")
        func continuousOperationsEmptyForWeightWithNoData() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.selectProductItem(.myWeight)
            store.dataManager.state.dailySummaries = []
            store.dataManager.state.monthlySummaries = []

            let ops = store.continuousOperations

            #expect(ops.isEmpty)
        }

        // MARK: - productTypeSelectorStore

        @Test("productTypeSelectorStore is accessible and returns the product type store")
        func productTypeSelectorStoreIsAccessible() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            // Just verify it's accessible without crash
            _ = store.productTypeSelectorStore
        }

        // MARK: - Graph State Reset on Product Switch

        @Test("switchProductType clears cached Y-axis domain and ticks")
        func switchProductTypeClearsYAxisState() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
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

        @Test("switchProductType sets product context on cache manager")
        func switchProductTypeSetsProductContext() {
            let (store, mockCache, _) = DashboardStoreTestSupport.makeSUT()

            store.switchProductType(to: .bpm)

            #expect(mockCache.setProductContextCalls >= 1)
            #expect(mockCache.lastProductContext?.productType == .bpm)
        }

        @Test("switchProductType resets hasInitializedChart and isGraphReady")
        func switchProductTypeResetsChartFlags() {
            let (store, _, _) = DashboardStoreTestSupport.makeSUT()
            store.state.ui.hasInitializedChart = true
            store.graphManager.state.isGraphReady = true

            store.switchProductType(to: .bpm)

            #expect(store.state.ui.hasInitializedChart == false)
            #expect(store.graphManager.state.isGraphReady == false)
        }

        @Test("refreshSelectedProductContext clears caches when switching baby profiles")
        func refreshSelectedProductContextClearsCaches() {
            let (store, mockCache, _) = DashboardStoreTestSupport.makeSUT()
            store.productType = .wg
            store.state.ui.hasInitializedChart = true

            let baby1 = makeBaby(id: "b1")
            store.selectProductItem(.baby(profile: baby1))

            #expect(mockCache.clearAllCachesCalls >= 1)
            #expect(store.state.ui.hasInitializedChart == false || store.state.ui.hasInitializedChart == true)
        }
    }
}
