//
//  BluetoothScaleSetupStoreCompleteProfileTests.swift
//  meAppTests
//
//  Covers the Complete Profile Setup step in the Bluetooth (A3) flow (MOB-1388): it is
//  shown before pairing when the profile is incomplete, auto-skipped when complete, and
//  the Skip / Next footer actions route on to the pairing steps.
//

import Foundation
@testable import meApp
import Testing

extension BluetoothScaleSetupStoreTests {
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
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
        }

        @Test("intro always shows complete profile regardless of existing profile data")
        func introAlwaysShowsCompleteProfile() {
            // Default harness account is complete (gender + height present) — step still shown.
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
        }

        @Test("permissions next routes to complete profile when incomplete")
        func permissionsNextRoutesToCompleteProfile() {
            let permissions = MockPermissionsService()
            permissions.setPermissions(BluetoothScaleSetupStoreTestFixtures.disabledPermissions())
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(
                permissions: permissions,
                account: makeIncompleteAccount()
            )
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.currentStepIndex = BluetoothScaleSetupStep.permissions.index

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
        }

        @Test("complete profile prefills on entry")
        func completeProfilePrefillsOnEntry() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)

            store.moveToNextStep()

            #expect(store.currentStep == .completeProfile)
            #expect(store.completeProfileStore.profileGender == .male)
            #expect(store.completeProfileStore.profileHeightStored == "770")
        }

        @Test("skip advances to select user without persisting")
        func skipAdvancesToSelectUser() {
            let account = makeIncompleteAccount()
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(account: account)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.handleCompleteProfileSkip()

            #expect(store.currentStep == .selectUser)
            #expect(account.updateProfileCalls == 0)
        }

        @Test("next saves the profile and advances to select user")
        func nextSavesAndAdvances() async {
            let account = makeIncompleteAccount()
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(account: account)
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.handleCompleteProfileNext()

            await BluetoothScaleSetupStoreTestFixtures.waitUntil { account.updateProfileCalls == 1 }
            #expect(account.updateProfileCalls == 1)
            #expect(account.updateBodyCompCalls == 1)
            #expect(store.currentStep == .selectUser)
        }

        @Test("back from complete profile returns to intro when permissions are granted")
        func backReturnsToIntro() {
            let harness = BluetoothScaleSetupStoreTestFixtures.makeSUT(account: makeIncompleteAccount())
            let store = harness.store
            BluetoothScaleSetupStoreTestFixtures.configureDefaultScale(store)
            store.moveToNextStep()
            #expect(store.currentStep == .completeProfile)

            store.moveToPreviousStep()

            #expect(store.currentStep == .intro)
        }
    }
}
