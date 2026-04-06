import Foundation
import ggHealthKitPackage
@testable import meApp

@MainActor
final class MockHealthKitHandler: HealthKitHandlerProtocol {
    var availableResult = true
    var requestAuthorizationResult = true
    var getApprovedPermissionListReturn: [String] = ["HKQuantityTypeIdentifierBodyMass"]
    var saveDataError: Error?
    var deleteEntryError: Error?
    var deleteAllDataError: Error?

    private(set) var availableCalls = 0
    private(set) var requestAuthorizationCalls = 0
    private(set) var getApprovedPermissionListCalls = 0
    private(set) var saveDataCalls = 0
    private(set) var saveDataLastPayloadCount = 0
    private(set) var deleteEntryCalls = 0
    private(set) var deleteAllDataCalls = 0
    private(set) var openAppleHealthCalls = 0

    func available() -> Bool {
        availableCalls += 1
        return availableResult
    }

    func requestAuthorization() async -> Bool {
        requestAuthorizationCalls += 1
        return requestAuthorizationResult
    }

    func getApprovedPermissionList() -> [String] {
        getApprovedPermissionListCalls += 1
        return getApprovedPermissionListReturn
    }

    func saveData(_ data: [HealthKitData]) async throws {
        saveDataCalls += 1
        saveDataLastPayloadCount = data.count
        if let error = saveDataError { throw error }
    }

    func deleteEntry(_ data: [HealthKitData]) async throws {
        deleteEntryCalls += 1
        if let error = deleteEntryError { throw error }
    }

    func deleteAllData() async throws {
        deleteAllDataCalls += 1
        if let error = deleteAllDataError { throw error }
    }

    func openAppleHealth() async {
        openAppleHealthCalls += 1
    }

    private(set) var updateAppTypeCalls = 0
    private(set) var lastDeviceTypes: Set<String>?

    func updateAppType(for deviceTypes: Set<String>) {
        updateAppTypeCalls += 1
        lastDeviceTypes = deviceTypes
    }
}
