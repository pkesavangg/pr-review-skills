//
//  BabyMeasurementUnitsTests.swift
//  meAppTests
//
//  Baby surfaces must follow the account's "My Kids" `measurementUnits`, never the adult
//  "My Weight" `weightUnit` — every fixture here sets the two to CONFLICTING units so a
//  passing assertion can only come from reading the kids' units.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BabyMeasurementUnitsTests {

    private static let babySelection = ProductSelection.baby(profile: BabyProfile(id: "baby-1", name: "Aria"))

    // MARK: - MeasurementUnits → unit mapping

    @Test("MeasurementUnits.weightUnit maps metric to kg and both imperial forms to lb")
    func measurementUnitsWeightUnitMapping() {
        #expect(MeasurementUnits.metric.weightUnit == .kg)
        #expect(MeasurementUnits.imperialLbOz.weightUnit == .lb)
        #expect(MeasurementUnits.imperialLbDecimal.weightUnit == .lb)
    }

    @Test("BabyWeightUnit(MeasurementUnits) maps each unit to its entry-field form")
    func babyWeightUnitMapping() {
        #expect(BabyWeightUnit(.metric) == .kg)
        #expect(BabyWeightUnit(.imperialLbDecimal) == .lb)
        #expect(BabyWeightUnit(.imperialLbOz) == .lbsOz)
    }

    @Test("babyMeasurementUnits parses the account value and defaults to lb/oz")
    func babyMeasurementUnitsParsing() {
        let metric = DashboardStoreTestSupport.makeActiveAccount(measurementUnits: .metric)
        #expect(metric.babyMeasurementUnits == .metric)

        let unset = DashboardStoreTestSupport.makeActiveAccount(measurementUnits: nil)
        #expect(unset.babyMeasurementUnits == .imperialLbOz)
    }

    // MARK: - Dashboard display conversion

    @Test("Goal manager converts baby values with the kids' unit while a baby is selected")
    func goalManagerUsesKidsUnitForBaby() {
        let bundle = makeGoalManager(weightUnit: .lb, measurementUnits: .metric)
        bundle.productTypeStore.selectedItem = Self.babySelection

        #expect(bundle.sut.displayWeightUnit == .kg)
        #expect(abs(bundle.sut.convertWeightToDisplay(1200) - ConversionTools.convertStoredToKg(1200)) < 0.001)
    }

    @Test("Goal manager converts with the adult unit for the weight product")
    func goalManagerUsesAdultUnitForWeight() {
        let bundle = makeGoalManager(weightUnit: .lb, measurementUnits: .metric)
        bundle.productTypeStore.selectedItem = .myWeight

        #expect(bundle.sut.displayWeightUnit == .lb)
        #expect(abs(bundle.sut.convertWeightToDisplay(1200) - ConversionTools.convertStoredToLbs(1200)) < 0.001)
    }

    @Test("Goal maths stays on the adult unit even while a baby is selected")
    func goalMathsStaysAdult() {
        let bundle = makeGoalManager(weightUnit: .lb, measurementUnits: .metric)
        bundle.productTypeStore.selectedItem = Self.babySelection

        #expect(abs(bundle.sut.convertStoredWeightToDisplay(1200) - ConversionTools.convertStoredToLbs(1200)) < 0.001)
    }

    // MARK: - Store unit + refresh trigger

    @Test("DashboardStore.currentUnit follows the kids' unit for a baby selection")
    func storeCurrentUnitFollowsKidsUnit() {
        let sut = DashboardStoreTestSupport.makeSUT(
            initialAccount: DashboardStoreTestSupport.makeActiveAccount(weightUnit: .lb, measurementUnits: .metric)
        )
        #expect(sut.store.currentUnit == .lb) // weight product → adult unit

        sut.store.selectProductItem(Self.babySelection)
        #expect(sut.store.currentUnit == .kg)
        #expect(sut.store.currentMeasurementUnits == .metric)
    }

    @Test("A kids' unit change makes a new AccountSettingsSnapshot, so the dashboard refreshes")
    func settingsSnapshotTracksMeasurementUnits() {
        let lbOz = AccountSettingsSnapshot(
            from: DashboardStoreTestSupport.makeActiveAccount(weightUnit: .lb, measurementUnits: .imperialLbOz)
        )
        let metric = AccountSettingsSnapshot(
            from: DashboardStoreTestSupport.makeActiveAccount(weightUnit: .lb, measurementUnits: .metric)
        )
        #expect(lbOz != metric)
    }

    // MARK: - Snapshot card

    @Test("Baby snapshot card converts and labels with the kids' unit, not the adult one")
    func snapshotCardUsesKidsUnit() {
        TestDependencyContainer.reset()
        let mockAccount = MockAccountService()
        // Adult kg vs kids lb/oz — the card must render lb.
        mockAccount.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "acct-baby",
            isActiveAccount: true,
            measurementUnits: MeasurementUnits.imperialLbOz.rawValue,
            weightUnit: .kg
        )
        DependencyContainer.shared.register(mockAccount as AccountServiceProtocol)

        let sut = BabySnapshotCardViewModel()
        #expect(sut.weightUnit == .lb)
        #expect(sut.unitText == WeightUnit.lb.rawValue)
        #expect(abs(sut.convertStoredWeightToDisplay(80) - ConversionTools.convertStoredToLbs(80)) < 0.001)
    }

    // MARK: - Helpers

    private struct GoalManagerBundle {
        let sut: DashboardGoalManager
        let productTypeStore: MockProductTypeStore
    }

    private func makeGoalManager(
        weightUnit: WeightUnit,
        measurementUnits: MeasurementUnits
    ) -> GoalManagerBundle {
        TestDependencyContainer.reset()

        let accountService = AccountService(
            apiRepo: MockAccountAPIRepository(),
            localRepo: MockAccountRepository(),
            integrationApiRepo: MockIntegrationAPIRepository(),
            networkMonitor: MockNetworkMonitor(isConnected: true),
            performInitialLoad: false
        )
        accountService.activeAccount = DashboardStoreTestSupport.makeActiveAccount(
            weightUnit: weightUnit,
            measurementUnits: measurementUnits
        )
        DependencyContainer.shared.register(accountService as AccountService)
        DependencyContainer.shared.register(LoggerService() as LoggerService)
        DependencyContainer.shared.register(
            EntryService(
                accountService: accountService,
                localRepo: MockEntryRepository(),
                localKVRepo: MockEntrySyncStore(),
                remoteRepo: MockEntryRepositoryAPI()
            ) as EntryService
        )

        // Register the product-type mock LAST: seeding `activeAccount` above fires AccountService's
        // sink → ServiceRegistry.registerSessionServices(), which re-registers the real
        // ProductTypeStore.shared over anything registered earlier. Stamp the protocol keys directly
        // because `register(x as SomeProtocol)` opens the existential and keys it by concrete type.
        let productTypeStore = MockProductTypeStore()
        DependencyContainer.shared.dependencies = DependencyContainer.shared.dependencies.filter {
            !($0.value is ProductTypeStoreProtocol)
        }
        DependencyContainer.shared.register(productTypeStore)
        DependencyContainer.shared.dependencies["ProductTypeStoreProtocol"] = productTypeStore
        DependencyContainer.shared.dependencies["meApp.ProductTypeStoreProtocol"] = productTypeStore

        return GoalManagerBundle(sut: DashboardGoalManager(), productTypeStore: productTypeStore)
    }
}
