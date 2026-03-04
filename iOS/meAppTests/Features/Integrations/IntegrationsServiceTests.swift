import Foundation
import Testing
@testable import meApp

@MainActor
struct IntegrationsServiceTests {

    // MARK: - getIntegrationUrl

    @Test("getIntegrationUrl healthKit: returns expected URL")
    func getIntegrationUrlHealthKit() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)

        let sut = makeSUT(account: account)
        let url = try await sut.getIntegrationUrl(.healthKit)

        #expect(url == "\(API.baseURL)/health-kit/101")
    }

    @Test("getIntegrationUrl fitbit: returns expected URL")
    func getIntegrationUrlFitbit() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "202", email: "u@ex.com", isLoggedIn: true, isActive: true)

        let sut = makeSUT(account: account)
        let url = try await sut.getIntegrationUrl(.fitbit)

        #expect(url == "\(API.baseURL)/fitbit/202")
    }

    @Test("getIntegrationUrl no active account: throws noActiveAccount")
    func getIntegrationUrlNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            _ = try await sut.getIntegrationUrl(.healthKit)
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - removeIntegration

    @Test("removeIntegration success: calls API and clears local integration data")
    func removeIntegrationSuccess() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let api = MockIntegrationsAPIRepository()
        let local = MockIntegrationRepository()

        let sut = makeSUT(account: account, api: api, local: local)
        try await sut.removeIntegration(.healthKit)

        #expect(api.removeIntegrationCalls == 1)
        #expect(api.lastRemoveAccountId == "101")
        #expect(api.lastRemoveProvider == .healthKit)
        #expect(local.setIntegrationDataCalls == 1)
        #expect(local.lastSetAccountId == "101")
        #expect(local.lastSetInfo == nil)
    }

    @Test("removeIntegration API error: rethrows and does not clear local")
    func removeIntegrationApiError() async {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let api = MockIntegrationsAPIRepository()
        api.removeIntegrationError = IntegrationTestError.apiFailed
        let local = MockIntegrationRepository()

        let sut = makeSUT(account: account, api: api, local: local)

        do {
            try await sut.removeIntegration(.healthKit)
            Issue.record("Expected throw")
        } catch {
            #expect(error as? IntegrationTestError == .apiFailed)
        }
        #expect(local.setIntegrationDataCalls == 0)
    }

    @Test("removeIntegration no active account: throws noActiveAccount")
    func removeIntegrationNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            try await sut.removeIntegration(.healthKit)
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - getStoredIntegrationData / setStoredIntegrationData

    @Test("getStoredIntegrationData success: returns local data")
    func getStoredIntegrationDataSuccess() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")

        let sut = makeSUT(account: account, local: local)
        let result = try await sut.getStoredIntegrationData()

        #expect(result == IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101"))
        #expect(local.getIntegrationDataCalls == 1)
        #expect(local.lastGetAccountId == "101")
    }

    @Test("getStoredIntegrationData missing account: throws noActiveAccount")
    func getStoredIntegrationDataNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            _ = try await sut.getStoredIntegrationData()
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    @Test("setStoredIntegrationData with info: stores local and updates account integrations")
    func setStoredIntegrationDataWithInfoSuccess() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        account.updateIntegrationsResult = .success(account.activeAccount!)
        let local = MockIntegrationRepository()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")

        let sut = makeSUT(account: account, local: local)
        try await sut.setStoredIntegrationData(info)

        #expect(local.setIntegrationDataCalls == 1)
        #expect(local.lastSetAccountId == "101")
        #expect(local.lastSetInfo == info)
        #expect(account.updateIntegrationsCalls == 1)
        #expect(account.lastIntegrationType == .healthKit)
    }

    @Test("setStoredIntegrationData nil info: stores nil and does not call account update")
    func setStoredIntegrationDataNilInfo() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()

        let sut = makeSUT(account: account, local: local)
        try await sut.setStoredIntegrationData(nil)

        #expect(local.setIntegrationDataCalls == 1)
        #expect(local.lastSetInfo == nil)
        #expect(account.updateIntegrationsCalls == 0)
    }

    @Test("setStoredIntegrationData local save error: rethrows")
    func setStoredIntegrationDataLocalError() async {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.setIntegrationDataError = IntegrationTestError.persistenceFailed
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")

        let sut = makeSUT(account: account, local: local)

        do {
            try await sut.setStoredIntegrationData(info)
            Issue.record("Expected throw")
        } catch {
            #expect(error as? IntegrationTestError == .persistenceFailed)
        }
        #expect(account.updateIntegrationsCalls == 0)
    }

    @Test("setStoredIntegrationData account update error: does not rethrow")
    func setStoredIntegrationDataAccountUpdateError() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        account.updateIntegrationsResult = .failure(IntegrationTestError.apiFailed)
        let local = MockIntegrationRepository()
        let info = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")

        let sut = makeSUT(account: account, local: local)
        try await sut.setStoredIntegrationData(info)

        #expect(local.setIntegrationDataCalls == 1)
        #expect(account.updateIntegrationsCalls == 1)
    }

    @Test("setStoredIntegrationData missing account: throws noActiveAccount")
    func setStoredIntegrationDataNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            try await sut.setStoredIntegrationData(IntegrationTestFixtures.makeIntegrationInfo())
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - isIntegrationAlreadyUsed

    @Test("isIntegrationAlreadyUsed returns local result")
    func isIntegrationAlreadyUsedSuccess() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.isIntegrationAlreadyUsedResult = true

        let sut = makeSUT(account: account, local: local)
        let result = try await sut.isIntegrationAlreadyUsed(type: .healthKit)

        #expect(result == true)
        #expect(local.isIntegrationAlreadyUsedCalls == 1)
        #expect(local.lastCheckAccountId == "101")
        #expect(local.lastCheckType == .healthKit)
    }

    @Test("isIntegrationAlreadyUsed missing account: throws noActiveAccount")
    func isIntegrationAlreadyUsedNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            _ = try await sut.isIntegrationAlreadyUsed(type: .healthKit)
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - clearIntegrationStatus

    @Test("clearIntegrationStatus success: stores de-integrated state and calls deleteHealthIntegration")
    func clearIntegrationStatusSuccess() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        account.updateIntegrationsResult = .success(account.activeAccount!)
        account.deleteHealthIntegrationResult = .success(())
        let local = MockIntegrationRepository()

        let sut = makeSUT(account: account, local: local)
        try await sut.clearIntegrationStatus(integrationType: .healthKit)

        #expect(local.setIntegrationDataCalls == 1)
        #expect(local.lastSetInfo?.type == .healthKit)
        #expect(local.lastSetInfo?.isIntegrated == false)
        #expect(account.deleteHealthIntegrationCalls == 1)
        #expect(account.lastDeletedHealthIntegrationType == .healthKit)
    }

    @Test("clearIntegrationStatus missing account: throws noActiveAccount")
    func clearIntegrationStatusNoActiveAccount() async {
        let account = MockAccountService()
        account.activeAccount = nil
        let sut = makeSUT(account: account)

        do {
            try await sut.clearIntegrationStatus(integrationType: .healthKit)
            Issue.record("Expected AccountError.noActiveAccount")
        } catch {
            assertNoActiveAccount(error)
        }
    }

    // MARK: - Entry Sync + Log Flows

    @Test("syncNewEntry no active integration: does not call HealthKit sync")
    func syncNewEntryNoIntegration() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = nil
        let healthKit = MockHealthKitServiceForIntegrations()
        let entry = HealthKitTestFixtures.makeEntry(accountId: "101")

        let sut = makeSUT(account: account, local: local, healthKit: healthKit)
        try await sut.syncNewEntry(entry)

        #expect(healthKit.syncNewDataNotificationCalls == 0)
    }

    @Test("syncNewEntry healthKit integrated: forwards entry to HealthKit")
    func syncNewEntryHealthKitIntegrated() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")
        let healthKit = MockHealthKitServiceForIntegrations()
        let entry = HealthKitTestFixtures.makeEntry(accountId: "101")

        let sut = makeSUT(account: account, local: local, healthKit: healthKit)
        try await sut.syncNewEntry(entry)

        #expect(healthKit.syncNewDataNotificationCalls == 1)
    }

    @Test("deleteEntry healthKit integrated: forwards deletion to HealthKit")
    func deleteEntryHealthKitIntegrated() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")
        let healthKit = MockHealthKitServiceForIntegrations()
        let entry = HealthKitTestFixtures.makeEntry(accountId: "101")

        let sut = makeSUT(account: account, local: local, healthKit: healthKit)
        try await sut.deleteEntry(entry)

        #expect(healthKit.deleteEntryNotificationCalls == 1)
    }

    @Test("clearIntegration no active integration: returns without clearing HealthKit")
    func clearIntegrationNoActiveIntegration() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = nil
        let healthKit = MockHealthKitServiceForIntegrations()

        let sut = makeSUT(account: account, local: local, healthKit: healthKit)
        try await sut.clearIntegration()

        #expect(healthKit.clearHealthKitCalls == 0)
    }

    @Test("clearIntegration healthKit active: clears HealthKit data")
    func clearIntegrationHealthKitActive() async throws {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")
        let healthKit = MockHealthKitServiceForIntegrations()

        let sut = makeSUT(account: account, local: local, healthKit: healthKit)
        try await sut.clearIntegration()

        #expect(healthKit.clearHealthKitCalls == 1)
    }

    @Test("logHealthEntry healthKit active with permissions: logs via API")
    func logHealthEntryHealthKitWithPermissions() async {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let api = MockIntegrationsAPIRepository()
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")
        let healthKit = MockHealthKitServiceForIntegrations()
        healthKit.permissions = ["HKQuantityTypeIdentifierBodyMass"]
        let notification = HealthKitTestFixtures.makeEntryNotification(accountId: "101")

        let sut = makeSUT(account: account, api: api, local: local, healthKit: healthKit)
        await sut.logHealthEntry(notification: notification)

        #expect(api.logHealthIntegrationCalls == 1)
        #expect(api.lastLogType == .healthKit)
        #expect(api.lastLogTimestamp == notification.entryTimestamp)
    }

    @Test("logHealthEntry healthKit active with no permissions: does not call API")
    func logHealthEntryNoPermissions() async {
        let account = MockAccountService()
        account.activeAccount = AccountTestFixtures.makeAccountModel(id: "101", email: "u@ex.com", isLoggedIn: true, isActive: true)
        let api = MockIntegrationsAPIRepository()
        let local = MockIntegrationRepository()
        local.getIntegrationDataResult = IntegrationTestFixtures.makeIntegrationInfo(type: .healthKit, isIntegrated: true, assignedTo: "101")
        let healthKit = MockHealthKitServiceForIntegrations()
        healthKit.permissions = []
        let notification = HealthKitTestFixtures.makeEntryNotification(accountId: "101")

        let sut = makeSUT(account: account, api: api, local: local, healthKit: healthKit)
        await sut.logHealthEntry(notification: notification)

        #expect(api.logHealthIntegrationCalls == 0)
    }

    // MARK: - Helpers

    private func makeSUT(
        account: MockAccountService? = nil,
        logger: MockLoggerService? = nil,
        entry: MockEntryService? = nil,
        api: MockIntegrationsAPIRepository? = nil,
        local: MockIntegrationRepository? = nil,
        healthKit: MockHealthKitServiceForIntegrations? = nil
    ) -> IntegrationsService {
        IntegrationsService(
            apiRepository: api ?? MockIntegrationsAPIRepository(),
            localRepository: local ?? MockIntegrationRepository(),
            accountService: account ?? MockAccountService(),
            logger: logger ?? MockLoggerService(),
            entryService: entry ?? MockEntryService(),
            healthKitService: healthKit ?? MockHealthKitServiceForIntegrations(),
            observeEntrySaved: false
        )
    }

    private func assertNoActiveAccount(_ error: Error) {
        guard case AccountError.noActiveAccount = error else {
            Issue.record("Expected AccountError.noActiveAccount, got: \(error)")
            return
        }
    }
}

enum IntegrationTestError: Error, Equatable {
    case apiFailed
    case persistenceFailed
}
