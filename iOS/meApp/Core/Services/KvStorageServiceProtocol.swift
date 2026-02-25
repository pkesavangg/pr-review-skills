import Foundation

protocol KvStorageServiceProtocol {
    func getValue(forKey key: String) -> Any?
    func setValue(_ value: Any, forKey key: String)
    func clearValue(forKey key: String)
    func clearAll()
    func setCodable<T: Codable>(_ value: T, forKey key: String)
    func getCodable<T: Codable>(forKey key: String, as type: T.Type) -> T?
}

