import Combine
import Foundation
import ggInAppMessagingPackage

struct FeedUpdateInfo {
    let feedItem: FeedItem
    let actionType: GGFeedActionType
    let variationId: Int?
}

@MainActor
protocol GGInAppMessagingServiceProtocol: AnyObject {
    var feedsUpdatedPublisher: AnyPublisher<FeedUpdateInfo, Never> { get }
    var feedsChangedPublisher: AnyPublisher<[FeedItem], Never> { get }
    var feedNotificationChangedPublisher: AnyPublisher<Void, Never> { get }

    func setAccountId(_ accountId: String)
    func load(feeds: [FeedItem])
    func getUnreadFeedCount() -> Int
    func getFeedNotificationSetting() -> GGFeedSetting?
    func checkFeedModalTrigger() -> FeedItem?
    func clearFeedData()
}

@MainActor
extension GGInAppMessagingService: GGInAppMessagingServiceProtocol {
    var feedsUpdatedPublisher: AnyPublisher<FeedUpdateInfo, Never> {
        feedsUpdated
            .map {
                FeedUpdateInfo(
                    feedItem: $0.feedItem,
                    actionType: GGFeedActionType(rawValue: $0.actionType.rawValue) ?? .read,
                    variationId: $0.variationId
                )
            }
            .eraseToAnyPublisher()
    }

    var feedsChangedPublisher: AnyPublisher<[FeedItem], Never> {
        feedsChanged.eraseToAnyPublisher()
    }

    var feedNotificationChangedPublisher: AnyPublisher<Void, Never> {
        feedNotificationChanged
            .map { _ in () }
            .eraseToAnyPublisher()
    }

    func getFeedNotificationSetting() -> GGFeedSetting? {
        guard let result = getStoredFeedNotificationSetting() else {
            return nil
        }
        return GGFeedSetting(
            showPopupMessage: result.showPopupMessage,
            showNotificationBadge: result.showNotificationBadge
        )
    }
}
