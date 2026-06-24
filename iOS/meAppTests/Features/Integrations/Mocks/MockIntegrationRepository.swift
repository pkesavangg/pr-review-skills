import Foundation
@testable import meApp

@MainActor
final class MockIntegrationRepository: IntegrationRepositoryProtocol {
    var getIntegrationDataResult: IntegrationInfo?
    var getIntegrationDataError: Error?
    var setIntegrationDataError: Error?
    var isIntegrationAlreadyUsedResult = false
    var isIntegrationAlreadyUsedError: Error?
    var clearIntegrationStatusError: Error?

    private(set) var getIntegrationDataCalls = 0
    private(set) var setIntegrationDataCalls = 0
    private(set) var isIntegrationAlreadyUsedCalls = 0
    private(set) var clearIntegrationStatusCalls = 0

    private(set) var lastGetAccountId: String?
    private(set) var lastSetAccountId: String?
    private(set) var lastSetInfo: IntegrationInfo?
    private(set) var lastCheckAccountId: String?
    private(set) var lastCheckType: IntegrationType?
    private(set) var lastClearAccountId: String?

    func getIntegrationData(accountId: String) async throws -> IntegrationInfo? {
        getIntegrationDataCalls += 1
        lastGetAccountId = accountId
        if let getIntegrationDataError {
            throw getIntegrationDataError
        }
        return getIntegrationDataResult
    }

    func setIntegrationData(accountId: String, info: IntegrationInfo?) async throws {
        setIntegrationDataCalls += 1
        lastSetAccountId = accountId
        lastSetInfo = info
        if let setIntegrationDataError {
            throw setIntegrationDataError
        }
    }

    func isIntegrationAlreadyUsed(accountId: String, type: IntegrationType) async throws -> Bool {
        isIntegrationAlreadyUsedCalls += 1
        lastCheckAccountId = accountId
        lastCheckType = type
        if let isIntegrationAlreadyUsedError {
            throw isIntegrationAlreadyUsedError
        }
        return isIntegrationAlreadyUsedResult
    }

    func clearIntegrationStatus(accountId: String) async throws {
        clearIntegrationStatusCalls += 1
        lastClearAccountId = accountId
        if let clearIntegrationStatusError {
            throw clearIntegrationStatusError
        }
    }
}
