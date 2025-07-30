import Foundation

/// Protocol defining the service interface for managing feed items, including fetching, updating, and managing feed settings.
@MainActor
protocol FeedServiceProtocol {
    // MARK: - Published Properties

    /// The current list of feed items
    var feedItems: [FeedItem] { get }

    // MARK: - Feed Items Management

    /// Fetches all feed items from the backend and updates local state.
    func fetchFeedItems() async

    /// Updates a feed item's state with the given action.
    /// - Parameters:
    ///   - feedItem: The feed item to update
    ///   - actionType: The type of action (read, trigger, click, etc.)
    ///   - variationId: Optional variation ID for certain action types
    func updateFeedItem(_ feedItem: FeedItem, actionType: GGFeedActionType, variationId: Int?) async throws

    /// Gets the count of unread feed items.
    /// - Returns: Number of unread feed items
    func getUnreadFeedCount() -> Int

    // MARK: - Feed Settings Management

    /// Gets the stored feed notification settings.
    /// - Returns: FeedSetting if exists, nil otherwise
    func getFeedSettings() async throws -> GGFeedSetting?

    // MARK: - Feed Modal Management

    /// Shows a feed modal, if applicable.
    /// - Returns: True if modal was shown
    func showFeedModal() async throws -> Bool

    // MARK: - Cleanup

    /// Clears all feed data for the current user.
    func clearFeedData()
}
