import Foundation
import Testing
@testable import meApp

extension BpmSetupStoreTests {
    @Suite("Helpers And Edge Cases")
    @MainActor
    struct HelpersAndEdgeCases {

        // MARK: - Step Reconfiguration on Model Switch

        @Test("selecting a different SKU reconfigures steps for that model")
        func reconfigureStepsOnSkuChange() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Default A3 steps include confirmUser
            #expect(store.steps.contains(.confirmUser))
            #expect(!store.steps.contains(.powerSwitch))

            // Switch to 0636 which adds powerSwitch
            store.selectedSku = "0636"
            #expect(store.steps.contains(.powerSwitch))
            #expect(store.steps.contains(.confirmUser))
        }

        @Test("selecting toggle-button SKU removes confirmUser step")
        func reconfigureStepsToggleButton() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            #expect(store.steps.contains(.confirmUser))

            // Switch to 0604 (toggle-switch) which skips confirmUser
            store.selectedSku = "0604"
            #expect(!store.steps.contains(.confirmUser))
        }

        @Test("stepViews builds a view for every step across model variants")
        func stepViewsBuildsViewPerStep() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            // No configured model yet → no views.
            #expect(store.stepViews.isEmpty)

            // A3 default model produces a view per configured step.
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            #expect(store.stepViews.count == store.steps.count)
            #expect(store.stepViews.isEmpty == false)

            // 0636 adds the power-switch step; A6 exercises the A6 view variants.
            store.selectedSku = "0636"
            #expect(store.stepViews.count == store.steps.count)
            BpmSetupStoreTestFixtures.configureA6Bpm(store)
            #expect(store.stepViews.count == store.steps.count)
        }

        @Test("model switch updates nickname to the newly selected model's default")
        func modelSwitchKeepsDefaultNickname() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Selecting a different model reconfigures the nickname to that model's product name.
            store.selectedSku = "0634"

            #expect(store.deviceNickname == "Smart Pro-Series Blood Pressure Monitor")
        }

        @Test("model switch resets nickname to the model default (custom edits are not preserved)")
        func modelSwitchPreservesCustomNickname() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.deviceNickname = "My Custom Name"
            store.selectedSku = "0634"

            // Reconfiguring for the new model overwrites the nickname with the model default.
            #expect(store.deviceNickname == "Smart Pro-Series Blood Pressure Monitor")
        }

        // MARK: - confirmUserAndPair

        @Test("confirmUserAndPair with different user advances without alert")
        func confirmUserAndPairDifferentUser() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            store.testConfirmUserAndPair(isDifferentUser: true)

            // Should not show alert for different-user scenario
            #expect(harness.notification.showAlertCalls == 0)
        }

        @Test("confirmUserAndPair with same user shows conflict alert")
        func confirmUserAndPairSameUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.testConfirmUserAndPair(isDifferentUser: false)

            #expect(harness.notification.showAlertCalls == 1)
        }

        @Test("confirmUserAndPair same-user cancel button dismisses setup")
        func confirmUserAndPairCancelDismisses() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            var dismissed = false
            store.dismissAction = { dismissed = true }

            store.testConfirmUserAndPair(isDifferentUser: false)

            let alert = harness.notification.alertData
            // First button is cancel
            alert?.buttons[0].action(nil)
            #expect(dismissed)
        }

        @Test("confirmUserAndPair same-user continue button advances from scanning")
        func confirmUserAndPairContinueAdvances() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            store.testConfirmUserAndPair(isDifferentUser: false)

            let alert = harness.notification.alertData
            // Second button is continue
            alert?.buttons[1].action(nil)

            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep == .nickname
            }
            #expect(advanced)
        }

        // MARK: - checkForUserMismatch

        @Test("checkForUserMismatch returns false when users match")
        func userMismatchReturnsFalseWhenMatch() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let info = DeviceInfo(
                serialNumber: "SN-1",
                deviceName: "BPM",
                broadcastIdString: "FF01",
                password: "AB",
                macAddress: "AA:BB:CC",
                userNumber: 1
            )

            let mismatch = await store.testCheckForUserMismatch(info)
            #expect(!mismatch)
        }

        @Test("checkForUserMismatch returns true and shows alert when users differ")
        func userMismatchReturnsTrueWhenDiffer() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.broadcastIdString = "FF01"
            store.testSetInternalState(discoveredDevice: device)

            let info = DeviceInfo(
                serialNumber: "SN-1",
                deviceName: "BPM",
                broadcastIdString: "FF01",
                password: "AB",
                macAddress: "AA:BB:CC",
                userNumber: 2
            )

            let mismatch = await store.testCheckForUserMismatch(info)
            #expect(mismatch)
            #expect(harness.notification.showAlertCalls >= 1)
        }

        @Test("user mismatch alert review button navigates to selectUser")
        func userMismatchAlertReviewNavigates() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.broadcastIdString = "FF01"
            store.testSetInternalState(discoveredDevice: device)

            let info = DeviceInfo(
                serialNumber: "SN-1",
                deviceName: "BPM",
                broadcastIdString: "FF01",
                password: "AB",
                macAddress: "AA:BB:CC",
                userNumber: 2
            )

            _ = await store.testCheckForUserMismatch(info)

            let alert = harness.notification.alertData
            // Second button is "REVIEW"
            alert?.buttons[1].action(nil)
            #expect(store.currentStep == .selectUser)
        }

        // MARK: - applyDeviceInfo

        @Test("applyDeviceInfo sets peripheralIdentifier from serialNumber for A3")
        func applyDeviceInfoA3Serial() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            let info = DeviceInfo(
                serialNumber: "SN-12345",
                deviceName: "BPM",
                broadcastIdString: "",
                password: "",
                macAddress: "AA:BB:CC:DD:EE:FF"
            )

            store.testApplyDeviceInfo(info, to: device, protocolType: "A3")

            #expect(device.peripheralIdentifier == "SN-12345")
            #expect(device.mac == "AA:BB:CC:DD:EE:FF")
        }

        @Test("applyDeviceInfo does not set peripheralIdentifier for non-A3")
        func applyDeviceInfoNonA3() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.mac = nil
            let info = DeviceInfo(
                serialNumber: "SN-12345",
                deviceName: "BPM",
                broadcastIdString: "",
                password: "",
                macAddress: "AA:BB:CC"
            )

            store.testApplyDeviceInfo(info, to: device, protocolType: "A6")

            #expect(device.peripheralIdentifier == nil)
            #expect(device.mac == "AA:BB:CC")
        }

        @Test("applyDeviceInfo sets protocolType when device has none")
        func applyDeviceInfoSetsProtocolType() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.protocolType = nil
            let info = DeviceInfo(
                serialNumber: nil,
                deviceName: "BPM",
                broadcastIdString: "",
                password: "",
                macAddress: ""
            )

            store.testApplyDeviceInfo(info, to: device, protocolType: "A3")

            #expect(device.protocolType == "A3")
        }

        @Test("applyDeviceInfo does not overwrite existing MAC")
        func applyDeviceInfoPreservesExistingMac() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.mac = "EXISTING:MAC"
            let info = DeviceInfo(
                serialNumber: nil,
                deviceName: "BPM",
                broadcastIdString: "",
                password: "",
                macAddress: "NEW:MAC"
            )

            store.testApplyDeviceInfo(info, to: device, protocolType: "A3")

            #expect(device.mac == "EXISTING:MAC")
        }

        // MARK: - checkForDuplicateAndAdvance

        @Test("checkForDuplicateAndAdvance with pre-set deviceToDelete uses it directly")
        func duplicateAndAdvanceUsesPreSetDelete() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "existing")
            existing.userNumber = "1"
            store.testSetDeviceToDelete(existing.toSnapshot())

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            await store.testCheckForDuplicateAndAdvance(device)

            // Same user → shows conflict alert
            #expect(harness.notification.showAlertCalls >= 1)
        }

        @Test("checkForDuplicateAndAdvance with no duplicate advances")
        func duplicateAndAdvanceNoDuplicate() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.peripheralIdentifier = "unique-id"
            harness.scaleService.scales = [] // no existing devices

            await store.testCheckForDuplicateAndAdvance(device)

            let advanced = await BpmSetupStoreTestFixtures.waitUntil {
                store.currentStep == .nickname
            }
            #expect(advanced)
        }

        // MARK: - retryScanning

        @Test("retryScanning resets connection state and starts scanning again")
        func retryResetsAndScansAgain() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Record initial scan calls
            let initialCalls = harness.bluetooth.scanForBpmCalls

            // Call retryScanning directly (no need to go through step navigation)
            store.testRetryScanning()

            #expect(store.connectionState == .loading)
            #expect(harness.bluetooth.scanForBpmCalls == initialCalls + 1)
        }

        // MARK: - configure with different SKUs

        @Test("configure with 0636 includes powerSwitch step")
        func configure0636IncludesPowerSwitch() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0636")

            #expect(store.steps.contains(.powerSwitch))
        }

        @Test("configure with 0604 skips confirmUser step")
        func configure0604SkipsConfirmUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            #expect(!store.steps.contains(.confirmUser))
        }

        @Test("configure with 0661 skips confirmUser step")
        func configure0661SkipsConfirmUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0661")

            #expect(!store.steps.contains(.confirmUser))
        }

        @Test("configure sets the per-model product name as the nickname")
        func configureDefaultNickname() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store

            // 0603 (wrist monitor) is the base model whose product name is the default.
            store.configure(with: "0603")
            #expect(store.deviceNickname == BpmSetupStrings.Nickname.defaultName)

            store.configure(with: "0604")
            #expect(store.deviceNickname == "Smart Blood Pressure Monitor")

            store.configure(with: "0634")
            #expect(store.deviceNickname == "Smart Pro-Series Blood Pressure Monitor")

            store.configure(with: "0636")
            #expect(store.deviceNickname == "All-In-One Bluetooth Blood Pressure Monitor")
        }

        // MARK: - isA6Flow

        @Test("isA6Flow returns true for A6 SKU")
        func isA6FlowTrue() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0663")

            #expect(store.isA6Flow == true)
        }

        @Test("isA6Flow returns false for A3 SKU")
        func isA6FlowFalse() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            #expect(store.isA6Flow == false)
        }

        // MARK: - connectionErrorAlert

        @Test("scan failure shows connection error alert with try again button")
        func scanFailureShowsAlert() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT(scanTimeoutNs: 10_000_000) // 10ms
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            store.currentStepIndex = BpmSetupStoreTestFixtures.stepIndex(.scanning, in: store)

            let alertShown = await BpmSetupStoreTestFixtures.waitUntil {
                harness.notification.showAlertCalls > 0
            }
            #expect(alertShown)
            #expect(store.connectionState == .loading)
        }

        // MARK: - Pre-pairing user mismatch detection

        @Test("pre-pairing check detects user mismatch on discovered device")
        func prePairingUserMismatchDetected() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)
            store.selectedUserNumber = 1

            let device = BpmSetupStoreTestFixtures.makeBpmDevice()
            device.userNumber = "2"
            store.testSetInternalState(
                discoveredDevice: device,
                discoveryEvent: BpmSetupStoreTestFixtures.makeBpmDiscoveryEvent(device: device)
            )

            await store.testCheckForPrePairingDuplicate()

            // User mismatch should show alert (not advance to pairing)
            #expect(harness.notification.showAlertCalls >= 1)
        }

        @Test("pre-pairing check with no discovered device shows connection error")
        func prePairingNoDeviceShowsError() async {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            // Don't set discovered device
            await store.testCheckForPrePairingDuplicate()

            #expect(harness.notification.showAlertCalls >= 1)
        }
    }
}
