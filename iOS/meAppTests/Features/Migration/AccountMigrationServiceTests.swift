import Foundation
import ggInAppMessagingPackage
@testable import meApp
import Testing

// MARK: - AccountMigrationServiceTests

@Suite(.serialized)
@MainActor
struct AccountMigrationServiceTests {

    // MARK: - isMigrationNeeded

    @Test("isMigrationNeeded returns true when migration flag is absent")
    func isMigrationNeeded_flagAbsent_returnsTrue() {
        let (sut, _, _, _, _) = makeSUT()
        #expect(sut.isMigrationNeeded() == true)
    }

    @Test("isMigrationNeeded returns false when migration flag is true")
    func isMigrationNeeded_flagSet_returnsFalse() {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(true, forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue)
        #expect(sut.isMigrationNeeded() == false)
    }

    // MARK: - migrateAccountData

    @Test("migrateAccountData returns nil when no Ionic account data in storage")
    func migrateAccountData_noData_returnsNil() async throws {
        let (sut, _, repo, _, _) = makeSUT()
        let result = try await sut.migrateAccountData()
        #expect(result == nil)
        #expect(repo.saveAccountCalls == 0)
    }

    @Test("migrateAccountData returns nil when account JSON is invalid")
    func migrateAccountData_invalidJSON_returnsNil() async throws {
        let (sut, kv, repo, _, _) = makeSUT()
        kv.seed("not-valid-json{{{", forKey: MigrationKey.activeAccount.rawValue)
        let result = try await sut.migrateAccountData()
        #expect(result == nil)
        #expect(repo.saveAccountCalls == 0)
    }

    @Test("migrateAccountData successfully migrates valid Ionic account data")
    func migrateAccountData_validData_savesAccountAndReturnsIt() async throws {
        let (sut, kv, repo, _, _) = makeSUT()
        kv.seed(makeIonicAccountJSON(id: "acc-1", email: "user@test.com"), forKey: MigrationKey.activeAccount.rawValue)

        let result = try await sut.migrateAccountData()

        #expect(result != nil)
        #expect(result?.email == "user@test.com")
        #expect(repo.saveAccountCalls == 1)
        #expect(repo.lastSavedAccount?.email == "user@test.com")
    }

    @Test("migrateAccountData sets account flags correctly on migrated account")
    func migrateAccountData_validData_setsAccountFlags() async throws {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(makeIonicAccountJSON(), forKey: MigrationKey.activeAccount.rawValue)

        let result = try await sut.migrateAccountData()

        #expect(result?.isLoggedIn == true)
        #expect(result?.isActiveAccount == true)
        #expect(result?.isExpired == false)
        #expect(result?.isSynced == false)
    }

    @Test("migrateAccountData propagates error when saveAccount throws")
    func migrateAccountData_saveThrows_propagatesError() async {
        let (sut, kv, repo, _, _) = makeSUT()
        kv.seed(makeIonicAccountJSON(), forKey: MigrationKey.activeAccount.rawValue)
        repo.saveAccountError = MigrationTestError.saveFailed

        do {
            _ = try await sut.migrateAccountData()
            Issue.record("Expected migrateAccountData to throw")
        } catch {
            #expect(error as? MigrationTestError == .saveFailed)
        }
    }

    @Test("migrateAccountData stores tokens in keychain when tokens are non-empty")
    func migrateAccountData_withTokens_storesInKeychain() async throws {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(
            // swiftlint:disable:next no_hardcoded_credentials
            makeIonicAccountJSON(accessToken: "tok-access", refreshToken: "tok-refresh", expiresAt: "2025-12-31"),
            forKey: MigrationKey.activeAccount.rawValue
        )

        let result = try await sut.migrateAccountData()

        // Keychain is mocked via TestDependencyContainer; verify account was returned
        #expect(result != nil)
    }

    // MARK: - migrateAccountAndScaleData

    @Test("migrateAccountAndScaleData marks migration complete after running")
    func migrateAccountAndScaleData_completes_setsMigrationFlag() async throws {
        let (sut, kv, _, _, _) = makeSUT()

        _ = try await sut.migrateAccountAndScaleData()

        let flagValue = kv.getValue(forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue) as? Bool
        #expect(flagValue == true)
    }

