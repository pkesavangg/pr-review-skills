import Foundation
import Combine
import SwiftUI
import ggInAppMessagingPackage

/// Protocol defining the service interface for managing feed items, including fetching, updating, and managing feed settings.
@MainActor
protocol FeedServiceProtocol: AnyObject {
    // MARK: - Publishers

    /// Emits whenever `feedItems` are changed
    var feedsChanged: PassthroughSubject<[FeedItem], Never> { get }

    /// Emits when feed settings are updated
    var feedSettingsChanged: PassthroughSubject<GGFeedSetting?, Never> { get }

    /// Emits when the notification badge should be updated
    var notificationBadgeUpdated: CurrentValueSubject<Bool, Never> { get }

    // MARK: - Feed Items Management

    /// Fetches all feed items from the backend and updates local state.
    func fetchFeedItems() async

    /// Updates a feed item's state with the given action.
    /// - Parameters:
    ///   - feedItem: The feed item to update
    ///   - actionType: The type of action (read, trigger, click, etc.)
    ///   - variationId: Optional variation ID for certain action types
    func updateFeedItem(_ feedItem: FeedItem, actionType: GGFeedActionType, variationId: Int?) async

    /// Gets the count of unread feed items.
    /// - Returns: Number of unread feed items
    func getUnreadFeedCount() -> Int

    // MARK: - Feed Settings Management

    /// Gets the stored feed notification settings.
    /// - Returns: FeedSetting if exists, nil otherwise
    @discardableResult
    func getFeedSettings() -> GGFeedSetting?

    // MARK: - Feed Modal Management

    /// Checks and displays the feed modal if trigger conditions are met.
    func checkAndTriggerFeedModal()

    // MARK: - Cleanup

    /// Clears all feed data for the current user.
    func clearFeedData()
}
