import Combine
import Foundation
import ggInAppMessagingPackage
@testable import meApp

@MainActor
final class MockFeedService: FeedServiceProtocol {
    let feedsChanged = PassthroughSubject<[FeedItem], Never>()
    let feedSettingsChanged = PassthroughSubject<GGFeedSetting?, Never>()
    let notificationBadgeUpdated = CurrentValueSubject<Bool, Never>(false)

    var unreadFeedCount = 0
    var feedSettings: GGFeedSetting?

    private(set) var fetchFeedItemsCalls = 0
    private(set) var updateFeedItemCalls = 0
    private(set) var getFeedSettingsCalls = 0
    private(set) var checkAndTriggerFeedModalCalls = 0
    private(set) var clearFeedDataCalls = 0
    private(set) var lastUpdatedFeedItem: FeedItem?
    private(set) var lastUpdateActionType: GGFeedActionType?
    private(set) var lastUpdateVariationId: Int?

    func fetchFeedItems() async {
        fetchFeedItemsCalls += 1
    }

    func updateFeedItem(_ feedItem: FeedItem, actionType: GGFeedActionType, variationId: Int?) async {
        updateFeedItemCalls += 1
        lastUpdatedFeedItem = feedItem
        lastUpdateActionType = actionType
        lastUpdateVariationId = variationId
    }

    func getUnreadFeedCount() -> Int {
        unreadFeedCount
    }

    func getFeedSettings() -> GGFeedSetting? {
        getFeedSettingsCalls += 1
        return feedSettings
    }

    func checkAndTriggerFeedModal() {
        checkAndTriggerFeedModalCalls += 1
    }

    func clearFeedData() {
        clearFeedDataCalls += 1
    }
}