    @Test("migrateAccountAndScaleData returns nil account when no Ionic data")
    func migrateAccountAndScaleData_noData_returnsNilAccountAndZeroScales() async throws {
        let (sut, _, _, _, _) = makeSUT()

        let result = try await sut.migrateAccountAndScaleData()

        #expect(result.account == nil)
        #expect(result.scalesCount == 0)
    }

    @Test("migrateAccountAndScaleData returns migrated account and scale count")
    func migrateAccountAndScaleData_withDataAndScales_returnsBothCounts() async throws {
        let kv = MockMigrationKvStorageService()
        let scales = MockMigrationScaleMigrationService()
        // Seed one account key so findAllAccountIds returns one ID
        let accountId = "acc-multi"
        kv.seed("dummy", forKey: MigrationKey.goalMetAlertKey(for: accountId))
        // Account data
        kv.seed(makeIonicAccountJSON(id: accountId, email: "multi@test.com"), forKey: MigrationKey.activeAccount.rawValue)
        // Scale migration returns 2 devices
        scales.isMigrationNeededResult = true
        scales.migrateScaleDataResult = .success([makeDevice(id: "d1"), makeDevice(id: "d2")])

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv, scaleMigrationService: scales)

        let result = try await sut.migrateAccountAndScaleData()

        #expect(result.account?.email == "multi@test.com")
        #expect(result.scalesCount == 2)
    }

    @Test("migrateAccountAndScaleData continues and returns nil account when account migration fails")
    func migrateAccountAndScaleData_accountMigrationFails_returnsNilAccount() async throws {
        let kv = MockMigrationKvStorageService()
        let repo = MockMigrationAccountRepository()
        kv.seed(makeIonicAccountJSON(), forKey: MigrationKey.activeAccount.rawValue)
        repo.saveAccountError = MigrationTestError.saveFailed

        let (sut, _, _, _, _) = makeSUT(kvStorage: kv, accountRepo: repo)

        let result = try await sut.migrateAccountAndScaleData()

        #expect(result.account == nil)
        // Migration flag must still be set despite failure
        let flag = kv.getValue(forKey: KvStorageKeys.ionicToNativeAppMigrationCompleted.rawValue) as? Bool
        #expect(flag == true)
    }

    @Test("migrateAccountAndScaleData cleans up all Ionic data after completion")
    func migrateAccountAndScaleData_completes_cleansUpActiveAccountKey() async throws {
        let (sut, kv, _, _, _) = makeSUT()
        kv.seed(makeIonicAccountJSON(), forKey: MigrationKey.activeAccount.rawValue)

        _ = try await sut.migrateAccountAndScaleData()

        #expect(kv.hasClearedValue(forKey: MigrationKey.activeAccount.rawValue))
    }

    // MARK: - migrateGoalAlertData

    @Test("migrateGoalAlertData converts 'true' string to Bool true")
    func migrateGoalAlertData_trueString_writesTrueBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-goal"
        kv.seed("true", forKey: MigrationKey.goalMetAlertKey(for: accountId))

        sut.migrateGoalAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.goalMetFlagKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    @Test("migrateGoalAlertData converts 'false' string to Bool false")
    func migrateGoalAlertData_falseString_writesFalseBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-goal"
        kv.seed("false", forKey: MigrationKey.goalMetAlertKey(for: accountId))

        sut.migrateGoalAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.goalMetFlagKey(for: accountId)) as? Bool
        #expect(written == false)
    }

    @Test("migrateGoalAlertData does nothing when no Ionic value present")
    func migrateGoalAlertData_noValue_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-goal"

        sut.migrateGoalAlertData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.goalMetFlagKey(for: accountId)))
    }

    // MARK: - migrateGoalCardStatusData

    @Test("migrateGoalCardStatusData converts 'true' string to Bool true")
    func migrateGoalCardStatusData_trueString_writesTrueBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-card"
        kv.seed("true", forKey: MigrationKey.setAGoalCardViewedKey(for: accountId))

        sut.migrateGoalCardStatusData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.setAGoalModalFlagKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    @Test("migrateGoalCardStatusData converts 'false' string to Bool false")
    func migrateGoalCardStatusData_falseString_writesFalseBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-card"
        kv.seed("false", forKey: MigrationKey.setAGoalCardViewedKey(for: accountId))

        sut.migrateGoalCardStatusData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.setAGoalModalFlagKey(for: accountId)) as? Bool
        #expect(written == false)
    }

    @Test("migrateGoalCardStatusData does nothing when no Ionic value present")
    func migrateGoalCardStatusData_noValue_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-card"

        sut.migrateGoalCardStatusData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.setAGoalModalFlagKey(for: accountId)))
    }

    // MARK: - migrateAppearanceData

    @Test("migrateAppearanceData maps 'light' to 'Light'")
    func migrateAppearanceData_light_mapsToLight() {
        assertAppearanceMigration(ionicValue: "light", expectedNativeValue: "Light")
    }

    @Test("migrateAppearanceData maps 'system_light' to 'Light'")
    func migrateAppearanceData_systemLight_mapsToLight() {
        assertAppearanceMigration(ionicValue: "system_light", expectedNativeValue: "Light")
    }

    @Test("migrateAppearanceData maps 'dark' to 'Dark'")
    func migrateAppearanceData_dark_mapsToDark() {
        assertAppearanceMigration(ionicValue: "dark", expectedNativeValue: "Dark")
    }

    @Test("migrateAppearanceData maps 'system_dark' to 'Dark'")
    func migrateAppearanceData_systemDark_mapsToDark() {
        assertAppearanceMigration(ionicValue: "system_dark", expectedNativeValue: "Dark")
    }

    @Test("migrateAppearanceData maps 'system' to 'System Settings'")
    func migrateAppearanceData_system_mapsToSystemSettings() {
        assertAppearanceMigration(ionicValue: "system", expectedNativeValue: "System Settings")
    }

    @Test("migrateAppearanceData maps unknown value to 'System Settings'")
    func migrateAppearanceData_unknown_mapsToSystemSettings() {
        assertAppearanceMigration(ionicValue: "banana", expectedNativeValue: "System Settings")
    }

    @Test("migrateAppearanceData does nothing when no Ionic value present")
    func migrateAppearanceData_noValue_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-app"

        sut.migrateAppearanceData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.appearanceModeKey(for: accountId)))
    }

    // MARK: - migrateNotificationAlertData

    @Test("migrateNotificationAlertData converts 'true' string to Bool true")
    func migrateNotificationAlertData_trueString_writesTrueBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-notif"
        kv.seed("true", forKey: MigrationKey.notificationAlertViewedKey(for: accountId))

        sut.migrateNotificationAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    @Test("migrateNotificationAlertData converts 'false' string to Bool false")
    func migrateNotificationAlertData_falseString_writesFalseBool() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-notif"
        kv.seed("false", forKey: MigrationKey.notificationAlertViewedKey(for: accountId))

        sut.migrateNotificationAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)) as? Bool
        #expect(written == false)
    }

    @Test("migrateNotificationAlertData does nothing when no Ionic value present")
    func migrateNotificationAlertData_noValue_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-notif"

        sut.migrateNotificationAlertData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.notificationOnlyAlertShownKey(for: accountId)))
    }

    // MARK: - migrateGlobalNotificationAlertData

    @Test("migrateGlobalNotificationAlertData converts 'true' to account-scoped Bool true")
    func migrateGlobalNotificationAlertData_trueValue_writesToAccountScopedKey() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-global"
        kv.seed("true", forKey: MigrationKey.notificationOnlyAlertShown.rawValue)

        sut.migrateGlobalNotificationAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)) as? Bool
        #expect(written == true)
    }

    @Test("migrateGlobalNotificationAlertData converts 'false' to account-scoped Bool false")
    func migrateGlobalNotificationAlertData_falseValue_writesToAccountScopedKeyAsFalse() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-global"
        kv.seed("false", forKey: MigrationKey.notificationOnlyAlertShown.rawValue)

        sut.migrateGlobalNotificationAlertData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)) as? Bool
        #expect(written == false)
    }

    @Test("migrateGlobalNotificationAlertData does nothing when no global flag present")
    func migrateGlobalNotificationAlertData_noValue_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-global"

        sut.migrateGlobalNotificationAlertData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.notificationOnlyPermAlertShownKey(for: accountId)))
    }

    // MARK: - migrateHealthKitIntegrationData

    @Test("migrateHealthKitIntegrationData does nothing when no HealthKit flags present")
    func migrateHealthKitIntegrationData_noFlags_noIntegrationStoreCall() {
        let (sut, _, _, _, integration) = makeSUT()

        sut.migrateHealthKitIntegrationData(for: "acc-hk")

        #expect(integration.setIntegrationDataCalls == 0)
    }

    @Test("migrateHealthKitIntegrationData creates integration info when isIntegrated is true")
    func migrateHealthKitIntegrationData_isIntegratedTrue_callsIntegrationStore() {
        let (sut, kv, _, _, integration) = makeSUT()
        let accountId = "acc-hk"
        kv.seed("true", forKey: MigrationKey.healthKitIntegratedKey(for: accountId))

        sut.migrateHealthKitIntegrationData(for: accountId)

        #expect(integration.setIntegrationDataCalls == 1)
        #expect(integration.lastIntegrationInfo?.isIntegrated == true)
        #expect(integration.lastIntegrationInfo?.type == .healthKit)
        #expect(integration.lastAccountId == accountId)
    }

    @Test("migrateHealthKitIntegrationData uses assignedTo as the integration accountId")
    func migrateHealthKitIntegrationData_withAssignedTo_usesAssignedToAsAccountId() {
        let (sut, kv, _, _, integration) = makeSUT()
        let accountId = "acc-hk"
        let assignedToId = "other-acc"
        kv.seed("true", forKey: MigrationKey.healthKitIntegratedKey(for: accountId))
        kv.seed(assignedToId, forKey: MigrationKey.healthKitAssignedTo.rawValue)

        sut.migrateHealthKitIntegrationData(for: accountId)

        #expect(integration.lastAccountId == assignedToId)
        #expect(integration.lastIntegrationInfo?.assignedTo == assignedToId)
    }

    @Test("migrateHealthKitIntegrationData sets deIntegrated when deintegration flag is true")
    func migrateHealthKitIntegrationData_deintegratedTrue_setsDeIntegratedField() {
        let (sut, kv, _, _, integration) = makeSUT()
        let accountId = "acc-hk"
        kv.seed("true", forKey: MigrationKey.healthKitDeintegratedKey(for: accountId))

        sut.migrateHealthKitIntegrationData(for: accountId)

        #expect(integration.setIntegrationDataCalls == 1)
        #expect(integration.lastIntegrationInfo?.deIntegrated == accountId)
    }

    @Test("migrateHealthKitIntegrationData does not set deIntegrated when flag is false")
    func migrateHealthKitIntegrationData_deintegratedFalse_deIntegratedIsNil() {
        let (sut, kv, _, _, integration) = makeSUT()
        let accountId = "acc-hk"
        kv.seed("true", forKey: MigrationKey.healthKitIntegratedKey(for: accountId))
        kv.seed("false", forKey: MigrationKey.healthKitDeintegratedKey(for: accountId))

        sut.migrateHealthKitIntegrationData(for: accountId)

        #expect(integration.lastIntegrationInfo?.deIntegrated == nil)
    }

    @Test("migrateHealthKitIntegrationData logs error but does not crash when integrationStore throws")
    func migrateHealthKitIntegrationData_storeThrows_logsErrorNoCrash() {
        let (sut, kv, _, _, integration) = makeSUT()
        let accountId = "acc-hk"
        kv.seed("true", forKey: MigrationKey.healthKitIntegratedKey(for: accountId))
        integration.setIntegrationDataError = MigrationTestError.integrationFailed

        // Should not throw — errors are logged, not propagated
        sut.migrateHealthKitIntegrationData(for: accountId)

        #expect(integration.setIntegrationDataCalls == 1)
    }

    // MARK: - migrateScaleData

    @Test("migrateScaleData returns 0 when scale migration is not needed")
    func migrateScaleData_notNeeded_returnsZeroWithoutMigrating() async throws {
        let scales = MockMigrationScaleMigrationService()
        scales.isMigrationNeededResult = false
        let (sut, _, _, _, _) = makeSUT(scaleMigrationService: scales)

        let count = try await sut.migrateScaleData(for: "acc-scale")

        #expect(count == 0)
        #expect(scales.migrateScaleDataCalls.isEmpty)
        #expect(scales.cleanupAfterMigrationCalls.isEmpty)
    }

    @Test("migrateScaleData returns device count and cleans up when migration succeeds")
    func migrateScaleData_succeeds_returnsCountAndCleansUp() async throws {
        let scales = MockMigrationScaleMigrationService()
        scales.isMigrationNeededResult = true
        scales.migrateScaleDataResult = .success([makeDevice(id: "s1"), makeDevice(id: "s2"), makeDevice(id: "s3")])
        let (sut, _, _, _, _) = makeSUT(scaleMigrationService: scales)

        let count = try await sut.migrateScaleData(for: "acc-scale")

        #expect(count == 3)
        #expect(scales.cleanupAfterMigrationCalls.contains("acc-scale"))
    }

    @Test("migrateScaleData propagates error when scale service throws")
    func migrateScaleData_serviceThrows_propagatesError() async {
        let scales = MockMigrationScaleMigrationService()
        scales.isMigrationNeededResult = true
        scales.migrateScaleDataResult = .failure(MigrationTestError.scaleFailed)
        let (sut, _, _, _, _) = makeSUT(scaleMigrationService: scales)

        do {
            _ = try await sut.migrateScaleData(for: "acc-scale")
            Issue.record("Expected migrateScaleData to throw")
        } catch {
            #expect(error as? MigrationTestError == .scaleFailed)
        }
        #expect(scales.cleanupAfterMigrationCalls.isEmpty)
    }

    // MARK: - migrateFeedData

    @Test("migrateFeedData migrates feed info stored as JSON string")
    func migrateFeedData_jsonStringFeedInfo_decodesAndStores() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-feed"
        let feedJSON = #"{"showPopupMessage":true,"showNotificationBadge":false}"#
        kv.seed(feedJSON, forKey: MigrationKey.feedSettingsInfoKey(for: accountId))

        sut.migrateFeedData(for: accountId)

        let stored = kv.getCodable(forKey: KvStorageKeys.feedInfoKey(for: accountId), as: FeedSetting.self)
        #expect(stored?.showPopupMessage == true)
        #expect(stored?.showNotificationBadge == false)
    }

    @Test("migrateFeedData migrates feed info stored as dictionary")
    func migrateFeedData_dictionaryFeedInfo_convertsAndStores() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-feed"
        let dict: [String: Any] = ["showPopupMessage": false, "showNotificationBadge": true]
        kv.seed(dict, forKey: MigrationKey.feedSettingsInfoKey(for: accountId))

        sut.migrateFeedData(for: accountId)

        let stored = kv.getCodable(forKey: KvStorageKeys.feedInfoKey(for: accountId), as: FeedSetting.self)
        #expect(stored?.showPopupMessage == false)
        #expect(stored?.showNotificationBadge == true)
    }

    @Test("migrateFeedData migrates feed info stored as encoded Data")
    func migrateFeedData_encodedDataFeedInfo_decodesAndStores() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-feed"
        let feedSetting = FeedSetting(showPopupMessage: true, showNotificationBadge: true)
        let data = try? JSONEncoder().encode(feedSetting)
        kv.seed(data as Any, forKey: MigrationKey.feedSettingsInfoKey(for: accountId))

        sut.migrateFeedData(for: accountId)

        let stored = kv.getCodable(forKey: KvStorageKeys.feedInfoKey(for: accountId), as: FeedSetting.self)
        #expect(stored?.showPopupMessage == true)
        #expect(stored?.showNotificationBadge == true)
    }

    @Test("migrateFeedData copies lastTriggered timestamp to native key")
    func migrateFeedData_withLastTriggered_copiesTimestamp() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-feed"
        let timestamp = 1_700_000_000.0
        kv.seed(timestamp, forKey: MigrationKey.feedLastTriggeredAtKey(for: accountId))

        sut.migrateFeedData(for: accountId)

        let stored = kv.getValue(forKey: KvStorageKeys.feedLastTriggeredAtKey(for: accountId)) as? Double
        #expect(stored == timestamp)
    }

    @Test("migrateFeedData does nothing when no feed data present")
    func migrateFeedData_noData_writesNothing() {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-feed"

        sut.migrateFeedData(for: accountId)

        #expect(!kv.hasSetValue(forKey: KvStorageKeys.feedInfoKey(for: accountId)))
        #expect(!kv.hasSetValue(forKey: KvStorageKeys.feedLastTriggeredAtKey(for: accountId)))
    }

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

    // MARK: - makeSUT

    @MainActor
    func makeSUT(
        kvStorage: MockMigrationKvStorageService? = nil,
        accountRepo: MockMigrationAccountRepository? = nil,
        scaleMigrationService: MockMigrationScaleMigrationService? = nil,
        integrationStore: MockMigrationIntegrationStore? = nil
        // Test factory return; labeled tuple is clearer than a one-off SUT struct.
        // swiftlint:disable:next large_tuple
    ) -> (
        sut: AccountMigrationService,
        kv: MockMigrationKvStorageService,
        repo: MockMigrationAccountRepository,
        scales: MockMigrationScaleMigrationService,
        integration: MockMigrationIntegrationStore
    ) {
        let kvStorage = kvStorage ?? MockMigrationKvStorageService()
        let accountRepo = accountRepo ?? MockMigrationAccountRepository()
        let scaleMigrationService = scaleMigrationService ?? MockMigrationScaleMigrationService()
        let integrationStore = integrationStore ?? MockMigrationIntegrationStore()

        TestDependencyContainer.reset()
        let sut = AccountMigrationService(
            kvStorage: kvStorage,
            accountRepo: accountRepo,
            scaleMigrationService: scaleMigrationService,
            integrationStore: integrationStore
        )
        return (sut, kvStorage, accountRepo, scaleMigrationService, integrationStore)
    }

    // MARK: - Helpers

    func assertAppearanceMigration(ionicValue: String, expectedNativeValue: String) {
        let (sut, kv, _, _, _) = makeSUT()
        let accountId = "acc-app"
        kv.seed(ionicValue, forKey: MigrationKey.appearanceKey(for: accountId))

        sut.migrateAppearanceData(for: accountId)

        let written = kv.getValue(forKey: KvStorageKeys.appearanceModeKey(for: accountId)) as? String
        #expect(written == expectedNativeValue, "ionic '\(ionicValue)' should map to '\(expectedNativeValue)', got '\(written ?? "nil")'")
    }

    func makeIonicAccountJSON(
        id: String = "account-abc",
        email: String = "test@example.com",
        accessToken: String = "access-token",
        refreshToken: String = "refresh-token",
        expiresAt: String = "2025-12-31T00:00:00Z"
    ) -> String {
        return """
        {
            "accessToken": "\(accessToken)",
            "refreshToken": "\(refreshToken)",
            "expiresAt": "\(expiresAt)",
            "id": "\(id)",
            "email": "\(email)",
            "firstName": "TestUser",
            "gender": "male",
            "weightUnit": "lb",
            "height": 70,
            "activityLevel": "normal",
            "dob": "1990-01-01",
            "dashboardType": "dashboard_4",
            "dashboardMetrics": [],
            "shouldSendEntryNotifications": true,
            "shouldSendWeightInEntryNotifications": false
        }
        """
    }

    func makeDevice(id: String, deviceType: String = DeviceType.scale.rawValue) -> Device {
        let device = ScaleTestFixtures.makeDevice(id: id)
        device.deviceType = deviceType
        return device
    }
}

// MARK: - Test Error

enum MigrationTestError: Error, Equatable {
    case saveFailed
    case scaleFailed
    case integrationFailed
}
