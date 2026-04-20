import Combine
import Foundation
import GGBluetoothSwiftPackage
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct BluetoothServiceDeviceProfileUtilsTests {

    // MARK: - getSafeScaleType

    @Test("getSafeScaleType returns scaleType when device has bathScale")
    func getSafeScaleTypeWithBathScale() {
        let sut = makeSUT()
        let device = makeDevice(bathScale: BathScale(scaleType: ScaleSourceType.btWifiR4.rawValue, bodyComp: true))

        let result = sut.getSafeScaleType(for: device.toSnapshot())

        #expect(result == ScaleSourceType.btWifiR4.rawValue)
    }

    @Test("getSafeScaleType returns nil when device has no bathScale")
    func getSafeScaleTypeNoBathScale() {
        let sut = makeSUT()
        let device = makeDevice()

        let result = sut.getSafeScaleType(for: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("getSafeScaleType returns nil scaleType when bathScale has nil scaleType")
    func getSafeScaleTypeNilScaleType() {
        let sut = makeSUT()
        let device = makeDevice(bathScale: BathScale(scaleType: nil, bodyComp: nil))

        let result = sut.getSafeScaleType(for: device.toSnapshot())

        #expect(result == nil)
    }

    @Test("getSafeScaleType returns correct type for each ScaleSourceType variant")
    func getSafeScaleTypeAllVariants() {
        let sut = makeSUT()
        let variants: [ScaleSourceType] = [.wifi, .bluetooth, .btWifiR4, .appsync, .bluetoothScale]

        for variant in variants {
            let device = makeDevice(id: "dev-\(variant.rawValue)", bathScale: BathScale(scaleType: variant.rawValue, bodyComp: false))
            let result = sut.getSafeScaleType(for: device.toSnapshot())
            #expect(result == variant.rawValue)
        }
    }

    // MARK: - calculateHeightCm

    @Test("calculateHeightCm converts valid height string")
    func calculateHeightCmValid() {
        let sut = makeSUT()
        // 680 tenths of inches → 680 * 0.254 = 172.72 → 173 cm
        let result = sut.calculateHeightCm(height: "680")

        #expect(result == ConversionTools.convertStoredHeightToCm(680))
    }

    @Test("calculateHeightCm returns default for nil height")
    func calculateHeightCmNil() {
        let sut = makeSUT()
        let result = sut.calculateHeightCm(height: nil)

        #expect(result == ConversionTools.convertStoredHeightToCm(680))
    }

    @Test("calculateHeightCm returns default for non-numeric string")
    func calculateHeightCmNonNumeric() {
        let sut = makeSUT()
        let result = sut.calculateHeightCm(height: "abc")

        #expect(result == ConversionTools.convertStoredHeightToCm(680))
    }

    @Test("calculateHeightCm returns default for empty string")
    func calculateHeightCmEmpty() {
        let sut = makeSUT()
        let result = sut.calculateHeightCm(height: "")

        #expect(result == ConversionTools.convertStoredHeightToCm(680))
    }

    @Test("calculateHeightCm handles decimal string by rounding")
    func calculateHeightCmDecimal() {
        let sut = makeSUT()
        // "700.6" → round(700.6) = 701
        let result = sut.calculateHeightCm(height: "700.6")

        #expect(result == ConversionTools.convertStoredHeightToCm(701))
    }

    // MARK: - createScanData

    @Test("createScanData returns nil for nil account")
    func createScanDataNilAccount() {
        let sut = makeSUT()

        let result = sut.createScanData(from: nil)

        #expect(result == nil)
    }

    @Test("createScanData returns ScanData with correct defaults for minimal account")
    func createScanDataMinimalAccount() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot()
        // AccountDTO defaults: gender=male, weightUnit=kg, activityLevel=normal, height=170, dob="2000-01-01"

        let result = sut.createScanData(from: account)

        #expect(result != nil)
        #expect(result?.sex == "male")
        #expect(result?.unit == "kg")
        #expect(result?.isAthlete == false)
        #expect(result?.goalWeight == nil) // makeAccountDTO has goalWeight: nil
    }

    @Test("createScanData maps athlete activityLevel correctly")
    func createScanDataAthlete() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(activityLevel: .athlete)

        let result = sut.createScanData(from: account)

        #expect(result?.isAthlete == true)
    }

    @Test("createScanData uses fallback age 30 for nil dob")
    func createScanDataNilDob() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(dob: nil)

        let result = sut.createScanData(from: account)

        #expect(result?.age == 30)
    }

    @Test("createScanData uses fallback age 30 for invalid dob format")
    func createScanDataInvalidDob() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(dob: "not-a-date")

        let result = sut.createScanData(from: account)

        #expect(result?.age == 30)
    }

    @Test("createScanData defaults sex to male when gender is nil")
    func createScanDataNilGender() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(gender: nil)

        let result = sut.createScanData(from: account)

        #expect(result?.sex == "male")
    }

    @Test("createScanData maps female gender correctly")
    func createScanDataFemaleGender() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(gender: .female)

        let result = sut.createScanData(from: account)

        #expect(result?.sex == "female")
    }

    @Test("createScanData defaults unit to kg when weightUnit is kg")
    func createScanDataDefaultWeightUnit() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(weightUnit: .kg)

        let result = sut.createScanData(from: account)

        #expect(result?.unit == "kg")
    }

    @Test("createScanData converts goalWeight when present")
    func createScanDataWithGoalWeight() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(goalType: .lose, goalWeight: 1500, initialWeight: 180)

        let result = sut.createScanData(from: account)

        #expect(result?.goalWeight != nil)
        #expect(result?.goalWeight == ConversionTools.convertStoredToDisplay(1500, isMetric: true))
    }

    @Test("createScanData calculates height using stored height conversion")
    func createScanDataHeightConversion() {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot()
        // AccountDTO height is 170 → stored as string "170" in WeightCompSettings
        // calculateHeightCm("170") → round(170.0) = 170 → convertStoredHeightToCm(170)

        let result = sut.createScanData(from: account)

        let expectedHeight = ConversionTools.convertStoredHeightToCm(170)
        #expect(result?.height == Double(expectedHeight))
    }

    // MARK: - getProfileInfo

    @Test("getProfileInfo returns profile with weight when latest entry exists")
    func getProfileInfoWithLatestEntry() async {
        let entry = MockEntryService()
        let sut = makeSUT(entry: entry)
        let account = AccountTestFixtures.makeAccountSnapshot(firstName: "Alice")

        let testEntry = Entry(entryTimestamp: "2026-01-01T00:00:00Z", accountId: account.accountId, operationType: "create")
        testEntry.scaleEntry = BathScaleEntry(weight: 1500) // stored weight
        entry.getLatestEntryResult = .success(testEntry)

        let result = await sut.getProfileInfo(from: account)

        #expect(result != nil)
        #expect(result?.name == "Alice")
        #expect(result?.weight != nil)
        #expect(result?.weight == ConversionTools.convertStoredToDisplay(1500, isMetric: true))
    }

    @Test("getProfileInfo returns profile with nil weight when no latest entry")
    func getProfileInfoNoLatestEntry() async {
        let entry = MockEntryService()
        entry.latestEntry = nil
        let sut = makeSUT(entry: entry)
        let account = AccountTestFixtures.makeAccountSnapshot(firstName: "Bob")

        let result = await sut.getProfileInfo(from: account)

        #expect(result != nil)
        #expect(result?.name == "Bob")
        #expect(result?.weight == nil)
    }

    @Test("getProfileInfo uses 'User' when firstName is nil")
    func getProfileInfoNilFirstName() async {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(firstName: nil)

        let result = await sut.getProfileInfo(from: account)

        #expect(result?.name == "User")
    }

    @Test("getProfileInfo includes goalType from account")
    func getProfileInfoWithGoalType() async {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(goalType: .lose, goalWeight: 150, initialWeight: 180)

        let result = await sut.getProfileInfo(from: account)

        #expect(result?.goalType == GoalType.lose.rawValue)
    }

    @Test("getProfileInfo maps scan data fields to profile")
    func getProfileInfoMapsFields() async {
        let sut = makeSUT()
        let account = AccountTestFixtures.makeAccountSnapshot(gender: .female, weightUnit: .lb, activityLevel: .athlete)

        let result = await sut.getProfileInfo(from: account)

        #expect(result?.sex == "female")
        #expect(result?.isAthlete == true)
        #expect(result?.unit == "lb")
    }

    // MARK: - getWeightByProtocolType

    @Test("getWeightByProtocolType returns correct value for A3")
    func getWeightByProtocolTypeA3() {
        let sut = makeSUT()
        let weightInKg: Float = 70.0

        let result = sut.getWeightByProtocolType(protocolType: .A3, weightInKg: weightInKg, weight: 0)

        let expected = Int(ConversionTools.convertBluetoothToStored(Double(weightInKg)) * 10)
        #expect(result == expected)
    }

    @Test("getWeightByProtocolType returns correct value for A6")
    func getWeightByProtocolTypeA6() {
        let sut = makeSUT()
        let weightInKg: Float = 70.0

        let result = sut.getWeightByProtocolType(protocolType: .A6, weightInKg: weightInKg, weight: 0)

        let expected = Int(ConversionTools.convertKgToStored(Double(weightInKg)))
        #expect(result == expected)
    }

    @Test("getWeightByProtocolType returns correct value for R4")
    func getWeightByProtocolTypeR4() {
        let sut = makeSUT()
        let weightLbs: Float = 154.3

        let result = sut.getWeightByProtocolType(protocolType: .R4, weightInKg: 0, weight: weightLbs)

        let expected = Int(ConversionTools.convertLbsToStored(Double(weightLbs)))
        #expect(result == expected)
    }

    @Test("getWeightByProtocolType handles zero weight")
    func getWeightByProtocolTypeZero() {
        let sut = makeSUT()

        let resultA3 = sut.getWeightByProtocolType(protocolType: .A3, weightInKg: 0, weight: 0)
        let resultA6 = sut.getWeightByProtocolType(protocolType: .A6, weightInKg: 0, weight: 0)
        let resultR4 = sut.getWeightByProtocolType(protocolType: .R4, weightInKg: 0, weight: 0)

        #expect(resultA3 == 0)
        #expect(resultA6 == 0)
        #expect(resultR4 == 0)
    }

    // MARK: - disconnectDevice

    @Test("disconnectDevice adds broadcastId to skipDevices")
    func disconnectDeviceAddsToSkipDevices() async {
        let sut = makeSUT()

        _ = await sut.disconnectDevice(broadcastId: "BID-1")

        #expect(sut.skipDevices.contains("BID-1"))
    }

    @Test("disconnectDevice does not duplicate broadcastId in skipDevices")
    func disconnectDeviceNoDuplicate() async {
        let sut = makeSUT()
        sut.skipDevices = ["BID-1"]

        _ = await sut.disconnectDevice(broadcastId: "BID-1")

        #expect(sut.skipDevices.filter { $0 == "BID-1" }.count == 1)
    }

    @Test("disconnectDevice adds to blockedBroadcastIds")
    func disconnectDeviceAddsToBlocked() async {
        let sut = makeSUT()

        _ = await sut.disconnectDevice(broadcastId: "BID-2")

        #expect(sut.blockedBroadcastIds.contains("BID-2"))
    }

    @Test("disconnectDevice cancels existing unblock task for same broadcastId")
    func disconnectDeviceCancelsExistingTask() async {
        let sut = makeSUT()

        _ = await sut.disconnectDevice(broadcastId: "BID-3")
        let firstTask = sut.unblockTasks["BID-3"]

        _ = await sut.disconnectDevice(broadcastId: "BID-3")
        let secondTask = sut.unblockTasks["BID-3"]

        #expect(firstTask?.isCancelled == true)
        #expect(secondTask != nil)
    }

    @Test("disconnectDevice calls skipDevice on SDK")
    func disconnectDeviceCallsSDKSkip() async {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)

        _ = await sut.disconnectDevice(broadcastId: "BID-4", considerForSession: false)

        #expect(sdk.skippedDevices.count == 1)
        #expect(sdk.skippedDevices.first?.broadcastId == "BID-4")
        #expect(sdk.skippedDevices.first?.considerForSession == false)
    }

    @Test("disconnectDevice sets canShowScaleDiscoveredModal to false")
    func disconnectDeviceSetsModalFlag() async {
        let sut = makeSUT()
        sut.setCanShowScaleDiscoveredModal(true)

        _ = await sut.disconnectDevice(broadcastId: "BID-5")

        #expect(sut.canShowScaleDiscoveredModal == false)
    }

    @Test("disconnectDevice returns success")
    func disconnectDeviceReturnsSuccess() async {
        let sut = makeSUT()

        let result = await sut.disconnectDevice(broadcastId: "BID-6")

        guard case .success = result else {
            Issue.record("Expected success")
            return
        }
    }

    // MARK: - reapplySkipDevicesExcludingPaired

    @Test("reapplySkipDevicesExcludingPaired removes paired devices from skipDevices")
    func reapplySkipDevicesRemovesPaired() {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.skipDevices = ["PAIRED-1", "UNPAIRED-1", "PAIRED-2"]
        sut.bluetoothScales = [
            makeSnapshot(id: "d1", broadcastIdString: "PAIRED-1"),
            makeSnapshot(id: "d2", broadcastIdString: "PAIRED-2")
        ]

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sut.skipDevices == ["UNPAIRED-1"])
    }

    @Test("reapplySkipDevicesExcludingPaired calls skipDevice for remaining devices")
    func reapplySkipDevicesCallsSDK() {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.skipDevices = ["PAIRED-1", "KEEP-1", "KEEP-2"]
        sut.bluetoothScales = [makeDevice(id: "d1", broadcastIdString: "PAIRED-1").toSnapshot()]

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sdk.skippedDevices.count == 2)
        #expect(sdk.skippedDevices.map(\.broadcastId).sorted() == ["KEEP-1", "KEEP-2"])
    }

    @Test("reapplySkipDevicesExcludingPaired is case-insensitive")
    func reapplySkipDevicesCaseInsensitive() {
        let sut = makeSUT()
        sut.skipDevices = ["paired-1", "keep-1"]
        sut.bluetoothScales = [makeDevice(id: "d1", broadcastIdString: "PAIRED-1").toSnapshot()]

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sut.skipDevices == ["keep-1"])
    }

    @Test("reapplySkipDevicesExcludingPaired with no paired devices keeps all skipDevices")
    func reapplySkipDevicesNoPaired() {
        let sdk = MockBluetoothSDKClient()
        let sut = makeSUT(sdk: sdk)
        sut.skipDevices = ["A", "B"]
        sut.bluetoothScales = []

        sut.reapplySkipDevicesExcludingPaired()

        #expect(sut.skipDevices == ["A", "B"])
        #expect(sdk.skippedDevices.count == 2)
    }

    // MARK: - disconnectDeletedScales

    @Test("disconnectDeletedScales disconnects scales removed from new list")
    func disconnectDeletedScalesRemoved() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let currentDevice = makeSnapshot(id: "del-1", accountId: "acct-1", broadcastIdString: "DEL-BID", isConnected: true)

        await sut.disconnectDeletedScales(currentScales: [currentDevice], newScales: [])

        // deleteDevice is called through SDK (via sdkOperationSerializer)
        #expect(sdk.skippedDevices.contains { $0.broadcastId == "DEL-BID" })
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 1)
    }

    @Test("disconnectDeletedScales skips non-connected devices")
    func disconnectDeletedScalesSkipsNotConnected() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let currentDevice = makeSnapshot(id: "del-2", accountId: "acct-1", broadcastIdString: "BID-2", isConnected: false)

        await sut.disconnectDeletedScales(currentScales: [currentDevice], newScales: [])

        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 0)
        #expect(sdk.skippedDevices.isEmpty)
    }

    @Test("disconnectDeletedScales only processes scales for active account")
    func disconnectDeletedScalesAccountFiltering() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let otherAccountDevice = makeSnapshot(id: "other-1", accountId: "other-acct", broadcastIdString: "OTHER-BID", isConnected: true)

        await sut.disconnectDeletedScales(currentScales: [otherAccountDevice], newScales: [])

        // Device belongs to different account, should not be processed
        #expect(sdk.skippedDevices.isEmpty)
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 0)
    }

    @Test("disconnectDeletedScales keeps scales present in both lists")
    func disconnectDeletedScalesKeepsExisting() async {
        let sdk = MockBluetoothSDKClient()
        let scale = MockScaleService()
        let sut = makeSUT(scale: scale, sdk: sdk)
        sut.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", isActiveAccount: true)

        let device = makeSnapshot(id: "keep-1", accountId: "acct-1", broadcastIdString: "KEEP-BID", isConnected: true)

        // Same device in both lists (broadcastId match — both nil Int64, so they match)
        await sut.disconnectDeletedScales(currentScales: [device], newScales: [device])

        #expect(sdk.skippedDevices.isEmpty)
        #expect(scale.updateConnectedDeviceWeightOnlyModeCalls == 0)
    }

    // MARK: - Helpers

    private func makeSUT(
        account: MockAccountService? = nil,
        scale: MockScaleService? = nil,
        entry: MockEntryService? = nil,
        logger: MockLoggerService? = nil,
        discovery: MockBLEDiscoveryManager? = nil,
        sdk: MockBluetoothSDKClient? = nil,
        notification: MockNotificationHelperService? = nil
    ) -> BluetoothService {
        BluetoothService(
            accountService: account ?? MockAccountService(),
            scaleService: scale ?? MockScaleService(),
            entryService: entry ?? MockEntryService(),
            babyService: MockBabyService(),
            logger: logger ?? MockLoggerService(),
            discoveryManager: discovery ?? MockBLEDiscoveryManager(),
            ggBleSDK: sdk ?? MockBluetoothSDKClient(),
            notificationService: notification ?? MockNotificationHelperService()
        )
    }

    private func makeDevice(
        id: String = "device-1",
        accountId: String = "101",
        broadcastIdString: String? = "ABC123",
        isConnected: Bool? = true,
        bathScale: BathScale? = nil
    ) -> Device {
        BluetoothTestFixtures.makeDevice(
            id: id,
            accountId: accountId,
            broadcastIdString: broadcastIdString,
            isConnected: isConnected,
            bathScale: bathScale
        )
    }

    private func makeSnapshot(
        id: String = "device-1",
        accountId: String = "101",
        broadcastIdString: String? = "ABC123",
        isConnected: Bool = true,
        bathScale: BathScale? = nil
    ) -> DeviceSnapshot {
        makeDevice(
            id: id,
            accountId: accountId,
            broadcastIdString: broadcastIdString,
            isConnected: isConnected,
            bathScale: bathScale
        ).toSnapshot(isConnected: isConnected)
    }
}
