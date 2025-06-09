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
