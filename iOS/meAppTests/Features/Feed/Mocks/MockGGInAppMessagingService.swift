import Combine
import Foundation
@testable import meApp

@MainActor
final class MockGGInAppMessagingService: GGInAppMessagingServiceProtocol {
    let feedsUpdated = PassthroughSubject<FeedUpdateInfo, Never>()
    let feedsChanged = PassthroughSubject<[FeedItem], Never>()
    let feedNotificationChanged = PassthroughSubject<Void, Never>()
    
    var feedsUpdatedPublisher: AnyPublisher<FeedUpdateInfo, Never> {
        feedsUpdated.eraseToAnyPublisher()
    }
    
    var feedsChangedPublisher: AnyPublisher<[FeedItem], Never> {
        feedsChanged.eraseToAnyPublisher()
    }
    
    var feedNotificationChangedPublisher: AnyPublisher<Void, Never> {
        feedNotificationChanged.eraseToAnyPublisher()
    }
    
    private(set) var setAccountIdCalls = 0
    private(set) var lastSetAccountId: String?
    
    private(set) var loadCalls = 0
    private(set) var lastLoadedFeeds: [FeedItem] = []
    
    var unreadFeedCount = 0
    var feedSetting: GGFeedSetting?
    var feedModalTriggerResult: FeedItem?
    
    private(set) var clearFeedDataCalls = 0
    
    func setAccountId(_ accountId: String) {
        setAccountIdCalls += 1
        lastSetAccountId = accountId
    }
    
    func load(feeds: [FeedItem]) {
        loadCalls += 1
        lastLoadedFeeds = feeds
    }
    
    func getUnreadFeedCount() -> Int {
        unreadFeedCount
    }
    
    func getFeedNotificationSetting() -> GGFeedSetting? {
        feedSetting
    }
    
    func checkFeedModalTrigger() -> FeedItem? {
        feedModalTriggerResult
    }
    
    func clearFeedData() {
        clearFeedDataCalls += 1
    }
}
