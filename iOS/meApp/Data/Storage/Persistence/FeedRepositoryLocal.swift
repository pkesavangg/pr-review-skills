import Foundation

/// Repository for managing local feed data and settings using KvStorageService.
final class FeedRepositoryLocal {
    private let kv: KvStorageService = KvStorageService.shared
    private let feedInfoKey = "feedInfo"
    private let feedLastTriggeredAtKey = "feedLastTriggeredAt"
    
    
    /// Gets the feed notification settings for the given account.
    /// - Parameter accountId: The account ID
    /// - Returns: FeedSetting if exists, nil otherwise
    func getFeedSettings(accountId: String) async throws -> FeedSetting? {
        let key = makeFeedInfoKey(accountId: accountId)
        guard let value = kv.getValue(forKey: key) as? String,
              let data = value.data(using: .utf8) else {
            throw FeedError.localStorageInvalidValue
        }
        guard let settings = try? JSONDecoder().decode(FeedSetting.self, from: data) else {
            throw FeedError.localStorageDecodingFailed
        }
        return settings
    }
    
    /// Stores feed notification settings for the given account.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - settings: The feed settings to store
    func storeFeedSettings(accountId: String, settings: FeedSetting) async throws {
        let key = makeFeedInfoKey(accountId: accountId)
        let data = try JSONEncoder().encode(settings)
        guard let value = String(data: data, encoding: .utf8) else {
            throw FeedError.localStorageEncodingFailed
        }
        kv.setValue(value, forKey: key)
    }
    
    /// Gets the last triggered timestamp for feed modal for the given account.
    /// - Parameter accountId: The account ID
    /// - Returns: Timestamp string if exists, nil otherwise
    func getLastTriggeredTimestamp(accountId: String) async throws -> String? {
        let key = makeLastTriggeredKey(accountId: accountId)
        return kv.getValue(forKey: key) as? String
    }
    
    /// Sets the last triggered timestamp for feed modal for the given account.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - timestamp: The timestamp to store
    func setLastTriggeredTimestamp(accountId: String, timestamp: String) async throws {
        let key = makeLastTriggeredKey(accountId: accountId)
        kv.setValue(timestamp, forKey: key)
    }
    
    /// Clears all feed data for the given account.
    /// - Parameter accountId: The account ID
    func clearFeedData(accountId: String) async throws {
        let feedInfoKeyForAccount = makeFeedInfoKey(accountId: accountId)
        let lastTriggeredKey = makeLastTriggeredKey(accountId: accountId)
        kv.clearValue(forKey: feedInfoKeyForAccount)
        kv.clearValue(forKey: lastTriggeredKey)
    }
    
    // MARK: - Private Helpers
    
    private func makeFeedInfoKey(accountId: String) -> String {
        return "\(feedInfoKey)_\(accountId)"
    }
    
    private func makeLastTriggeredKey(accountId: String) -> String {
        return "\(feedLastTriggeredAtKey)_\(accountId)"
    }
}
