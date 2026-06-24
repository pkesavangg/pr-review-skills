import Foundation

/// Protocol for abstracting all remote (API) feed data access and operations.
///
/// This protocol defines the contract for interacting with the backend feed API endpoints (see /api/v3/feed/*),
/// including fetching feed items and updating their states (read, trigger, click, etc.).
///
/// Implementations of this protocol should handle all networking, serialization, and error handling for these operations.
@MainActor
protocol FeedRepositoryAPIProtocol {
    /// Fetches all feed items from the backend. (GET /feed/iam)
    /// - Returns: Array of FeedItem objects
    func fetchFeedItems() async throws -> [FeedItem]
    
    /// Updates a feed item's state with the given action. (POST /feed/iam/{feedPostId})
    ///  - Parameters:
    ///  - feedPostId: The ID of the feed item to update.
    ///  - feedAction: The action to perform on the feed item (e.g., mark as read, trigger, click).
    func updateFeedItem(feedPostId: String, feedAction: FeedAction) async throws
}
