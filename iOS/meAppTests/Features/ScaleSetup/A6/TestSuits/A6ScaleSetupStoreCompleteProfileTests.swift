//
//  A6ScaleSetupStoreCompleteProfileTests.swift
//  meAppTests
//
//  Covers the Complete Profile Setup step in the A6 (LCBT) flow (MOB-1388): it is shown
//  before pairing when the profile is incomplete, auto-skipped when complete, and the
//  Skip / Next footer actions advance the flow.
//

import Foundation
@testable import meApp
import Testing

extension A6ScaleSetupStoreTests {
    @Suite("Complete Profile")
    @MainActor
    struct CompleteProfile {
        private func makeIncompleteAccount() -> MockAccountService {
            let account = MockAccountService()
            let snapshot = AccountTestFixtures.makeAccountSnapshot(
                gender: nil,
                isActiveAccount: true,
                weightHeight: "0"
            )
            account.seedAccounts([snapshot], active: snapshot)
            account.updateProfileResult = .success(())
            account.updateBodyCompResult = .success(())
            account.createGoalResult = .success(())
            return account
        }

        @Test("intro shows complete profile when profile is incomplete")
        func introShowsCompleteProfileWhenIncomplete() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
        }

        @Test("intro always shows complete profile regardless of existing profile data")
        func introAlwaysShowsCompleteProfile() {
            // Default harness account is complete (gender + height present) — step still shown.
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
        }

        @Test("complete profile prefills on entry")
        func completeProfilePrefillsOnEntry() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
            // Defaults carried from the signup form when the account is missing values.
            #expect(store.completeProfileStore.profileGender == .male)
            #expect(store.completeProfileStore.profileHeightStored == "770")
        }

        @Test("skip advances past complete profile to wake up without persisting")
        func skipAdvancesToWakeUp() {
            let account = makeIncompleteAccount()
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(account: account)
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.handleCompleteProfileSkip()

            #expect(store.currentStep == .wakeUp)
            #expect(account.updateProfileCalls == 0)
        }

        @Test("next saves the profile and advances to wake up")
        func nextSavesAndAdvances() async {
            let account = makeIncompleteAccount()
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(
                account: account,
                pairingTimeoutNs: 5_000_000_000
            )
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.handleCompleteProfileNext()

            await A6ScaleSetupStoreTestFixtures.waitUntil { account.updateProfileCalls == 1 }
            #expect(account.updateProfileCalls == 1)
            #expect(account.updateBodyCompCalls == 1)
            #expect(store.currentStep == .wakeUp)
        }

        @Test("back from complete profile returns to intro when permissions are granted")
        func backReturnsToIntro() {
            let harness = A6ScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            A6ScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.moveToPreviousStep()

            #expect(store.currentStep == .intro)
        }
    }
}
