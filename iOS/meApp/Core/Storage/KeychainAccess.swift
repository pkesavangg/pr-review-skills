import Foundation
import Security

protocol KeychainAccessing: Sendable {
    func add(_ query: [String: Any]) -> OSStatus
    func update(searchQuery: [String: Any], attributes: [String: Any]) -> OSStatus
    func copyMatching(_ query: [String: Any], result: inout AnyObject?) -> OSStatus
    func delete(_ query: [String: Any]) -> OSStatus
}

struct SystemKeychainAccess: KeychainAccessing {
    func add(_ query: [String: Any]) -> OSStatus {
        SecItemAdd(query as CFDictionary, nil)
    }

    func update(searchQuery: [String: Any], attributes: [String: Any]) -> OSStatus {
        SecItemUpdate(searchQuery as CFDictionary, attributes as CFDictionary)
    }

    func copyMatching(_ query: [String: Any], result: inout AnyObject?) -> OSStatus {
        SecItemCopyMatching(query as CFDictionary, &result)
    }

    func delete(_ query: [String: Any]) -> OSStatus {
        SecItemDelete(query as CFDictionary)
    }
}
