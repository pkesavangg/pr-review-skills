import Combine
import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BabySnapshotCardViewModelTests {

    // MARK: - Helpers

    private func makeSUT(account: AccountSnapshot? = nil) -> (sut: BabySnapshotCardViewModel, mockAccount: MockAccountService) {
        TestDependencyContainer.reset()
        let mockAccount = MockAccountService()
        mockAccount.activeAccount = account
        DependencyContainer.shared.register(mockAccount as AccountServiceProtocol)
        let sut = BabySnapshotCardViewModel()
        return (sut, mockAccount)
    }

    private func makeAccount(weightUnit: WeightUnit) -> AccountSnapshot {
        AccountTestFixtures.makeAccountSnapshot(
            id: "acct-baby",
            isActiveAccount: true,
            weightUnit: weightUnit,
            weightHeight: "70",
            activityLevel: .normal
        )
    }

    // MARK: - Initial State

    @Test("activeAccount initialises from accountService")
    func activeAccountInitialisedFromService() {
        let account = makeAccount(weightUnit: .lb)
        let (sut, _) = makeSUT(account: account)
        #expect(sut.activeAccount != nil)
    }

    @Test("activeAccount is nil when no active account")
    func activeAccountNilByDefault() {
        let (sut, _) = makeSUT(account: nil)
        #expect(sut.activeAccount == nil)
    }

    // MARK: - Publisher Binding

    @Test("activeAccount updates when accountService publishes new account")
    func activeAccountUpdatesOnPublish() async {
        let (sut, mockAccount) = makeSUT(account: nil)
        mockAccount.activeAccount = makeAccount(weightUnit: .kg)
        await DashboardTestFixtures.waitUntil { sut.activeAccount != nil }
        #expect(sut.activeAccount != nil)
    }

    @Test("activeAccount clears when accountService publishes nil")
    func activeAccountClearsOnNil() async {
        let (sut, mockAccount) = makeSUT(account: makeAccount(weightUnit: .lb))
        mockAccount.activeAccount = nil
        await DashboardTestFixtures.waitUntil { sut.activeAccount == nil }
        #expect(sut.activeAccount == nil)
    }

    // MARK: - unitText

    @Test("unitText returns lbs when unit is lb")
    func unitTextLbs() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        #expect(sut.unitText == WeightUnit.lb.rawValue)
    }

    @Test("unitText returns kg when unit is kg")
    func unitTextKg() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .kg))
        #expect(sut.unitText == WeightUnit.kg.rawValue)
    }

    @Test("unitText defaults to lbs when no account is set")
    func unitTextDefaultsWhenNoAccount() {
        let (sut, _) = makeSUT(account: nil)
        #expect(sut.unitText == "lbs")
    }

    // MARK: - convertStoredWeightToDisplay

    @Test("convertStoredWeightToDisplay uses lb conversion when unit is lb")
    func convertStoredWeightLbs() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        let stored = 1200
        let result = sut.convertStoredWeightToDisplay(stored)
        #expect(abs(result - ConversionTools.convertStoredToLbs(stored)) < 0.001)
    }

    @Test("convertStoredWeightToDisplay uses kg conversion when unit is kg")
    func convertStoredWeightKg() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .kg))
        let stored = 1200
        let result = sut.convertStoredWeightToDisplay(stored)
        #expect(abs(result - ConversionTools.convertStoredToKg(stored)) < 0.001)
    }

    @Test("convertStoredWeightToDisplay defaults to lbs when no account")
    func convertStoredWeightDefaultsToLbs() {
        let (sut, _) = makeSUT(account: nil)
        let stored = 1200
        #expect(abs(sut.convertStoredWeightToDisplay(stored) - ConversionTools.convertStoredToLbs(stored)) < 0.001)
    }

    // MARK: - convertDecigramsToDisplay

    @Test("convertDecigramsToDisplay converts through kg factor then to lbs")
    func convertDecigramsToLbs() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        let decigrams = 35000 // ~3.5 kg
        let result = sut.convertDecigramsToDisplay(decigrams)
        // Result should be a positive lbs value
        #expect(result > 0)
    }

    @Test("convertDecigramsToDisplay keeps kg units when account is metric")
    func convertDecigramsToKg() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .kg))
        let decigrams = 35000
        let result = sut.convertDecigramsToDisplay(decigrams)
        #expect(result > 0)
        // In kg it should be less than the lbs value
        let lbsSUT = makeSUT(account: makeAccount(weightUnit: .lb)).sut
        let lbsResult = lbsSUT.convertDecigramsToDisplay(decigrams)
        #expect(result < lbsResult)
    }

    @Test("convertDecigramsToDisplay returns 0 for 0 decigrams")
    func convertDecigramsZero() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        #expect(sut.convertDecigramsToDisplay(0) == 0)
    }

    // MARK: - formatBabyWeight

    @Test("formatBabyWeight splits into whole lbs and fractional ounces")
    func formatBabyWeightSplitsCorrectly() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        // stored = 80 → 8.0 lbs → 8 lbs 0.0 oz
        let result = sut.formatBabyWeight(80)
        #expect(result.lbs == "8")
        #expect(result.oz == "0.0")
    }

    @Test("formatBabyWeight handles fractional pounds as ounces")
    func formatBabyWeightFractionalOz() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        // 75 stored = 7.5 lbs = 7 lbs 8.0 oz
        let result = sut.formatBabyWeight(75)
        #expect(result.lbs == "7")
        #expect(result.oz == "8.0")
    }

    // MARK: - weekAverageLbsOz

    @Test("weekAverageLbsOz returns nil for empty summaries")
    func weekAverageNilForEmpty() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        #expect(sut.weekAverageLbsOz(from: []) == nil)
    }

    @Test("weekAverageLbsOz ignores zero-weight summaries")
    func weekAverageIgnoresZeroWeights() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        let summaries = [
            DashboardTestFixtures.makeSummary(weight: 0),
            DashboardTestFixtures.makeSummary(weight: 0)
        ]
        #expect(sut.weekAverageLbsOz(from: summaries) == nil)
    }

    @Test("weekAverageLbsOz averages valid weights and formats them")
    func weekAverageComputesCorrectly() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        // Two entries of 80 stored = 8.0 lbs each → average = 8.0 lbs
        let summaries = [
            DashboardTestFixtures.makeSummary(weight: 80),
            DashboardTestFixtures.makeSummary(weight: 80)
        ]
        let result = sut.weekAverageLbsOz(from: summaries)
        #expect(result != nil)
        #expect(result?.lbs == "8")
    }

    @Test("weekAverageLbsOz returns single value when one non-zero summary")
    func weekAverageSingleEntry() {
        let (sut, _) = makeSUT(account: makeAccount(weightUnit: .lb))
        let summaries = [DashboardTestFixtures.makeSummary(weight: 75)]
        let result = sut.weekAverageLbsOz(from: summaries)
        #expect(result != nil)
        #expect(result?.lbs == "7")
        #expect(result?.oz == "8.0")
    }
}
