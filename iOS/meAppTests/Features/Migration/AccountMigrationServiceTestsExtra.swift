import Foundation
import ggInAppMessagingPackage
@testable import meApp
import Testing

// MARK: - AccountMigrationServiceTests Cleanup & ProductTypes

@MainActor
extension AccountMigrationServiceTests {

    // MARK: - cleanupAfterMigration

    @Test("cleanupAfterMigration clears the active account key")
    func cleanupAfterMigration_clearsActiveAccountKey() {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed("some-data", forKey: MigrationKey.activeAccount.rawValue)

        sut.cleanupAfterMigration()

        #expect(kv.hasClearedValue(forKey: MigrationKey.activeAccount.rawValue))
    }

    // MARK: - cleanupOfflineData

    @Test("cleanupOfflineData clears the offline key for the given account")
    func cleanupOfflineData_clearsOfflineKeyForAccount() {
        let accountId = "acc-offline"
        let (sut, kv, _, _, _) = makeSUT()
        let offlineKey = "\(MigrationKey.offlineAccountPrefix.rawValue)\(accountId)"
        kv.seed("data", forKey: offlineKey)

        sut.cleanupOfflineData(for: accountId)

        #expect(kv.hasClearedValue(forKey: offlineKey))
    }

    // MARK: - resetMigrationFlag

    @Test("resetMigrationFlag clears the migration completion flag")
    func resetMigrationFlag_clearsMigrationKey() {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(true, forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue)

        sut.resetMigrationFlag()

        #expect(kv.hasClearedValue(forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue))
        #expect(!kv.contains(key: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue))
    }

    // MARK: - cleanupGoalAlertData

    @Test("cleanupGoalAlertData clears ionic goal alert key for account")
    func cleanupGoalAlertData_clearsIonicGoalAlertKey() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let ionicKey = MigrationKey.goalMetAlertKey(for: accountId)
        kv.seed("true", forKey: ionicKey)

        sut.cleanupGoalAlertData(for: accountId)

