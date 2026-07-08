//
//  CompleteProfileSetupStoreTests.swift
//  meAppTests
//
//  Covers the shared Complete Profile Setup store (MOB-1388): the skip guard, prefill
//  defaults, field updates, and the save / skip behaviour. This store backs the
//  `.completeProfile` step across the A6 and Bluetooth scale-setup flows.
//

import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct CompleteProfileSetupStoreTests {
    private struct Harness {
        let store: CompleteProfileSetupStore
        let account: MockAccountService
        let notification: TestNotificationHelperService
    }

    private func makeHarness(gender: Sex?, weightHeight: String) -> Harness {
        TestDependencyContainer.reset()

        let account = MockAccountService()
        let notification = TestNotificationHelperService()
        let snapshot = AccountTestFixtures.makeAccountSnapshot(
            gender: gender,
            isActiveAccount: true,
            weightHeight: weightHeight
        )
        account.seedAccounts([snapshot], active: snapshot)
        account.updateProfileResult = .success(())
        account.updateBodyCompResult = .success(())
        account.createGoalResult = .success(())

        DependencyContainer.shared.register(account as AccountServiceProtocol)
        DependencyContainer.shared.register(notification as NotificationHelperServiceProtocol)

        return Harness(store: CompleteProfileSetupStore(), account: account, notification: notification)
    }

    @MainActor
    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: 20_000_000)
        }
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

    // MARK: - Field updates

    @Test("updateProfileGender changes the selection")
    func updateGender_changesSelection() {
        let store = makeHarness(gender: .male, weightHeight: "170").store
        store.updateProfileGender(.female)
        #expect(store.profileGender == .female)
        #expect(store.profileGenderText == "Female")
    }

    // MARK: - Save / Skip

    @Test("save persists profile and body composition, then completes")
    func save_persistsProfileAndBodyComp() async {
        let harness = makeHarness(gender: nil, weightHeight: "0")
        harness.store.prefillCompleteProfile()

        var didComplete = false
        harness.store.saveCompleteProfile { didComplete = true }

        await waitUntil { didComplete }
        #expect(didComplete)
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

        var didComplete = false
        harness.store.saveCompleteProfile { didComplete = true }

        await waitUntil { didComplete }
        #expect(harness.account.createGoalCalls == 1)
        #expect(harness.account.updateProfileCalls == 1)
    }

    @Test("save still completes when persistence fails")
    func save_completes_evenWhenPersistenceFails() async {
        let harness = makeHarness(gender: nil, weightHeight: "0")
        harness.account.updateProfileResult = .failure(UnexpectedCallError.methodCalled("updateProfile"))
        harness.store.prefillCompleteProfile()

        var didComplete = false
        harness.store.saveCompleteProfile { didComplete = true }

        await waitUntil { didComplete }
        #expect(didComplete)
        #expect(harness.account.updateProfileCalls == 1)
    }

    @Test("skip does not persist anything")
    func skip_doesNotPersist() {
        let harness = makeHarness(gender: nil, weightHeight: "0")
        var didComplete = false
        harness.store.skipCompleteProfile { didComplete = true }
        #expect(didComplete)
        #expect(harness.account.updateProfileCalls == 0)
        #expect(harness.account.updateBodyCompCalls == 0)
        #expect(harness.account.createGoalCalls == 0)
    }
}
