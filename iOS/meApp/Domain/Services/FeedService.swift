import Foundation
import ggInAppMessagingPackage
import Combine
import SwiftUI

@MainActor
final class FeedService: FeedServiceProtocol, ObservableObject {
    static let shared = FeedService()
    @Injector var notificationService: NotificationHelperService
    @Injector var logger: LoggerService
    
    private let apiRepo: FeedRepositoryAPIProtocol = FeedRepositoryAPI()
    private let networkMonitor = NetworkMonitor.shared
    private let accountService = AccountService.shared
    private let ggIAMService = GGInAppMessagingService.shared
    
    /// Emits whenever `feedItems` changes. Consumers can subscribe without needing GG IAM package.
    let feedsChanged = PassthroughSubject<[FeedItem], Never>()
    let feedSettingsChanged = PassthroughSubject<GGFeedSetting?, Never>()
    let notificationBadgeUpdated = PassthroughSubject<Bool, Never>()
    
    private let tag = "FeedService"
    private var cancellables = Set<AnyCancellable>()
    init() {
        getFeedSettings()
        ggIAMService
            .feedsUpdated
            .sink { [weak self] feedInfo in
                Task {
                    let feedActionType: GGFeedActionType = GGFeedActionType(rawValue: feedInfo.actionType.rawValue) ?? .read
                    await self?.updateFeedItem(feedInfo.feedItem, actionType: feedActionType, variationId: feedInfo.variationId)
                }
            }
            .store(in: &cancellables)
        
        // Listen for full-feed changes from the GG IAM service and propagate them internally
        ggIAMService
            .feedsChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] newFeeds in
                guard let self = self else { return }
                self.feedsChanged.send(newFeeds)
                self.updateNotificationBadge()
            }
            .store(in: &cancellables)
        
        ggIAMService
            .feedNotificationChanged
            .receive(on: DispatchQueue.main)
            .sink { [weak self] feedSettings in
                guard let self = self else { return }
                let result = getFeedSettings()
                self.feedSettingsChanged.send(result)
                self.updateNotificationBadge()
            }
            .store(in: &cancellables)
    }
    
    // MARK: - Feed Items Management
    func fetchFeedItems(isFromRefresh: Bool = false) async {
        do {
            ggIAMService.setAccountId(accountService.activeAccount?.accountId ?? "")
            let items = try await apiRepo.fetchFeedItems()
            ggIAMService.load(feeds: items)
            feedsChanged.send(items)
            updateNotificationBadge()
            logger.log(level: .info, tag: tag, message: "Successfully fetched feed items", data: ["count": items.count])
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to fetch feed items", data: error)
            ggIAMService.load(feeds: [])
            updateNotificationBadge()
        }
    }
    
    func updateFeedItem(_ feedItem: FeedItem, actionType: GGFeedActionType, variationId: Int?) async {
        let action = buildFeedAction(actionType: actionType, variationId: variationId)
        do {
            try await apiRepo.updateFeedItem(feedPostId: feedItem.feedPostId, feedAction: action)
            logger.log(level: .info, tag: tag, message: "Successfully updated feed item", data: ["feedPostId": feedItem.feedPostId, "actionType": actionType])
        } catch {
            logger.log(level: .error, tag: tag, message: "Failed to update feed item", data: ["feedPostId": feedItem.feedPostId, "error": error])
        }
    }
    
    func getUnreadFeedCount() -> Int {
        return ggIAMService.getUnreadFeedCount()
    }
    
    // MARK: - Feed Settings Management
    @discardableResult
    func getFeedSettings() -> GGFeedSetting? {
        if let result = ggIAMService.getStoredFeedNotificationSetting() {
            return GGFeedSetting(
                showPopupMessage: result.showPopupMessage,
                showNotificationBadge: result.showNotificationBadge
            )
        }
        return nil
    }
    
    // MARK: - Feed Modal Management
    func checkAndTriggerFeedModal() {
        let result = ggIAMService.checkFeedModalTrigger()
        if let feedItem = result {
            notificationService.showModal(ModalData(
                presentedView: AnyView(IAMFeedModalView(feedItem: feedItem, onClose: {
                    self.notificationService.dismissModal()
                })),
                backdropDismiss: false
            ))
        }
    }
    
    // MARK: - Cleanup
    func clearFeedData() {
        ggIAMService.clearFeedData()
        updateNotificationBadge()
    }
    
    // MARK: - Private Helpers
    
    private func buildFeedAction(actionType: GGFeedActionType, variationId: Int?) -> FeedAction {
        let requiresMeta = !(actionType == .click || actionType == .read || actionType == .trigger)
        return FeedAction(
            action: actionType,
            osType: requiresMeta ? "ios" : nil,
            meta: requiresMeta ? FeedActionMeta(variationId: variationId) : nil
        )
    }
    
    // MARK: - Notification Badge Helper
    private func updateNotificationBadge() {
        let feedSettings = getFeedSettings()
        let badgeShouldShow = getUnreadFeedCount() > 0 && (feedSettings?.showNotificationBadge ?? true)
        notificationBadgeUpdated.send(badgeShouldShow)
    }
    
    deinit {
        cancellables.forEach { $0.cancel() }
    }
}
