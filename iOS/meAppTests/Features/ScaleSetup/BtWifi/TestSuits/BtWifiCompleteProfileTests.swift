//
//  BtWifiCompleteProfileTests.swift
//  meAppTests
//
//  Covers the Complete Profile Setup step (MOB-1388): the skip guard, prefill
//  defaults, and the save / skip behaviour.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct BtWifiCompleteProfileTests {
    private func makeHarness(gender: Sex?, weightHeight: String) -> BtWifiStoreTestHarness {
        let account = MockAccountService()
        let snapshot = AccountTestFixtures.makeAccountSnapshot(
            gender: gender,
            isActiveAccount: true,
            weightHeight: weightHeight
        )
        account.seedAccounts([snapshot], active: snapshot)
        // MockAccountService's persistence stubs default to `.failure`; stub success so the
        // save path runs updateProfile → updateBodyComp → createGoal without throwing early.
        account.updateProfileResult = .success(())
        account.updateBodyCompResult = .success(())
        account.createGoalResult = .success(())
        return BtWifiStoreTestFixtures.makeSUT(account: account)
    }

    // MARK: - Skip guard

    @Test("isProfileComplete true when gender and height present")
    func isProfileComplete_true_whenComplete() {
        let store = makeHarness(gender: .male, weightHeight: "170").store
        #expect(store.isProfileComplete())
    }

    @Test("isProfileComplete false when gender missing")
    func isProfileComplete_false_whenGenderMissing() {
        let store = makeHarness(gender: nil, weightHeight: "170").store
        #expect(!store.isProfileComplete())
    }

    @Test("isProfileComplete false when height missing")
    func isProfileComplete_false_whenHeightMissing() {
        let store = makeHarness(gender: .male, weightHeight: "0").store
        #expect(!store.isProfileComplete())
    }

    // MARK: - Prefill

    @Test("prefill uses account values when present")
    func prefill_usesAccountValues() {
        let store = makeHarness(gender: .female, weightHeight: "681").store
        store.prefillCompleteProfile()
        #expect(store.profileGender == .female)
        #expect(store.profileHeightStored == "681")
    }

    @Test("prefill falls back to defaults when profile incomplete")
    func prefill_fallsBackToDefaults() {
        let store = makeHarness(gender: nil, weightHeight: "0").store
        store.prefillCompleteProfile()
        #expect(store.profileGender == .male)
        // 770 == 6'5" — the default height carried from the signup form.
        #expect(store.profileHeightStored == "770")
    }

    // MARK: - Save / Skip

    @Test("save persists profile and body composition")
    func save_persistsProfileAndBodyComp() async {
        let harness = makeHarness(gender: nil, weightHeight: "0")
        harness.store.prefillCompleteProfile()
        harness.store.saveCompleteProfileAndProceed()

        await BtWifiStoreTestFixtures.waitUntil { harness.account.updateProfileCalls == 1 }
        #expect(harness.account.updateProfileCalls == 1)
        #expect(harness.account.updateBodyCompCalls == 1)
        #expect(harness.account.createGoalCalls == 0)
        #expect(harness.account.lastUpdatedProfile?.gender == .male)
    }

    @Test("save creates a goal only when a goal weight is entered")
    func save_createsGoal_whenGoalWeightProvided() async {
        let harness = makeHarness(gender: .male, weightHeight: "170")
        harness.store.prefillCompleteProfile()
        harness.store.profileGoalSegment = .maintain
        harness.store.profileGoalWeight = "150"
        harness.store.saveCompleteProfileAndProceed()

        await BtWifiStoreTestFixtures.waitUntil { harness.account.createGoalCalls == 1 }
        #expect(harness.account.createGoalCalls == 1)
        #expect(harness.account.updateProfileCalls == 1)
    }

    @Test("skip does not persist anything")
    func skip_doesNotPersist() {
        let harness = makeHarness(gender: nil, weightHeight: "0")
        harness.store.handleSkipCompleteProfile()
        #expect(harness.account.updateProfileCalls == 0)
        #expect(harness.account.updateBodyCompCalls == 0)
        #expect(harness.account.createGoalCalls == 0)
    }
}
