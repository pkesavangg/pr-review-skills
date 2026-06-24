import Foundation
@testable import meApp

/// In-memory key-value store for tests. Thread-safe for single-threaded test use.
final class MockKvStorageService: KvStorageServiceProtocol {
    private var storage: [String: Any] = [:]

    func getValue(forKey key: String) -> Any? {
        storage[key]
    }

    func setValue(_ value: Any, forKey key: String) {
        storage[key] = value
    }

    func clearValue(forKey key: String) {
        storage.removeValue(forKey: key)
    }

    func clearAll() {
        storage.removeAll()
    }

    func setCodable<T: Codable>(_ value: T, forKey key: String) {
        storage[key] = value
    }

    func getCodable<T: Codable>(forKey key: String, as type: T.Type) -> T? {
        storage[key] as? T
    }

    func getAllKeys() -> [String] {
        Array(storage.keys)
    }
}
