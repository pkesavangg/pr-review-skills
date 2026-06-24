import Foundation
import Testing
@testable import meApp

@Suite(.serialized)
@MainActor
struct HealthKitServiceTests {

    // MARK: - integrate(turnOn:)

    @Test("integrate turnOn success: available, authorized, permissions, setStoredIntegrationData succeeds, returns true")
    func integrateTurnOnSuccess() async throws {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = false
        integration.setStoredIntegrationDataError = nil
        let handler = MockHealthKitHandler()
        handler.availableResult = true
        handler.requestAuthorizationResult = true
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let account = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let accountService = MockAccountService()
        accountService.seedAccounts([account], active: account)

        let sut = makeSUT(
            integrationService: integration,
            accountService: accountService,
            healthKitHandler: handler
        )

        let result = try await sut.integrate(turnOn: true)

        #expect(result == true)
        #expect(integration.isIntegrationAlreadyUsedCalls == 1)
        #expect(handler.availableCalls == 1)
        // MOB-405: full authorization requests Weight Gurus + Balance Health sets back-to-back.
        #expect(handler.requestAuthorizationCalls == 2)
        #expect(integration.setStoredIntegrationDataCalls == 1)
        #expect(integration.lastSetStoredIntegrationDataInfo?.isIntegrated == true)
        #expect(integration.lastSetStoredIntegrationDataInfo?.assignedTo == "101")
    }

    @Test("integrate turnOn userConflict: isIntegrationAlreadyUsed true, throws IntegrationError.userConflict")
    func integrateTurnOnUserConflict() async {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = true
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService)

