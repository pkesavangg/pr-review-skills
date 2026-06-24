//
//  MockIntegrationService.swift
//  meAppTests
//

import Foundation
@testable import meApp

@MainActor
final class MockIntegrationService: IntegrationServiceProtocol {

    // MARK: - removeIntegration
    var removeIntegrationError: Error?
    var removeIntegrationCallCount = 0
    var lastRemovedProvider: IntegrationType?

    func removeIntegration(_ provider: IntegrationType) async throws {
        removeIntegrationCallCount += 1
        lastRemovedProvider = provider
        if let error = removeIntegrationError { throw error }
    }

    // MARK: - getIntegrationUrl
    var getIntegrationUrlResult: String = "https://mock.integration.url"
    var getIntegrationUrlError: Error?

    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String {
        if let error = getIntegrationUrlError { throw error }
        return getIntegrationUrlResult
    }

    // MARK: - getStoredIntegrationData
    var getStoredIntegrationDataResult: IntegrationInfo?

    func getStoredIntegrationData() async throws -> IntegrationInfo? {
        return getStoredIntegrationDataResult
    }

    // MARK: - setStoredIntegrationData
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {}

    // MARK: - isIntegrationAlreadyUsed
    var isIntegrationAlreadyUsedResult = false

    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool {
        return isIntegrationAlreadyUsedResult
    }

    // MARK: - clearIntegrationStatus
    func clearIntegrationStatus(integrationType: IntegrationType) async throws {}

    // MARK: - syncNewEntry
    func syncNewEntry(_ entry: Entry) async throws {}

    func syncNewEntry(notification: EntryNotification) async throws {}

    // MARK: - deleteEntry
    func deleteEntry(_ entry: Entry) async throws {}

    // MARK: - clearIntegration
    func clearIntegration() async throws {}
}
