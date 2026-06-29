import Foundation
@testable import meApp
import Testing

extension BpmSetupStoreTests {
    @Suite("Asset Resolution")
    @MainActor
    struct AssetResolution {

        // MARK: - gifSubdirectory

        @Test("gifSubdirectory returns A3/0603 for default A3 SKU 0603")
        func gifSubdirectoryA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifSubdirectory(for: "0603")
            #expect(result == "Gifs/BpmMonitors/A3/0603")
        }

        @Test("gifSubdirectory returns shared A3/0603 for A3 SKU 0634")
        func gifSubdirectoryA3PerSku0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testGifSubdirectory(for: "0634")
            #expect(result == "Gifs/BpmMonitors/A3/0634")
        }

        @Test("gifSubdirectory returns shared A3/0603 for A3 SKU 0636")
        func gifSubdirectoryA3PerSku0636() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0636")

            let result = store.testGifSubdirectory(for: "0636")
            #expect(result == "Gifs/BpmMonitors/A3/0636")
        }

        @Test("gifSubdirectory returns A6/0663 for A6 SKU")
        func gifSubdirectoryA6() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testGifSubdirectory(for: "0663")
            #expect(result == "Gifs/BpmMonitors/A6/0663")
        }

        @Test("gifSubdirectory returns A6/0661 for A6 SKU 0661")
        func gifSubdirectoryA6_0661() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0661")

            let result = store.testGifSubdirectory(for: "0661")
            #expect(result == "Gifs/BpmMonitors/A6/0661")
        }

        // MARK: - confirmUserGifSubdirectory

        @Test("confirmUserGifSubdirectory returns per-SKU folder for 0634")
        func confirmUserGifSubdir0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testConfirmUserGifSubdirectory(for: "0634")
            #expect(result == "Gifs/BpmMonitors/A3/0634")
        }

        @Test("confirmUserGifSubdirectory returns shared 0603 folder for 0636")
        func confirmUserGifSubdir0636() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0636")

            let result = store.testConfirmUserGifSubdirectory(for: "0636")
            #expect(result == "Gifs/BpmMonitors/A3/0636")
        }

        @Test("confirmUserGifSubdirectory returns shared 0603 folder for default A3 SKU")
        func confirmUserGifSubdirA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testConfirmUserGifSubdirectory(for: "0603")
            #expect(result == "Gifs/BpmMonitors/A3/0603")
        }

        @Test("confirmUserGifSubdirectory returns A6 folder for A6 SKU")
        func confirmUserGifSubdirA6() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testConfirmUserGifSubdirectory(for: "0663")
            #expect(result == "Gifs/BpmMonitors/A6/0663")
        }

        // MARK: - userGifSubdirectory

        @Test("userGifSubdirectory returns per-SKU folder for A3")
        func userGifSubdirA3() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            let result = store.testUserGifSubdirectory(for: "0604")
            #expect(result == "Gifs/BpmMonitors/A3/0604")
        }

        @Test("userGifSubdirectory returns per-SKU folder for A6")
        func userGifSubdirA6() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testUserGifSubdirectory(for: "0663")
            #expect(result == "Gifs/BpmMonitors/A6/0663")
        }

        // MARK: - gifName

        @Test("gifName for prePairing returns MEM_Button for default A3 SKU")
        func gifNamePrePairingA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifName(for: .prePairing, sku: "0603")
            #expect(result == "A3_MEM_Button")
        }

        @Test("gifName for prePairing returns per-SKU Pulse for 0634")
        func gifNamePrePairing0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testGifName(for: .prePairing, sku: "0634")
            #expect(result == "A3_0634_Pulse")
        }

        @Test("gifName for prePairing returns MEM_Button for A3 SKU 0636")
        func gifNamePrePairing0636() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0636")

            let result = store.testGifName(for: .prePairing, sku: "0636")
            #expect(result == "A3_0636_Pulse")
        }

        @Test("gifName for confirmUser returns Pulse for A3 SKU 0634")
        func gifNameConfirmUser0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testGifName(for: .confirmUser, sku: "0634")
            #expect(result == nil)
        }

        @Test("gifName for confirmUser returns nil for default A3 SKU")
        func gifNameConfirmUserA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifName(for: .confirmUser, sku: "0603")
            #expect(result == nil)
        }

        @Test("gifName for measureSetup returns shared Cuff for A3 SKU 0634")
        func gifNameMeasureSetup0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testGifName(for: .measureSetup, sku: "0634")
            #expect(result == "A3_0634_Cuff")
        }

        @Test("gifName for measureSetup returns shared Cuff for default A3")
        func gifNameMeasureSetupA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifName(for: .measureSetup, sku: "0603")
            #expect(result == "A3_Cuff")
        }

        @Test("gifName for measureStart returns shared Start for A3 SKU 0636")
        func gifNameMeasureStart0636() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0636")

            let result = store.testGifName(for: .measureStart, sku: "0636")
            #expect(result == "A3_0636_Start")
        }

        @Test("gifName for measureStart returns shared Start for default A3")
        func gifNameMeasureStartA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifName(for: .measureStart, sku: "0603")
            #expect(result == "A3_Start")
        }

        @Test("gifName for A6 prePairing returns resolved Pulse for 0663")
        func gifNamePrePairingA6_0663() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testGifName(for: .prePairing, sku: "0663")
            #expect(result == "A6_Pulse")
        }

        @Test("gifName for A6 prePairing returns shared Pulse for 0661")
        func gifNamePrePairingA6_0661() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0661")

            let result = store.testGifName(for: .prePairing, sku: "0661")
            #expect(result == "A6_0661_Pulse")
        }

        @Test("gifName for A6 measureSetup returns shared Cuff for 0661")
        func gifNameMeasureSetupA6_0661() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0661")

            let result = store.testGifName(for: .measureSetup, sku: "0661")
            #expect(result == "A6_0661_Cuff")
        }

        @Test("gifName for A6 measureStart returns shared Start for 0661")
        func gifNameMeasureStartA6_0661() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0661")

            let result = store.testGifName(for: .measureStart, sku: "0661")
            #expect(result == "A6_0661_Start")
        }

        @Test("gifName returns nil for unrecognized step")
        func gifNameUnrecognizedStep() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testGifName(for: .selectModel, sku: "0603")
            #expect(result == nil)
        }

        // MARK: - imageName

        @Test("imageName for setUser returns A3_SetUser for A3 SKU")
        func imageNameSetUserA3() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testImageName(for: .setUser, sku: "0603")
            #expect(result == "A3_SetUser")
        }

        @Test("imageName for confirmUser returns A3_Monitor_StartStop for default A3")
        func imageNameConfirmUserA3Default() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testImageName(for: .confirmUser, sku: "0603")
            #expect(result == "A3_Monitor_StartStop")
        }

        @Test("imageName for confirmUser returns A3_Monitor_StartStop for 0634")
        func imageNameConfirmUser0634() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0634")

            let result = store.testImageName(for: .confirmUser, sku: "0634")
            #expect(result == "A3_0634_Monitor_Off")
        }

        @Test("imageName for setUser returns A6_SetUser for A6 SKU")
        func imageNameSetUserA6() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testImageName(for: .setUser, sku: "0663")
            #expect(result == "A6_SetUser")
        }

        @Test("imageName for confirmUser returns A6_Monitor_StartStop for A6")
        func imageNameConfirmUserA6() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testImageName(for: .confirmUser, sku: "0663")
            #expect(result == "A6_Monitor_StartStop")
        }

        @Test("imageName returns nil for unrecognized step")
        func imageNameUnrecognizedStep() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testImageName(for: .scanning, sku: "0603")
            #expect(result == nil)
        }

        // MARK: - userGifName

        @Test("userGifName returns A3_User_1 for 0603 user 1")
        func userGifName0603User1() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testUserGifName(for: "0603", selectedUserNumber: 1)
            #expect(result == "A3_User_1")
        }

        @Test("userGifName returns A3_0604_User_A for 0604 user 1")
        func userGifName0604UserA() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            let result = store.testUserGifName(for: "0604", selectedUserNumber: 1)
            #expect(result == "A3_0604_User_A")
        }

        @Test("userGifName returns A3_0604_User_B for 0604 user 2")
        func userGifName0604UserB() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            let result = store.testUserGifName(for: "0604", selectedUserNumber: 2)
            #expect(result == "A3_0604_User_B")
        }

        @Test("userGifName returns A6_0663_User_A for A6 user 1")
        func userGifNameA6UserA() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA6Bpm(store)

            let result = store.testUserGifName(for: "0663", selectedUserNumber: 1)
            #expect(result == "A6_0663_User_A")
        }

        @Test("userGifName returns nil when no user selected")
        func userGifNameNilUser() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let result = store.testUserGifName(for: "0603", selectedUserNumber: nil)
            #expect(result == nil)
        }

        // MARK: - userLabel

        @Test("userLabel returns numeric for 0603")
        func userLabelNumeric0603() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            #expect(store.testUserLabel(for: 1) == "1")
            #expect(store.testUserLabel(for: 2) == "2")
        }

        @Test("userLabel returns A/B for non-numeric monitors")
        func userLabelAlpha() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            #expect(store.testUserLabel(for: 1) == "A")
            #expect(store.testUserLabel(for: 2) == "B")
        }

        // MARK: - userLabelForConflict

        @Test("userLabelForConflict returns numeric when 0603 configured")
        func userLabelForConflictNumeric() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "old")
            existing.userNumber = "2"
            store.testSetDeviceToDelete(existing.toSnapshot())

            #expect(store.testUserLabelForConflict() == "2")
        }

        @Test("userLabelForConflict returns A/B when non-numeric monitor configured")
        func userLabelForConflictAlpha() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            store.configure(with: "0604")

            let existing = BpmSetupStoreTestFixtures.makeBpmDevice(id: "old")
            existing.userNumber = "1"
            store.testSetDeviceToDelete(existing.toSnapshot())

            #expect(store.testUserLabelForConflict() == "A")
        }

        @Test("userLabelForConflict returns default 1 when no deviceToDelete")
        func userLabelForConflictDefault() {
            let harness = BpmSetupStoreTestFixtures.makeSUT()
            let store = harness.store
            BpmSetupStoreTestFixtures.configureA3Bpm(store)

            #expect(store.testUserLabelForConflict() == "1")
        }
    }
}
