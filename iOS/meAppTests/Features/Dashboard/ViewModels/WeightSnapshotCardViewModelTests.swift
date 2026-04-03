import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct WeightSnapshotCardViewModelTests {

    // MARK: - Helpers

    private func makeSUT(account: Account? = nil) -> (sut: WeightSnapshotCardViewModel, mockAccount: MockAccountService) {
        TestDependencyContainer.reset()
        let mockAccount = MockAccountService()
        mockAccount.activeAccount = account
        DependencyContainer.shared.register(mockAccount as AccountServiceProtocol)
        let sut = WeightSnapshotCardViewModel()
        return (sut, mockAccount)
    }

    private func makeAccount(weightUnit: WeightUnit, goalWeight: Double? = nil) -> Account {
        let account = AccountTestFixtures.makeAccountModel(id: "acct-snap", isActive: true)
        account.weightSettings = WeightCompSettings(
            accountId: "acct-snap",
            height: "70",
            activityLevel: .normal,
            weightUnit: weightUnit
        )
        if let goalWeight {
            account.goalSettings = GoalSettings(
                accountId: "acct-snap",
                goalType: .lose,
                initialWeight: 2000,
                goalWeight: goalWeight,
                goalPercent: nil,
                isSynced: false
            )
        }
        return account
    }

    // MARK: - Initial State

    @Test("activeAccount initialises from accountService.activeAccount")
    func activeAccountInitialisesFromService() {
        let account = makeAccount(weightUnit: .lb)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.activeAccount != nil)
        #expect(sut.activeAccount?.weightSettings?.weightUnit == .lb)
    }

    @Test("activeAccount is nil when no active account is set")
    func activeAccountNilWhenNoAccount() {
        let (sut, _) = makeSUT(account: nil)
        #expect(sut.activeAccount == nil)
    }

    // MARK: - activeAccount Publisher

    @Test("activeAccount updates when accountService publishes new account")
    func activeAccountUpdatesOnPublish() async {
        let (sut, mockAccount) = makeSUT(account: nil)
        let newAccount = makeAccount(weightUnit: .kg)

        mockAccount.activeAccount = newAccount

        await DashboardTestFixtures.waitUntil { sut.activeAccount != nil }
        #expect(sut.activeAccount != nil)
    }

    @Test("activeAccount clears when accountService publishes nil")
    func activeAccountClearsOnNilPublish() async {
        let account = makeAccount(weightUnit: .lb)
        let (sut, mockAccount) = makeSUT(account: account)

        mockAccount.activeAccount = nil

        await DashboardTestFixtures.waitUntil { sut.activeAccount == nil }
        #expect(sut.activeAccount == nil)
    }

    // MARK: - unitText

    @Test("unitText returns lbs when weight unit is lb")
    func unitTextReturnLbs() {
        let account = makeAccount(weightUnit: .lb)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.unitText == WeightUnit.lb.rawValue)
    }

    @Test("unitText returns kg when weight unit is kg")
    func unitTextReturnKg() {
        let account = makeAccount(weightUnit: .kg)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.unitText == WeightUnit.kg.rawValue)
    }

    @Test("unitText defaults to lbs when no account is set")
    func unitTextDefaultsToLbs() {
        let (sut, _) = makeSUT(account: nil)
        #expect(sut.unitText == "lbs")
    }

    // MARK: - convertStoredWeightToDisplay

    @Test("convertStoredWeightToDisplay converts correctly in lbs mode")
    func convertStoredWeightLbs() {
        let account = makeAccount(weightUnit: .lb)
        let (sut, _) = makeSUT(account: account)

        let stored = 1800 // 180.0 lbs
        let result = sut.convertStoredWeightToDisplay(stored)
        let expected = ConversionTools.convertStoredToLbs(stored)

        #expect(abs(result - expected) < 0.001)
    }

    @Test("convertStoredWeightToDisplay converts correctly in kg mode")
    func convertStoredWeightKg() {
        let account = makeAccount(weightUnit: .kg)
        let (sut, _) = makeSUT(account: account)

        let stored = 1800
        let result = sut.convertStoredWeightToDisplay(stored)
        let expected = ConversionTools.convertStoredToKg(stored)

        #expect(abs(result - expected) < 0.001)
    }

    @Test("convertStoredWeightToDisplay defaults to lbs when no account")
    func convertStoredWeightDefaultsToLbs() {
        let (sut, _) = makeSUT(account: nil)
        let stored = 1800
        let result = sut.convertStoredWeightToDisplay(stored)
        let expected = ConversionTools.convertStoredToLbs(stored)
        #expect(abs(result - expected) < 0.001)
    }

    @Test("convertStoredWeightToDisplay handles zero weight")
    func convertStoredWeightZero() {
        let account = makeAccount(weightUnit: .lb)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.convertStoredWeightToDisplay(0) == 0)
    }

    // MARK: - goalWeightForDisplay

    @Test("goalWeightForDisplay returns nil when no goal is set")
    func goalWeightNilWithoutGoal() {
        let account = makeAccount(weightUnit: .lb, goalWeight: nil)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.goalWeightForDisplay() == nil)
    }

    @Test("goalWeightForDisplay returns nil when no account is set")
    func goalWeightNilWithoutAccount() {
        let (sut, _) = makeSUT(account: nil)
        #expect(sut.goalWeightForDisplay() == nil)
    }

    @Test("goalWeightForDisplay converts goal weight to lbs")
    func goalWeightConvertsToLbs() {
        let storedGoal: Double = 1600 // 160 lbs
        let account = makeAccount(weightUnit: .lb, goalWeight: storedGoal)
        let (sut, _) = makeSUT(account: account)

        let result = sut.goalWeightForDisplay()
        let expected = ConversionTools.convertStoredToLbs(Int(storedGoal))

        #expect(result != nil)
        #expect(abs(result! - expected) < 0.001)
    }

    @Test("goalWeightForDisplay converts goal weight to kg")
    func goalWeightConvertsToKg() {
        let storedGoal: Double = 1600
        let account = makeAccount(weightUnit: .kg, goalWeight: storedGoal)
        let (sut, _) = makeSUT(account: account)

        let result = sut.goalWeightForDisplay()
        let expected = ConversionTools.convertStoredToKg(Int(storedGoal))

        #expect(result != nil)
        #expect(abs(result! - expected) < 0.001)
    }
}