        do {
            _ = try await sut.integrate(turnOn: true)
            Issue.record("Expected IntegrationError.userConflict")
        } catch {
            #expect(error is IntegrationError)
            #expect((error as? IntegrationError) == .userConflict)
        }
        #expect(integration.isIntegrationAlreadyUsedCalls == 1)
    }

    @Test("integrate turnOn isIntegrationAlreadyUsed throws: maps to IntegrationError.userConflict")
    func integrateTurnOnIntegrationCheckThrows() async {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedError = HealthKitTestError.persistenceFailed
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService)

        do {
            _ = try await sut.integrate(turnOn: true)
            Issue.record("Expected IntegrationError.userConflict")
        } catch {
            #expect((error as? IntegrationError) == .userConflict)
        }
    }

    @Test("integrate turnOn unavailable: available false, returns false")
    func integrateTurnOnUnavailable() async throws {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = false
        let handler = MockHealthKitHandler()
        handler.availableResult = false
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.integrate(turnOn: true)

        #expect(result == false)
        #expect(handler.availableCalls == 1)
        #expect(handler.requestAuthorizationCalls == 0)
    }

    @Test("integrate turnOn authorization failed: requestAuthorization false, returns false")
    func integrateTurnOnAuthorizationFailed() async throws {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = false
        let handler = MockHealthKitHandler()
        handler.availableResult = true
        handler.requestAuthorizationResult = false
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.integrate(turnOn: true)

        #expect(result == false)
        // MOB-405: both Weight Gurus + Balance Health authorization requests are attempted upfront.
        #expect(handler.requestAuthorizationCalls == 2)
    }

    @Test("integrate turnOn no permissions: getApprovedPermissionList empty, returns false")
    func integrateTurnOnNoPermissions() async throws {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = false
        let handler = MockHealthKitHandler()
        handler.availableResult = true
        handler.requestAuthorizationResult = true
        handler.getApprovedPermissionListReturn = []
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.integrate(turnOn: true)

        #expect(result == false)
        #expect(handler.getApprovedPermissionListCalls >= 1)
        #expect(integration.setStoredIntegrationDataCalls == 0)
    }

    @Test("integrate turnOn setStoredIntegrationData throws: returns false")
    func integrateTurnOnSetStoredFails() async throws {
        let integration = MockIntegrationService()
        integration.isIntegrationAlreadyUsedResult = false
        integration.setStoredIntegrationDataError = HealthKitTestError.persistenceFailed
        let handler = MockHealthKitHandler()
        handler.availableResult = true
        handler.requestAuthorizationResult = true
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.integrate(turnOn: true)

        #expect(result == false)
        #expect(integration.setStoredIntegrationDataCalls == 1)
    }

    @Test("integrate turnOff: calls clearHealthKit, returns false")
    func integrateTurnOff() async throws {
        let integration = MockIntegrationService()
        let handler = MockHealthKitHandler()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.integrate(turnOn: false)

        #expect(result == false)
        #expect(integration.clearIntegrationStatusCalls == 1)
        #expect(handler.deleteAllDataCalls == 1)
    }

    // MARK: - isHKOutOfSync

    @Test("isHKOutOfSync integrated and no permissions: returns true")
    func isHKOutOfSyncTrue() async {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: true, assignedTo: "101")
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []

        let sut = makeSUT(integrationService: integration, healthKitHandler: handler)

        let result = await sut.isHKOutOfSync()

        #expect(result == true)
        #expect(integration.getStoredIntegrationDataCalls == 1)
    }

    @Test("isHKOutOfSync not integrated: returns false")
    func isHKOutOfSyncNotIntegrated() async {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: false)
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []

        let sut = makeSUT(integrationService: integration, healthKitHandler: handler)

        let result = await sut.isHKOutOfSync()

        #expect(result == false)
    }

    @Test("isHKOutOfSync has permissions: returns false")
    func isHKOutOfSyncHasPermissions() async {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: true)
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]

        let sut = makeSUT(integrationService: integration, healthKitHandler: handler)

        let result = await sut.isHKOutOfSync()

        #expect(result == false)
    }

    @Test("isHKOutOfSync getStoredIntegrationData throws: returns false")
    func isHKOutOfSyncLoadFails() async {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataError = HealthKitTestError.persistenceFailed
        let sut = makeSUT(integrationService: integration)

        let result = await sut.isHKOutOfSync()

        #expect(result == false)
    }

    // MARK: - syncAllData

    @Test("syncAllData no active account: returns without throwing and does not call saveData")
    func syncAllDataNoActiveAccount() async throws {
        let accountService = MockAccountService()
        accountService.activeAccount = nil
        let handler = MockHealthKitHandler()
        let sut = makeSUT(accountService: accountService, healthKitHandler: handler)

        try await sut.syncAllData()

        #expect(handler.saveDataCalls == 0)
    }

    @Test("syncAllData active account with no create entries: does not call saveData")
    func syncAllDataNoCreateEntries() async throws {
        let accountId = "hk-sync-\(UUID().uuidString)"
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: accountId, email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let handler = MockHealthKitHandler()

        let nonCreate = HealthKitTestFixtures.makeEntry(
            accountId: accountId,
            timestamp: "2026-01-15T12:00:00.000Z"
        )
        nonCreate.operationType = OperationType.delete.rawValue
        try persistEntries([nonCreate])

        let sut = makeSUT(accountService: accountService, healthKitHandler: handler)
        try await sut.syncAllData()

        #expect(handler.saveDataCalls == 0)
    }

    @Test("syncAllData active account with create entries: saves payload")
    func syncAllDataWithEntries() async throws {
        let accountId = "hk-sync-\(UUID().uuidString)"
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: accountId, email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let handler = MockHealthKitHandler()

        let validEntry = HealthKitTestFixtures.makeEntry(
            accountId: accountId,
            timestamp: "2026-01-15T12:00:00.000Z",
            weight: 70000,
            bodyFat: 20,
            muscleMass: 35000,
            bmi: 2200
        )
        let invalidTimestampEntry = HealthKitTestFixtures.makeEntry(
            accountId: accountId,
            timestamp: "invalid-ts",
            weight: 70000
        )
        try persistEntries([validEntry, invalidTimestampEntry])

        let sut = makeSUT(accountService: accountService, healthKitHandler: handler)
        try await sut.syncAllData()

        #expect(handler.saveDataCalls == 1)
        #expect(handler.saveDataLastPayloadCount >= 1)
    }

    @Test("syncAllData handler saveData throws: rethrows")
    func syncAllDataSaveThrows() async {
        let accountId = "hk-sync-\(UUID().uuidString)"
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: accountId, email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let handler = MockHealthKitHandler()
        handler.saveDataError = HealthKitTestError.persistenceFailed

        let entry = HealthKitTestFixtures.makeEntry(
            accountId: accountId,
            timestamp: "2026-01-15T12:00:00.000Z",
            weight: 70000
        )
        try? persistEntries([entry])

        let sut = makeSUT(accountService: accountService, healthKitHandler: handler)
        do {
            try await sut.syncAllData()
            Issue.record("Expected throw")
        } catch {
            #expect(handler.saveDataCalls == 1)
            #expect(error as? HealthKitTestError == .persistenceFailed)
        }
    }

    // MARK: - openAppleHealth

    @Test("openAppleHealth calls handler")
    func openAppleHealthCallsHandler() async {
        let handler = MockHealthKitHandler()
        let sut = makeSUT(healthKitHandler: handler)

        sut.openAppleHealth()
        try? await Task.sleep(nanoseconds: 300_000_000)

        #expect(handler.openAppleHealthCalls == 1)
    }

    // MARK: - checkAuthorizationStatus / getApprovedPermissionList

    @Test("checkAuthorizationStatus has permissions: returns true")
    func checkAuthorizationStatusTrue() {
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let sut = makeSUT(healthKitHandler: handler)

        let result = sut.checkAuthorizationStatus()

        #expect(result == true)
        #expect(handler.getApprovedPermissionListCalls >= 1)
    }

    @Test("checkAuthorizationStatus no permissions: returns false")
    func checkAuthorizationStatusFalse() {
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let sut = makeSUT(healthKitHandler: handler)

        let result = sut.checkAuthorizationStatus()

        #expect(result == false)
    }

    @Test("getApprovedPermissionList returns handler list")
    func getApprovedPermissionList() {
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["a", "b"]
        let sut = makeSUT(healthKitHandler: handler)

        let result = sut.getApprovedPermissionList()

        #expect(result == ["a", "b"])
    }

    // MARK: - syncNewData(notification:) / deleteEntry(notification:)

    @Test("syncNewData notification success: builds payload and calls handler saveData")
    func syncNewDataNotificationSuccess() async throws {
        let handler = MockHealthKitHandler()
        let notification = HealthKitTestFixtures.makeEntryNotification(weight: 70000, bodyFat: 20, bmi: 2200, pulse: 72)
        let sut = makeSUT(healthKitHandler: handler)

        try await sut.syncNewData(notification: notification)

        #expect(handler.saveDataCalls == 1)
        #expect(handler.saveDataLastPayloadCount >= 1)
    }

    @Test("syncNewData notification invalid timestamp: calls saveData with empty payload")
    func syncNewDataNotificationInvalidTimestamp() async throws {
        let handler = MockHealthKitHandler()
        let notification = HealthKitTestFixtures.makeEntryNotification(entryTimestamp: "invalid-ts")
        let sut = makeSUT(healthKitHandler: handler)

        try await sut.syncNewData(notification: notification)

        #expect(handler.saveDataCalls == 1)
        #expect(handler.saveDataLastPayloadCount == 0)
    }

    @Test("syncNewData entry success: converts entry and calls saveData")
    func syncNewDataEntrySuccess() async throws {
        let handler = MockHealthKitHandler()
        let entry = HealthKitTestFixtures.makeEntry(
            timestamp: "2026-01-15T12:00:00Z",
            weight: 70000,
            bodyFat: 20,
            bmi: 2200,
            pulse: 72
        )
        let sut = makeSUT(healthKitHandler: handler)

        try await sut.syncNewData(entry: entry)

        #expect(handler.saveDataCalls == 1)
        #expect(handler.saveDataLastPayloadCount >= 1)
    }

    @Test("syncNewData entry invalid timestamp: calls saveData with empty payload")
    func syncNewDataEntryInvalidTimestamp() async throws {
        let handler = MockHealthKitHandler()
        let entry = HealthKitTestFixtures.makeEntry(timestamp: "invalid-ts")
        let sut = makeSUT(healthKitHandler: handler)

        try await sut.syncNewData(entry: entry)

        #expect(handler.saveDataCalls == 1)
        #expect(handler.saveDataLastPayloadCount == 0)
    }

    @Test("syncNewData entry saveData throws: rethrows")
    func syncNewDataEntrySaveThrows() async {
        let handler = MockHealthKitHandler()
        handler.saveDataError = HealthKitTestError.persistenceFailed
        let entry = HealthKitTestFixtures.makeEntry()
        let sut = makeSUT(healthKitHandler: handler)

        do {
            try await sut.syncNewData(entry: entry)
            Issue.record("Expected throw")
        } catch {
            #expect(handler.saveDataCalls == 1)
            #expect(error as? HealthKitTestError == .persistenceFailed)
        }
    }

    @Test("deleteEntry notification success: calls handler deleteEntry")
    func deleteEntryNotificationSuccess() async throws {
        let handler = MockHealthKitHandler()
        let notification = HealthKitTestFixtures.makeEntryNotification()
        let sut = makeSUT(healthKitHandler: handler)

        let result = try await sut.deleteEntry(notification: notification)

        #expect(result == true)
        #expect(handler.deleteEntryCalls == 1)
    }

    @Test("deleteEntry notification invalid timestamp: calls deleteEntry with empty payload")
    func deleteEntryNotificationInvalidTimestamp() async throws {
        let handler = MockHealthKitHandler()
        let notification = HealthKitTestFixtures.makeEntryNotification(entryTimestamp: "invalid-ts")
        let sut = makeSUT(healthKitHandler: handler)

        let result = try await sut.deleteEntry(notification: notification)

        #expect(result == true)
        #expect(handler.deleteEntryCalls == 1)
    }

    @Test("deleteEntry entry success: converts entry and calls handler")
    func deleteEntryEntrySuccess() async throws {
        let handler = MockHealthKitHandler()
        let entry = HealthKitTestFixtures.makeEntry()
        let sut = makeSUT(healthKitHandler: handler)

        let result = try await sut.deleteEntry(entry: entry)

        #expect(result == true)
        #expect(handler.deleteEntryCalls == 1)
    }

    @Test("deleteEntry entry throws: rethrows")
    func deleteEntryEntryThrows() async {
        let handler = MockHealthKitHandler()
        handler.deleteEntryError = HealthKitTestError.persistenceFailed
        let entry = HealthKitTestFixtures.makeEntry()
        let sut = makeSUT(healthKitHandler: handler)

        do {
            _ = try await sut.deleteEntry(entry: entry)
            Issue.record("Expected throw")
        } catch {
            #expect(handler.deleteEntryCalls == 1)
            #expect(error as? HealthKitTestError == .persistenceFailed)
        }
    }

    // MARK: - clearHealthKit

    @Test("clearHealthKit success: clearIntegrationStatus and deleteAllData")
    func clearHealthKitSuccess() async throws {
        let integration = MockIntegrationService()
        let handler = MockHealthKitHandler()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        try await sut.clearHealthKit()

        #expect(integration.clearIntegrationStatusCalls == 1)
        #expect(handler.deleteAllDataCalls == 1)
    }

    @Test("clearHealthKit clearIntegrationStatus throws: still calls deleteAllData")
    func clearHealthKitClearStatusThrows() async throws {
        let integration = MockIntegrationService()
        integration.clearIntegrationStatusError = HealthKitTestError.persistenceFailed
        let handler = MockHealthKitHandler()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        try await sut.clearHealthKit()

        #expect(integration.clearIntegrationStatusCalls == 1)
        #expect(handler.deleteAllDataCalls == 1)
    }

    @Test("clearHealthKit deleteAllData throws: rethrows")
    func clearHealthKitDeleteAllThrows() async {
        let integration = MockIntegrationService()
        let handler = MockHealthKitHandler()
        handler.deleteAllDataError = HealthKitTestError.persistenceFailed
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        do {
            try await sut.clearHealthKit()
            Issue.record("Expected throw")
        } catch {
            #expect(handler.deleteAllDataCalls == 1)
            #expect(error as? HealthKitTestError == .persistenceFailed)
        }
    }

    // MARK: - shouldShowHKIntegrationModal

    @Test("shouldShowHKIntegrationModal no accountId: returns nil")
    func shouldShowHKModalNoAccount() async throws {
        let accountService = MockAccountService()
        accountService.activeAccount = nil
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let sut = makeSUT(accountService: accountService, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == nil)
    }

    @Test("shouldShowHKIntegrationModal outOfSync: integrated, no permissions, not yet shown")
    func shouldShowHKModalOutOfSync() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: true, assignedTo: "101")
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == .outOfSync)
    }

    @Test("shouldShowHKIntegrationModal finishAdding: permissions granted, no stored integration, not used by another")
    func shouldShowHKModalFinishAdding() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = nil
        integration.isIntegrationAlreadyUsedResult = false
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == .finishAdding)
    }

    @Test("shouldShowHKIntegrationModal addIntegration: account has HealthKit on, no stored, not used by another")
    func shouldShowHKModalAddIntegration() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = nil
        integration.isIntegrationAlreadyUsedResult = false
        let kv = MockKvStorageService()
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true, isHealthKitOn: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == .addIntegration)
    }

    @Test("shouldShowHKIntegrationModal returns nil when no modal needed")
    func shouldShowHKModalNil() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: true)
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == nil)
    }

    @Test("shouldShowHKIntegrationModal outOfSync already shown: returns nil")
    func shouldShowHKModalOutOfSyncAlreadyShown() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = HealthKitTestFixtures.makeIntegrationInfo(isIntegrated: true, assignedTo: "101")
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let key = KvStorageKeys.scopedHealthKitModalKey(.outOfSyncAppleHealthModalBase, accountId: "101")
        kv.setValue(true, forKey: key)

        let sut = makeSUT(integrationService: integration, accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == nil)
    }

    @Test("shouldShowHKIntegrationModal addIntegration with no account: returns nil")
    func shouldShowHKModalAddIntegrationNoAccount() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = nil
        integration.isIntegrationAlreadyUsedResult = false
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = nil

        let sut = makeSUT(integrationService: integration, accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == nil)
    }

    @Test("shouldShowHKIntegrationModal finishAdding used by another account: returns nil")
    func shouldShowHKModalFinishAddingUsedByAnotherAccount() async throws {
        let integration = MockIntegrationService()
        integration.getStoredIntegrationDataResult = nil
        integration.isIntegrationAlreadyUsedResult = true
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(integrationService: integration, accountService: accountService, healthKitHandler: handler)

        let result = try await sut.shouldShowHKIntegrationModal()

        #expect(result == nil)
    }

    // MARK: - setWaitingForPermissionsRestored / clearWaitingForPermissionsRestored

    @Test("setWaitingForPermissionsRestored sets kv flag")
    func setWaitingForPermissionsRestored() {
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(accountService: accountService, kvStore: kv)
        sut.setWaitingForPermissionsRestored()

        let key = KvStorageKeys.scopedHealthKitModalKey(.waitingForHKPermissionsRestoredBase, accountId: "101")
        #expect((kv.getValue(forKey: key) as? Bool) == true)
    }

    @Test("clearWaitingForPermissionsRestored clears kv flag")
    func clearWaitingForPermissionsRestored() {
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let key = KvStorageKeys.scopedHealthKitModalKey(.waitingForHKPermissionsRestoredBase, accountId: "101")
        kv.setValue(true, forKey: key)

        let sut = makeSUT(accountService: accountService, kvStore: kv)
        sut.clearWaitingForPermissionsRestored()

        #expect(kv.getValue(forKey: key) == nil)
    }

    // MARK: - checkIfPermissionsRestoredAfterOutOfSync

    @Test("checkIfPermissionsRestoredAfterOutOfSync not waiting: returns false")
    func checkIfPermissionsRestoredNotWaiting() async {
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)

        let sut = makeSUT(accountService: accountService, kvStore: kv)

        let result = await sut.checkIfPermissionsRestoredAfterOutOfSync()

        #expect(result == false)
    }

    @Test("checkIfPermissionsRestoredAfterOutOfSync waiting and permissions restored: clears flag, returns true")
    func checkIfPermissionsRestoredRestored() async {
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let key = KvStorageKeys.scopedHealthKitModalKey(.waitingForHKPermissionsRestoredBase, accountId: "101")
        kv.setValue(true, forKey: key)
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = ["HKQuantityTypeIdentifierBodyMass"]

        let sut = makeSUT(accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = await sut.checkIfPermissionsRestoredAfterOutOfSync()

        #expect(result == true)
        #expect(kv.getValue(forKey: key) == nil)
    }

    @Test("checkIfPermissionsRestoredAfterOutOfSync waiting but not restored: returns false")
    func checkIfPermissionsRestoredStillNotRestored() async {
        let kv = MockKvStorageService()
        let accountService = MockAccountService()
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "101", email: "u@ex.com", isLoggedIn: true, isActiveAccount: true)
        let key = KvStorageKeys.scopedHealthKitModalKey(.waitingForHKPermissionsRestoredBase, accountId: "101")
        kv.setValue(true, forKey: key)
        let handler = MockHealthKitHandler()
        handler.getApprovedPermissionListReturn = []

        let sut = makeSUT(accountService: accountService, kvStore: kv, healthKitHandler: handler)

        let result = await sut.checkIfPermissionsRestoredAfterOutOfSync()

        #expect(result == false)
    }

    // MARK: - Helpers

    private func makeSUT(
        integrationService: MockIntegrationService? = nil,
        logger: MockLoggerService? = nil,
        accountService: MockAccountService? = nil,
        entryService: MockEntryService? = nil,
        kvStore: MockKvStorageService? = nil,
        healthKitHandler: MockHealthKitHandler? = nil
    ) -> HealthKitService {
        HealthKitService(
            integrationService: integrationService ?? MockIntegrationService(),
            logger: logger ?? MockLoggerService(),
            accountService: accountService ?? MockAccountService(),
            entryService: entryService ?? MockEntryService(),
            kvStore: kvStore ?? MockKvStorageService(),
            healthKitHandler: healthKitHandler ?? MockHealthKitHandler()
        )
    }

    private func persistEntries(_ entries: [Entry]) throws {
        let context = PersistenceController.shared.context
        for entry in entries {
            context.insert(entry)
        }
        try context.save()
    }
}

enum HealthKitTestError: Error, Equatable {
    case persistenceFailed
}
