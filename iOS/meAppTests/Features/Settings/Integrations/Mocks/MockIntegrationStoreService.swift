import Foundation
@testable import meApp

enum IntegrationStoreTestError: Error, Equatable {
    case removeFailed
    case refreshFailed
}

@MainActor
final class MockIntegrationStoreService: IntegrationServiceProtocol {
    var removeIntegrationError: Error?
    var removeDelayNanoseconds: UInt64 = 0
    var requestNewIntegrationError: Error?
    private(set) var requestNewIntegrationCalls: [String] = []

    private(set) var removeIntegrationCalls: [IntegrationType] = []

    func getIntegrationUrl(_ provider: IntegrationType) async throws -> String { "" }

    func removeIntegration(_ provider: IntegrationType) async throws {
        removeIntegrationCalls.append(provider)
        if removeDelayNanoseconds > 0 {
            try? await Task.sleep(nanoseconds: removeDelayNanoseconds)
        }
        if let removeIntegrationError {
            throw removeIntegrationError
        }
    }

    func getStoredIntegrationData() async throws -> IntegrationInfo? { nil }
    func setStoredIntegrationData(_ info: IntegrationInfo?) async throws {}
    func isIntegrationAlreadyUsed(type: IntegrationType) async throws -> Bool { false }
    func clearIntegrationStatus(integrationType: IntegrationType) async throws {}
    func syncNewEntry(_ entry: Entry) async throws {}
    func syncNewEntry(notification: EntryNotification) async throws {}
    func deleteEntry(_ entry: Entry) async throws {}
    func deleteEntry(notification: EntryNotification) async throws {}
    func clearIntegration() async throws {}
    func logHealthEntry(notification: EntryNotification) async {}
    func requestNewIntegration(text: String) async throws {
        requestNewIntegrationCalls.append(text)
        if let requestNewIntegrationError { throw requestNewIntegrationError }
    }
}
