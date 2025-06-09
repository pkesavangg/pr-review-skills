import Foundation

/// Protocol defining the service interface for managing feed items, including fetching, updating, and managing feed settings.
@MainActor
protocol FeedServiceProtocol {
    // MARK: - Published Properties
    
    /// The current list of feed items
    var feedItems: [FeedItem] { get }
    
    /// The current feed settings
    var feedSettings: FeedSetting? { get }
    
    // MARK: - Feed Items Management
    
    /// Fetches all feed items from the backend.
    /// - Returns: Array of FeedItem objects
    func fetchFeedItems() async throws
    
    /// Updates a feed item's state with the given action.
    /// - Parameters:
    ///   - feedItem: The feed item to update
    ///   - actionType: The type of action (read, trigger, click, etc.)
    ///   - variationId: Optional variation ID for certain action types
    func updateFeedItem(_ feedItem: FeedItem, actionType: FeedActionType, variationId: Int?) async throws
    
    /// Gets the count of unread feed items.
    /// - Returns: Number of unread feed items
    func getUnreadFeedCount() -> Int
    
    // MARK: - Feed Settings Management
    
    /// Checks and initializes feed notification settings if not already set.
    func initializeFeedSettings() async throws
    
    /// Stores feed notification settings.
    /// - Parameter settings: The feed settings to store
    func storeFeedSettings(_ settings: FeedSetting) async throws
    
    /// Gets the stored feed notification settings.
    /// - Returns: FeedSetting if exists, nil otherwise
    func getFeedSettings() async throws -> FeedSetting?
    
    // MARK: - Feed Modal Management
    
    /// Checks if a feed modal should be triggered and shows it if appropriate.
    /// - Returns: True if modal was shown, false otherwise
    func checkAndShowFeedModal() async throws -> Bool
    
    /// Gets the last triggered timestamp for feed modal.
    /// - Returns: Timestamp string if exists, nil otherwise
    func getLastTriggeredTimestamp() async throws -> String?
    
    /// Sets the last triggered timestamp for feed modal.
    /// - Parameter timestamp: The timestamp to store
    func setLastTriggeredTimestamp(_ timestamp: String) async throws
    // MARK: - Feed Modal Presentation
    
    /// Shows a feed modal with the given feed item.
    /// - Parameter feedItem: The feed item to display in the modal
    /// - Returns: True if modal was shown successfully
    func showFeedModal(_ feedItem: FeedItem) async throws -> Bool
    
    // MARK: - Cleanup
    
    /// Clears all feed data for the current user.
    func clearFeedData() async throws
}
