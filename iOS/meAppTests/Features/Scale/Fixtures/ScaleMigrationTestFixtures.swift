import Foundation
@testable import meApp

enum ScaleMigrationTestFixtures {
    @MainActor
    final class Harness {
        var existingById: [String: Device] = [:]
        var created: [Device] = []
        var failCreateIDs: Set<String> = []
        var failLookupIDs: Set<String> = []

        private(set) var createCalls = 0
        private(set) var syncCalls = 0

        func create(_ device: Device) async throws -> Device {
            createCalls += 1
            if failCreateIDs.contains(device.id) {
                throw FixtureError.createFailed(device.id)
            }
            existingById[device.id] = device
            created.append(device)
            return device
        }

        func fetch(_ id: String) async throws -> Device? {
            if failLookupIDs.contains(id) {
                throw FixtureError.lookupFailed(id)
            }
            return existingById[id]
        }

        func sync() async {
            syncCalls += 1
        }
    }

    enum FixtureError: Error {
        case createFailed(String)
        case lookupFailed(String)
    }
}
