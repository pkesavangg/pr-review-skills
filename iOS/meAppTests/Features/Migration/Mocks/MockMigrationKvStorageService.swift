import Foundation
@testable import meApp

@MainActor
final class MockMigrationKvStorageService: KvStorageServiceProtocol {
    private(set) var store: [String: Any] = [:]
    private(set) var setValueCalls: [(key: String, value: Any)] = []
    private(set) var clearValueCalls: [String] = []

    // MARK: - KvStorageServiceProtocol

    func getValue(forKey key: String) -> Any? {
        return store[key]
    }

    func setValue(_ value: Any, forKey key: String) {
        store[key] = value
        setValueCalls.append((key: key, value: value))
    }

    func clearValue(forKey key: String) {
        store.removeValue(forKey: key)
        clearValueCalls.append(key)
    }

    func clearAll() {
        store.removeAll()
    }

    func setCodable<T: Codable>(_ value: T, forKey key: String) {
        if let data = try? JSONEncoder().encode(value) {
            setValue(data, forKey: key)
        }
    }

    func getCodable<T: Codable>(forKey key: String, as type: T.Type) -> T? {
        if let data = getValue(forKey: key) as? Data {
            return try? JSONDecoder().decode(type, from: data)
        }
        return nil
    }

    func getAllKeys() -> [String] {
        return Array(store.keys)
    }

    // MARK: - Test Helpers

    func seed(_ value: Any, forKey key: String) {
        store[key] = value
    }

    func hasSetValue(forKey key: String) -> Bool {
        return setValueCalls.contains { $0.key == key }
    }

    func lastSetValue(forKey key: String) -> Any? {
        return setValueCalls.last(where: { $0.key == key })?.value
    }

    func hasClearedValue(forKey key: String) -> Bool {
        return clearValueCalls.contains { $0 == key }
    }

    func contains(key: String) -> Bool {
        return store[key] != nil
    }
}