        #expect(kv.hasClearedValue(forKey: ionicKey))
    }

    // MARK: - cleanupGoalCardStatusData

    @Test("cleanupGoalCardStatusData clears ionic goal card key for account")
    func cleanupGoalCardStatusData_clearsIonicGoalCardKey() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let ionicKey = MigrationKey.setAGoalCardViewedKey(for: accountId)
        kv.seed("true", forKey: ionicKey)

        sut.cleanupGoalCardStatusData(for: accountId)

        #expect(kv.hasClearedValue(forKey: ionicKey))
    }

    // MARK: - cleanupAppearanceData

    @Test("cleanupAppearanceData clears ionic appearance key for account")
    func cleanupAppearanceData_clearsIonicAppearanceKey() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let ionicKey = MigrationKey.appearanceKey(for: accountId)
        kv.seed("light", forKey: ionicKey)

        sut.cleanupAppearanceData(for: accountId)

        #expect(kv.hasClearedValue(forKey: ionicKey))
    }

    // MARK: - cleanupNotificationAlertData

    @Test("cleanupNotificationAlertData clears ionic notification alert key for account")
    func cleanupNotificationAlertData_clearsIonicNotificationKey() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let ionicKey = MigrationKey.notificationAlertViewedKey(for: accountId)
        kv.seed("true", forKey: ionicKey)

        sut.cleanupNotificationAlertData(for: accountId)

        #expect(kv.hasClearedValue(forKey: ionicKey))
    }

    // MARK: - cleanupGlobalNotificationAlertData

    @Test("cleanupGlobalNotificationAlertData clears global ionic notification key")
    func cleanupGlobalNotificationAlertData_clearsGlobalKey() {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed("true", forKey: MigrationKey.notificationOnlyAlertShown.rawValue)

        sut.cleanupGlobalNotificationAlertData()

        #expect(kv.hasClearedValue(forKey: MigrationKey.notificationOnlyAlertShown.rawValue))
    }

    // MARK: - cleanupScaleData

    @Test("cleanupScaleData clears scale key for account")
    func cleanupScaleData_clearsScaleKey() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        kv.seed("scales-json", forKey: scaleKey)

        sut.cleanupScaleData(for: accountId)

        #expect(kv.hasClearedValue(forKey: scaleKey))
    }

    // MARK: - cleanupFeedData

    @Test("cleanupFeedData clears feed info and lastTriggered keys for account")
    func cleanupFeedData_clearsBothFeedKeys() {
        let accountId = "acc-cleanup"
        let (sut, kv, _, _, _) = makeSUT()
        let feedInfoKey = MigrationKey.feedSettingsInfoKey(for: accountId)
        let feedLastKey = MigrationKey.feedLastTriggeredAtKey(for: accountId)
        kv.seed("data", forKey: feedInfoKey)
        kv.seed(1234.0, forKey: feedLastKey)

        sut.cleanupFeedData(for: accountId)

        #expect(kv.hasClearedValue(forKey: feedInfoKey))
        #expect(kv.hasClearedValue(forKey: feedLastKey))
    }

    // MARK: - cleanupHealthKitIntegrationData

    @Test("cleanupHealthKitIntegrationData clears HealthKit integrated and deintegrated keys")
    func cleanupHealthKitIntegrationData_clearsHKKeys() {
        let accountId = "acc-hk"
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed("true", forKey: MigrationKey.healthKitIntegratedKey(for: accountId))
        kv.seed("false", forKey: MigrationKey.healthKitDeintegratedKey(for: accountId))

        sut.cleanupHealthKitIntegrationData(for: accountId)

        #expect(kv.hasClearedValue(forKey: MigrationKey.healthKitIntegratedKey(for: accountId)))
        #expect(kv.hasClearedValue(forKey: MigrationKey.healthKitDeintegratedKey(for: accountId)))
    }

    @Test("cleanupHealthKitIntegrationData clears assignedTo key only when it matches account")
    func cleanupHealthKitIntegrationData_assignedToMatchesAccount_clearsAssignedTo() {
        let accountId = "acc-hk"
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(accountId, forKey: MigrationKey.healthKitAssignedTo.rawValue)

        sut.cleanupHealthKitIntegrationData(for: accountId)

        #expect(kv.hasClearedValue(forKey: MigrationKey.healthKitAssignedTo.rawValue))
    }

    @Test("cleanupHealthKitIntegrationData does not clear assignedTo key when it belongs to another account")
    func cleanupHealthKitIntegrationData_assignedToOtherAccount_doesNotClearAssignedTo() {
        let accountId = "acc-hk"
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed("other-account", forKey: MigrationKey.healthKitAssignedTo.rawValue)

        sut.cleanupHealthKitIntegrationData(for: accountId)

        #expect(!kv.hasClearedValue(forKey: MigrationKey.healthKitAssignedTo.rawValue))
    }

    // MARK: - migrateAllGoalAlertData (findAllAccountIds)

    @Test("migrateAllGoalAlertData processes all accounts found via goal alert key pattern")
    func migrateAllGoalAlertData_multipleAccounts_migratesAll() {
        let kv = MockMigrationKvStorageService()
        let ids = ["acc1", "acc2", "acc3"]
        for id in ids {
            kv.seed("true", forKey: MigrationKey.goalMetAlertKey(for: id))
        }
        let (sut, _, _, _, _) = makeSUT(kvStorage: kv)

        sut.migrateAllGoalAlertData()

        for id in ids {
            let written = kv.getValue(forKey: KvStorageKeys.goalMetFlagKey(for: id)) as? Bool
            #expect(written == true, "Expected true for account \(id)")
        }
    }

    @Test("migrateAllGoalAlertData does nothing when no keys present")
    func migrateAllGoalAlertData_noKeys_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()

        sut.migrateAllGoalAlertData()

        #expect(kv.setValueCalls.isEmpty)
    }

    // MARK: - migrateAllNotificationAlertData (findAllAccountIds pattern)

    @Test("migrateAllNotificationAlertData processes accounts found via notification alert key pattern")
    func migrateAllNotificationAlertData_withAccounts_migratesAll() {
        let kv = MockMigrationKvStorageService()
        let accountId = "acc-notif-all"
        kv.seed("true", forKey: MigrationKey.notificationAlertViewedKey(for: accountId))
        let (sut, _, _, _, _) = makeSUT(kvStorage: kv)

        sut.migrateAllNotificationAlertData()

        let written = kv.getValue(forKey: KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    // MARK: - migrateAllFeedData (findAllAccountIds pattern)

    @Test("migrateAllFeedData processes accounts found via feed key pattern")
    func migrateAllFeedData_withAccount_migratesFeedData() {
        let kv = MockMigrationKvStorageService()
        let accountId = "acc-feed-all"
        let timestamp = 9999.0
        kv.seed(timestamp, forKey: MigrationKey.feedLastTriggeredAtKey(for: accountId))
        let (sut, _, _, _, _) = makeSUT(kvStorage: kv)

        sut.migrateAllFeedData()

        let stored = kv.getValue(forKey: KvStorageKeys.feedLastTriggeredAtKey(for: accountId)) as? Double
        #expect(stored == timestamp)
    }

    // MARK: - migrateAllScaleData

    @Test("migrateAllScaleData returns results per account")
    func migrateAllScaleData_withAccountKeys_returnsMigrationResults() async {
        let kv = MockMigrationKvStorageService()
        let scales = MockMigrationScaleMigrationService()
        let accountId = "acc-scale-all"
        // Use goal alert key to register the account in findAllAccountIds
        kv.seed("true", forKey: MigrationKey.goalMetAlertKey(for: accountId))
        scales.isMigrationNeededResult = true
        scales.migrateScaleDataResult = .success([makeDevice(id: "d1")])

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv, scaleMigrationService: scales)

        let results = await sut.migrateAllScaleData()

        #expect(results.count == 1)
        #expect(results.first?.accountId == accountId)
        #expect(results.first?.scalesCount == 1)
    }

    @Test("migrateAllScaleData handles per-account scale failure gracefully")
    func migrateAllScaleData_oneAccountFails_continuesWithZeroCount() async {
        let kv = MockMigrationKvStorageService()
        let scales = MockMigrationScaleMigrationService()
        kv.seed("true", forKey: MigrationKey.goalMetAlertKey(for: "acc-fail"))
        scales.isMigrationNeededResult = true
        scales.migrateScaleDataResult = .failure(MigrationTestError.scaleFailed)

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv, scaleMigrationService: scales)

        let results = await sut.migrateAllScaleData()

        #expect(results.count == 1)
        #expect(results.first?.scalesCount == 0)
    }

    // MARK: - Idempotency

    @Test("isMigrationNeeded returns false after migrateAccountAndScaleData completes")
    func idempotency_afterMigrationCompletes_isMigrationNeededReturnsFalse() async throws {
        let (sut, _, _, _, _) = makeSUT()

        _ = try await sut.migrateAccountAndScaleData()

        #expect(sut.isMigrationNeeded() == false)
    }

    @Test("migrateAccountAndScaleData does not re-run migrations when flag already set")
    func idempotency_migrationFlagAlreadySet_isMigrationNeededReturnsFalse() {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(true, forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue)

        // isMigrationNeeded tells callers not to run again
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - findAllAccountIds via healthKitDeintegrated pattern

    @Test("migrateAllIntegrationData processes accounts found via healthKitDeintegrated key pattern")
    func migrateAllIntegrationData_withDeintegratedKey_callsIntegrationStore() {
        let kv = MockMigrationKvStorageService()
        let integration = MockMigrationIntegrationStore()
        let accountId = "acc-deint"
        // healthKitDeintegrated format: CapacitorStorage.healthKitDeintegrated-ACCOUNT_ID
        kv.seed("true", forKey: MigrationKey.healthKitDeintegratedKey(for: accountId))

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv, integrationStore: integration)

        sut.migrateAllIntegrationData()

        #expect(integration.setIntegrationDataCalls >= 1)
    }

    // MARK: - findAllAccountIds via goalCardStatus pattern

    @Test("migrateAllGoalCardStatusData processes accounts found via goalCardStatus key pattern")
    func migrateAllGoalCardStatusData_withGoalCardKey_migratesAccount() {
        let kv = MockMigrationKvStorageService()
        let accountId = "acc-gcs"
        // goalCardStatus format: CapacitorStorage.ACCOUNT_ID_goalCardStatus
        kv.seed("true", forKey: MigrationKey.setAGoalCardViewedKey(for: accountId))

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv)

        sut.migrateAllGoalCardStatusData()

        let written = kv.getValue(forKey: KvStorageKeys.setAGoalModalFlagKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    // MARK: - cleanupAllScaleData

    @Test("cleanupAllScaleData removes scale keys for all discovered accounts")
    func cleanupAllScaleData_withAccounts_clearsScaleKeys() {
        let kv = MockMigrationKvStorageService()
        let accountId = "acc-scale-cleanup"
        // Register via goal alert key
        kv.seed("true", forKey: MigrationKey.goalMetAlertKey(for: accountId))
        // Set scale key to be cleaned up
        let scaleKey = MigrationKey.scaleKey(for: accountId)
        kv.seed("data", forKey: scaleKey)

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv)

        sut.cleanupAllScaleData()

        #expect(kv.hasClearedValue(forKey: scaleKey))
    }

    // MARK: - migrateProductTypesIfNeeded

    @Test("migrateProductTypesIfNeeded is a no-op when productTypes is already populated")
    func migrateProductTypesIfNeeded_alreadyPopulated_doesNotOverwrite() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        account.productTypes = ["myWeight"]

        sut.migrateProductTypesIfNeeded(for: account, devices: [makeDevice(id: "d1")])

        #expect(account.productTypes == ["myWeight"])
    }

    @Test("migrateProductTypesIfNeeded skips migration when devices list is empty")
    func migrateProductTypesIfNeeded_emptyDevices_doesNotWrite() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()

        sut.migrateProductTypesIfNeeded(for: account, devices: [])

        #expect(account.productTypes.isEmpty)
    }

    @Test("migrateProductTypesIfNeeded sets [myWeight] for weight scale only")
    func migrateProductTypesIfNeeded_weightScaleOnly_setsMyWeight() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let device = makeDevice(id: "d1", deviceType: DeviceType.scale.rawValue)

        sut.migrateProductTypesIfNeeded(for: account, devices: [device])

        // Persisted vocabulary is the API value ("weight"); ProductTypeStore
        // normalizes to "myWeight" only at read time. See MOB-581 (3c0517896).
        #expect(account.productTypes == ["weight"])
    }

    @Test("migrateProductTypesIfNeeded sets [myBloodPressure] for BPM only")
    func migrateProductTypesIfNeeded_bpmOnly_setsMyBloodPressure() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let device = makeDevice(id: "d1", deviceType: DeviceType.bpm.rawValue)

        sut.migrateProductTypesIfNeeded(for: account, devices: [device])

        // Persisted vocabulary is the API value ("blood_pressure"). See MOB-581 (3c0517896).
        #expect(account.productTypes == ["blood_pressure"])
    }

    @Test("migrateProductTypesIfNeeded sets [baby] for baby scale only")
    func migrateProductTypesIfNeeded_babyScaleOnly_setsBaby() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let device = makeDevice(id: "d1", deviceType: DeviceType.babyScale.rawValue)

        sut.migrateProductTypesIfNeeded(for: account, devices: [device])

        #expect(account.productTypes == ["baby"])
    }

    @Test("migrateProductTypesIfNeeded sets [myWeight, myBloodPressure] for weight + BPM")
    func migrateProductTypesIfNeeded_weightAndBpm_setsBoth() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let devices = [
            makeDevice(id: "d1", deviceType: DeviceType.scale.rawValue),
            makeDevice(id: "d2", deviceType: DeviceType.bpm.rawValue)
        ]

        sut.migrateProductTypesIfNeeded(for: account, devices: devices)

        #expect(account.productTypes.sorted() == ["blood_pressure", "weight"])
    }

    @Test("migrateProductTypesIfNeeded sets [myWeight, baby] for weight + baby scale")
    func migrateProductTypesIfNeeded_weightAndBaby_setsBoth() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let devices = [
            makeDevice(id: "d1", deviceType: DeviceType.scale.rawValue),
            makeDevice(id: "d2", deviceType: DeviceType.babyScale.rawValue)
        ]

        sut.migrateProductTypesIfNeeded(for: account, devices: devices)

        #expect(account.productTypes.sorted() == ["baby", "weight"])
    }

    @Test("migrateProductTypesIfNeeded sets all three types for weight + BPM + baby scale")
    func migrateProductTypesIfNeeded_allThreeTypes_setsAll() {
        let (sut, _, _, _, _) = makeSUT()
        let account = AccountTestFixtures.makeAccountModel()
        let devices = [
            makeDevice(id: "d1", deviceType: DeviceType.scale.rawValue),
            makeDevice(id: "d2", deviceType: DeviceType.bpm.rawValue),
            makeDevice(id: "d3", deviceType: DeviceType.babyScale.rawValue)
        ]

        sut.migrateProductTypesIfNeeded(for: account, devices: devices)

        #expect(account.productTypes.sorted() == ["baby", "blood_pressure", "weight"])
    }
}
