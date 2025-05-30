import Foundation

/// Protocol for abstracting all feed data access and operations (local or remote).
///
/// This protocol defines the contract for interacting with feed data, including fetching feed items
/// and updating their state (read, trigger, click). Implementations may use local storage or remote API.
protocol FeedRepositoryProtocol {
    /// Fetches the list of feed items for the current user.
    /// - Returns: An array of FeedItem.
    func fetchFeedItems() async throws -> FeedListResponseDTO

    /// Updates the state of a feed item (e.g., mark as read, trigger, click).
    /// - Parameters:
    ///   - feedItem: The feed item to update.
    ///   - actionType: The action to perform (read, trigger, click).
    func updateFeedItem(_ feedItemId: String, actionType: FeedActionType) async throws
}
