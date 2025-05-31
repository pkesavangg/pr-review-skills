import Foundation

/// Protocol for business logic and orchestration related to feed items.
///
/// This protocol defines high-level operations for feed, including fetching feed items,
/// updating their state, and handling notification/subject logic as needed.
protocol FeedServiceProtocol {
    /// Fetches the latest feed items for the current user.
    /// - Returns: An array of FeedItem.
    func getFeedItems() async throws -> [FeedItem]

    /// Updates the state of a feed item (e.g., mark as read, trigger, click).
    /// - Parameters:
    ///   - feedItem: The feed item to update.
    ///   - actionType: The action to perform (read, trigger, click).
    func updateFeedItem(_ feedItem: FeedItem, actionType: FeedActionType) async throws
}
