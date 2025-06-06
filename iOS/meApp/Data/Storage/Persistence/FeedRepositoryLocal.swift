import Foundation

/// Repository for managing local feed data and settings using KvStorageService.
final class FeedRepositoryLocal {
    private let kv: KvStorageService = KvStorageService.shared
    
    private let feedStorageKey = "feedData"
    
    /// Gets the feed data for the given account.
    /// - Parameter accountId: The account ID
    /// - Returns: FeedLocalData if exists, empty object otherwise
    private func getFeedData(accountId: String) throws -> FeedLocalData {
        let key = makeFeedDataKey(accountId: accountId)
        guard let value = kv.getValue(forKey: key) as? String,
              let data = value.data(using: .utf8) else {
            return FeedLocalData()
        }
        guard let feedData = try? JSONDecoder().decode(FeedLocalData.self, from: data) else {
            throw FeedError.localStorageDecodingFailed
        }
        return feedData
    }
    
    /// Stores feed data for the given account.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - feedData: The feed data to store
    private func storeFeedData(accountId: String, feedData: FeedLocalData) throws {
        let key = makeFeedDataKey(accountId: accountId)
        let data = try JSONEncoder().encode(feedData)
        guard let value = String(data: data, encoding: .utf8) else {
            throw FeedError.localStorageEncodingFailed
        }
        kv.setValue(value, forKey: key)
    }
    
    /// Gets the feed notification settings for the given account.
    /// - Parameter accountId: The account ID
    /// - Returns: FeedSetting if exists, nil otherwise
    func getFeedSettings(accountId: String) async throws -> FeedSetting? {
        let feedData = try getFeedData(accountId: accountId)
        return feedData.settings
    }
    
    /// Stores feed notification settings for the given account.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - settings: The feed settings to store
    func storeFeedSettings(accountId: String, settings: FeedSetting) async throws {
        var feedData = try getFeedData(accountId: accountId)
        feedData.settings = settings
        try storeFeedData(accountId: accountId, feedData: feedData)
    }
    
    /// Gets the last triggered timestamp for feed modal for the given account.
    /// - Parameter accountId: The account ID
    /// - Returns: Timestamp string if exists, nil otherwise
    func getLastTriggeredTimestamp(accountId: String) async throws -> String? {
        let feedData = try getFeedData(accountId: accountId)
        return feedData.lastTriggeredTimestamp
    }
    
    /// Sets the last triggered timestamp for feed modal for the given account.
    /// - Parameters:
    ///   - accountId: The account ID
    ///   - timestamp: The timestamp to store
    func setLastTriggeredTimestamp(accountId: String, timestamp: String) async throws {
        var feedData = try getFeedData(accountId: accountId)
        feedData.lastTriggeredTimestamp = timestamp
        try storeFeedData(accountId: accountId, feedData: feedData)
    }
    
    /// Clears all feed data for the given account.
    /// - Parameter accountId: The account ID
    func clearFeedData(accountId: String) async throws {
        let key = makeFeedDataKey(accountId: accountId)
        kv.clearValue(forKey: key)
    }
    
    // MARK: - Private Helpers
    
    private func makeFeedDataKey(accountId: String) -> String {
        return "\(feedStorageKey)_\(accountId)"
    }
}
