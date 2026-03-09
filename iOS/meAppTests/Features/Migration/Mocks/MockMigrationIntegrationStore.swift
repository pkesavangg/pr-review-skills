import Foundation
@testable import meApp

@MainActor
final class MockMigrationIntegrationStore: MigrationIntegrationStoreProtocol {
    var setIntegrationDataError: Error?
    private(set) var setIntegrationDataCalls = 0
    private(set) var lastAccountId: String?
    private(set) var lastIntegrationInfo: IntegrationInfo?

    func setIntegrationData(accountId: String, info: IntegrationInfo?) throws {
        setIntegrationDataCalls += 1
        lastAccountId = accountId
        lastIntegrationInfo = info
        if let error = setIntegrationDataError { throw error }
    }
}
