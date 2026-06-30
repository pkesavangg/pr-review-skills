import Foundation
@testable import meApp
import Testing

extension HealthKitServiceTests {

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
        accountService.activeAccount = AccountTestFixtures.makeAccountSnapshot(
            id: "101",
            email: "u@ex.com",
            isLoggedIn: true,
            isActiveAccount: true,
            isHealthKitOn: true
        )

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
}
