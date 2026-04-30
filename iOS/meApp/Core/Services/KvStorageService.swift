import Foundation

class KvStorageService {
    static let shared = KvStorageService()

    init() {}

    func getValue(forKey key: String) -> Any? {
        return UserDefaults.standard.object(forKey: key)
    }

    func setValue(_ value: Any, forKey key: String) {
        UserDefaults.standard.set(value, forKey: key)
    }

    func clearValue(forKey key: String) {
        UserDefaults.standard.removeObject(forKey: key)
    }

    func clearAll() {
        if let appDomain = Bundle.main.bundleIdentifier {
            UserDefaults.standard.removePersistentDomain(forName: appDomain)
        }
    }
}

extension KvStorageService {
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
}