import Foundation
import ggInAppMessagingPackage
import Combine

@MainActor
final class FeedService: ObservableObject {
    static let shared = FeedService()
    
    @Injector var logger: LoggerService
    
    private let apiRepo: FeedRepositoryAPIProtocol = FeedRepositoryAPI()
    private let networkMonitor = NetworkMonitor.shared
    private let accountService = AccountService.shared
    private let ggIAMService = GGInAppMessagingService.shared
    
    @Published private(set) var feedItems: [FeedItem] = []
    
    private let tag = "FeedService"
    private var cancellables = Set<AnyCancellable>()
    init() {
        Task {
            do {
                try await getFeedSettings()
            } catch {
                logger.log(level: .error, tag: tag, message: "Failed to get feed settings during init", data: error)
            }
            
            ggIAMService
                .feedsUpdated
                .sink { [weak self] feedInfo in
                    Task {
                        let feedActionType: FeedActionType = FeedActionType(rawValue: feedInfo.actionType.rawValue) ?? .read
                        await self?.updateFeedItem(feedInfo.feedItem, actionType: feedActionType, variationId: feedInfo.variationId)
                    }
                }
                .store(in: &cancellables)
        }
    }
    
    // MARK: - Feed Items Management
    func fetchFeedItems() async {
        do {
            ggIAMService.setAccountId(accountService.activeAccount?.accountId ?? "")
            let items = try await apiRepo.fetchFeedItems()
            self.feedItems = items
            ggIAMService.load(feeds: self.feedItems)
            logger.log(level: .info, tag: tag, message: "Successfully fetched feed items", data: ["count": items.count])
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch feed items", data: error)
            ggIAMService.load(feeds: [])
        }
    }
    
    func updateFeedItem(_ feedItem: FeedItem, actionType: FeedActionType, variationId: Int?) async {
        let action = buildFeedAction(actionType: actionType, variationId: variationId)
        do {
            try await apiRepo.updateFeedItem(feedPostId: feedItem.feedPostId, feedAction: action)
            logger.log(level: .info, tag: tag, message: "Successfully updated feed item", data: ["feedPostId": feedItem.feedPostId, "actionType": actionType])
            
            // Update local state if item is marked as read
            if actionType == .read {
                if let index = self.feedItems.firstIndex(where: { $0.elementId == feedItem.elementId }) {
                    var updatedItem = self.feedItems[index]
                    updatedItem.isUnread = false
                    self.feedItems[index] = updatedItem
                }
                ggIAMService.load(feeds: self.feedItems)
            }
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update feed item", data: ["feedPostId": feedItem.feedPostId, "error": error])
        }
    }
    
    func getUnreadFeedCount() -> Int {
        return ggIAMService.getUnreadFeedCount()
    }
    
    // MARK: - Feed Settings Management
    @discardableResult
    func getFeedSettings() async throws -> GGFeedSetting? {
        if let result = ggIAMService.getStoredFeedNotificationSetting() {
            return GGFeedSetting(
                showPopupMessage: result.showPopupMessage,
                showNotificationBadge: result.showNotificationBadge
            )
        }
        return nil
    }
    
    // MARK: - Feed Modal Management
    func showFeedModal() async throws -> Bool {
        return true
    }
    
    // MARK: - Cleanup
    
    func clearFeedData() {
        ggIAMService.clearFeedData()
    }
    
    // MARK: - Private Helpers
    
    private func buildFeedAction(actionType: FeedActionType, variationId: Int?) -> FeedAction {
        let requiresMeta = !(actionType == .click || actionType == .read || actionType == .trigger)
        return FeedAction(
            action: actionType,
            osType: requiresMeta ? "ios" : nil,
            meta: requiresMeta ? FeedActionMeta(variationId: variationId) : nil
        )
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
    }
}
