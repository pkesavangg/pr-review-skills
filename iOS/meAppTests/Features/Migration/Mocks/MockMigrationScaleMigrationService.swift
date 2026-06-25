import Foundation
@testable import meApp

@MainActor
final class MockMigrationScaleMigrationService: DeviceMigrationServiceProtocol {
    var isMigrationNeededResult = false
    var migrateScaleDataResult: Result<[Device], Error> = .success([])

    private(set) var isMigrationNeededCalls: [String] = []
    private(set) var migrateScaleDataCalls: [String] = []
    private(set) var cleanupAfterMigrationCalls: [String] = []

    func isMigrationNeeded(for accountId: String) -> Bool {
        isMigrationNeededCalls.append(accountId)
        return isMigrationNeededResult
    }

    func migrateScaleData(for accountId: String) async throws -> [Device] {
        migrateScaleDataCalls.append(accountId)
        return try migrateScaleDataResult.get()
    }

    func cleanupAfterMigration(for accountId: String) {
        cleanupAfterMigrationCalls.append(accountId)
    }
}
