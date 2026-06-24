import Foundation
@testable import meApp

// MARK: - Test-only protocol defaults
//
// The MOB-535 device rename and related MeApp 2.0 work added several new
// requirements to these protocols. Many lightweight test mocks don't exercise
// the new members, so these default implementations let those mocks keep
// conforming without each having to stub every new method. Mocks that DO need
// custom behaviour still override these (a concrete implementation always wins
// over a protocol-extension default).

private enum TestProtocolDefaultError: Error {
    case notImplemented(String)
}

extension EntryServiceProtocol {
    func getAllEntriesAsSnapshots() async throws -> [EntrySnapshot] { [] }
}

extension EntryRepositoryProtocol {
    func markEntryAsDeleted(byId id: String) async throws {}
    func updateEntryServerEntryId(entryId: String, serverEntryId: String) async throws {}
    func fetchUnsyncedEntriesAsSnapshots(forUserId userId: String) async throws -> [(EntrySnapshot, BathScaleOperationDTO)] { [] }
}

extension DeviceServiceProtocol {
    func listPairedDevices(deviceType: DeviceType?) async throws -> [PairedDeviceResponse] { [] }
    func createPairedDevice(_ request: PairedDeviceRequest) async throws -> PairedDeviceResponse {
        throw TestProtocolDefaultError.notImplemented("createPairedDevice")
    }
    func updatePairedDevice(_ deviceId: String, nickname: String) async throws -> PairedDeviceResponse {
        throw TestProtocolDefaultError.notImplemented("updatePairedDevice")
    }
    func deletePairedDevice(_ deviceId: String) async throws {}
}
