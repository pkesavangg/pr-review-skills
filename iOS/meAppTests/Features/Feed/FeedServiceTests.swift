import Combine
import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct FeedServiceTests {
    @Test("fetchFeedItems success: sets account, loads feeds, publishes and updates badge")
    func fetchFeedItemsSuccess() async throws {
        let repo = MockFeedRepositoryAPI()
        let iam = MockGGInAppMessagingService()
        let account = MockAccountService()
        let logger = MockLoggerService()
        let notifications = MockNotificationHelperService()
        
        let first = try FeedTestFixtures.makeFeedItem(id: "feed-1", isUnread: true)
        let second = try FeedTestFixtures.makeFeedItem(id: "feed-2", isUnread: false)
        repo.fetchFeedItemsResult = .success([first, second])
        iam.unreadFeedCount = 1
        iam.feedSetting = GGFeedSetting(showPopupMessage: true, showNotificationBadge: true)
        
        account.activeAccount = AccountTestFixtures.makeAccountSnapshot(id: "acct-1", email: "feed@example.com", isActiveAccount: true)
        
        let sut = makeSUT(repo: repo, iam: iam, account: account, logger: logger, notifications: notifications)
        var publishedFeeds: [[FeedItem]] = []
        let feedsCancellable = sut.feedsChanged.sink { publishedFeeds.append($0) }
        
        await sut.fetchFeedItems()
        
        #expect(repo.fetchFeedItemsCalls == 1)
        #expect(iam.setAccountIdCalls == 1)
        #expect(iam.lastSetAccountId == "acct-1")
        #expect(iam.loadCalls == 1)
        #expect(iam.lastLoadedFeeds.count == 2)
        #expect(publishedFeeds.last?.count == 2)
        #expect(sut.notificationBadgeUpdated.value == true)
        #expect(logger.messages.contains { $0.contains("Successfully fetched feed items") })
        feedsCancellable.cancel()
    }
    
    @Test("fetchFeedItems empty result: keeps empty feed and badge false")
    func fetchFeedItemsEmptyResult() async {
        let repo = MockFeedRepositoryAPI()
        let iam = MockGGInAppMessagingService()
        iam.unreadFeedCount = 0
        iam.feedSetting = GGFeedSetting(showPopupMessage: true, showNotificationBadge: true)
        repo.fetchFeedItemsResult = .success([])
        
        let sut = makeSUT(repo: repo, iam: iam)
        
        await sut.fetchFeedItems()
        
        #expect(repo.fetchFeedItemsCalls == 1)
        #expect(iam.loadCalls == 1)
        #expect(iam.lastLoadedFeeds.isEmpty)
        #expect(sut.notificationBadgeUpdated.value == false)
    }
    
    @Test("fetchFeedItems network failure: logs error and loads empty feeds")
    func fetchFeedItemsNetworkFailure() async {
        let repo = MockFeedRepositoryAPI()
        let iam = MockGGInAppMessagingService()
        let logger = MockLoggerService()
        repo.fetchFeedItemsResult = .failure(FeedTestError.networkFailure)
        iam.unreadFeedCount = 0
        
        let sut = makeSUT(repo: repo, iam: iam, logger: logger)
        
        await sut.fetchFeedItems()
        
        #expect(repo.fetchFeedItemsCalls == 1)
        #expect(iam.loadCalls == 1)
        #expect(iam.lastLoadedFeeds.isEmpty)
        #expect(sut.notificationBadgeUpdated.value == false)
        #expect(logger.messages.contains { $0.contains("Failed to fetch feed items") })
    }
    
    @Test("fetchFeedItems invalid data: handles gracefully and does not crash")
    func fetchFeedItemsInvalidData() async {
        let repo = MockFeedRepositoryAPI()
        let iam = MockGGInAppMessagingService()
        repo.fetchFeedItemsResult = .failure(FeedTestError.invalidData)
        
        let sut = makeSUT(repo: repo, iam: iam)
        
        await sut.fetchFeedItems()
        
        #expect(repo.fetchFeedItemsCalls == 1)
        #expect(iam.loadCalls == 1)
        #expect(iam.lastLoadedFeeds.isEmpty)
    }
    
    @Test("updateFeedItem read action: does not include osType/meta")
    func updateFeedItemReadAction() async throws {
        let repo = MockFeedRepositoryAPI()
        let sut = makeSUT(repo: repo)
        let feedItem = try FeedTestFixtures.makeFeedItem(id: "post-100")
        
        await sut.updateFeedItem(feedItem, actionType: .read, variationId: 27)
        
        #expect(repo.updateFeedItemCalls == 1)
        #expect(repo.lastUpdatedFeedPostId == "post-100")
        #expect(repo.lastUpdatedFeedAction?.action == .read)
        #expect(repo.lastUpdatedFeedAction?.osType == nil)
        #expect(repo.lastUpdatedFeedAction?.meta == nil)
    }
    
    @Test("updateFeedItem variationClick action: includes metadata and iOS osType")
    func updateFeedItemVariationClickAction() async throws {
        let repo = MockFeedRepositoryAPI()
        let sut = makeSUT(repo: repo)
        let feedItem = try FeedTestFixtures.makeFeedItem(id: "post-101")
        
        await sut.updateFeedItem(feedItem, actionType: .variationClick, variationId: 7)
        
        #expect(repo.updateFeedItemCalls == 1)
        #expect(repo.lastUpdatedFeedAction?.action == .variationClick)
        #expect(repo.lastUpdatedFeedAction?.osType == "iOS")
        #expect(repo.lastUpdatedFeedAction?.meta?.variationId == 7)
    }

    @Test("updateFeedItem failure: logs error and does not throw")
    func updateFeedItemFailure() async throws {
        let repo = MockFeedRepositoryAPI()
        let logger = MockLoggerService()
        repo.updateFeedItemResult = .failure(FeedTestError.networkFailure)
        let sut = makeSUT(repo: repo, logger: logger)
        let feedItem = try FeedTestFixtures.makeFeedItem(id: "post-error")

        await sut.updateFeedItem(feedItem, actionType: .read, variationId: nil)

        #expect(repo.updateFeedItemCalls == 1)
        #expect(logger.messages.contains { $0.contains("Failed to update feed item") })
    }
    
    @Test("getFeedSettings returns mapped setting from IAM")
    func getFeedSettingsReturnsMappedSetting() {
        let iam = MockGGInAppMessagingService()
        iam.feedSetting = GGFeedSetting(showPopupMessage: false, showNotificationBadge: true)
        let sut = makeSUT(iam: iam)
        
        let settings = sut.getFeedSettings()
        
        #expect(settings?.showPopupMessage == false)
        #expect(settings?.showNotificationBadge == true)
    }
    
    @Test("clearFeedData clears IAM cache and updates badge state")
    func clearFeedDataUpdatesBadge() {
        let iam = MockGGInAppMessagingService()
        iam.unreadFeedCount = 0
        let sut = makeSUT(iam: iam)
        
        sut.clearFeedData()
        
        #expect(iam.clearFeedDataCalls == 1)
        #expect(sut.notificationBadgeUpdated.value == false)
    }
    
    @Test("checkAndTriggerFeedModal displays modal when trigger exists")
    func checkAndTriggerFeedModalDisplaysModal() async throws {
        let iam = MockGGInAppMessagingService()
        let notifications = MockNotificationHelperService()
        iam.feedModalTriggerResult = try FeedTestFixtures.makeFeedItem(
            id: "modal-1",
            titleImage: "https://example.com/modal.png"
        )
        
        let sut = makeSUT(
            iam: iam,
            notifications: notifications,
            modalTimeout: 0
        ) { _ in true }
        
        sut.checkAndTriggerFeedModal()
        for _ in 0..<20 where notifications.showModalCalls == 0 {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 10_000_000) // 10ms
        }
        
        #expect(notifications.showModalCalls == 1)
        #expect(notifications.isModalVisible == true)
    }
    
    @Test("checkAndTriggerFeedModal does nothing when no trigger is returned")
    func checkAndTriggerFeedModalNoTrigger() {
        let iam = MockGGInAppMessagingService()
        let notifications = MockNotificationHelperService()
        iam.feedModalTriggerResult = nil
        
        let sut = makeSUT(iam: iam, notifications: notifications, modalTimeout: 0)
        
        sut.checkAndTriggerFeedModal()
        
        #expect(notifications.showModalCalls == 0)
    }

    @Test("feedsUpdated publisher event: triggers updateFeedItem API call")
    func feedsUpdatedPublisherTriggersUpdate() async throws {
        let iam = MockGGInAppMessagingService()
        let repo = MockFeedRepositoryAPI()
        let feedItem = try FeedTestFixtures.makeFeedItem(id: "event-post")
        let sut = makeSUT(repo: repo, iam: iam)
        _ = sut

        iam.feedsUpdated.send(FeedUpdateInfo(feedItem: feedItem, actionType: .click, variationId: nil))
        for _ in 0..<20 where repo.updateFeedItemCalls == 0 {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        #expect(repo.updateFeedItemCalls == 1)
        #expect(repo.lastUpdatedFeedPostId == "event-post")
    }

    @Test("feedsChanged publisher event: republishes feeds and updates badge")
    func feedsChangedPublisherRepublishesAndUpdatesBadge() async throws {
        let iam = MockGGInAppMessagingService()
        iam.unreadFeedCount = 2
        iam.feedSetting = GGFeedSetting(showPopupMessage: true, showNotificationBadge: true)
        let sut = makeSUT(iam: iam)
        var observed: [[FeedItem]] = []
        let cancellable = sut.feedsChanged.sink { observed.append($0) }
        let eventFeed = try FeedTestFixtures.makeFeedItem(id: "changed-1")

        iam.feedsChanged.send([eventFeed])
        for _ in 0..<20 where observed.isEmpty {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        #expect(observed.last?.count == 1)
        #expect(sut.notificationBadgeUpdated.value == true)
        cancellable.cancel()
    }

    @Test("feedNotificationChanged publisher event: emits settings and refreshes badge")
    func feedNotificationChangedPublisherEmitsSettings() async {
        let iam = MockGGInAppMessagingService()
        iam.unreadFeedCount = 1
        iam.feedSetting = GGFeedSetting(showPopupMessage: false, showNotificationBadge: false)
        let sut = makeSUT(iam: iam)
        var settingsEvents: [GGFeedSetting?] = []
        let cancellable = sut.feedSettingsChanged.sink { settingsEvents.append($0) }

        iam.feedNotificationChanged.send(())
        for _ in 0..<20 where settingsEvents.isEmpty {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        #expect(settingsEvents.last??.showNotificationBadge == false)
        #expect(sut.notificationBadgeUpdated.value == false)
        cancellable.cancel()
    }

    @Test("checkAndTriggerFeedModal preload failure logs error and still shows modal")
    func checkAndTriggerFeedModalPreloadFailure() async throws {
        let iam = MockGGInAppMessagingService()
        let notifications = MockNotificationHelperService()
        let logger = MockLoggerService()
        iam.feedModalTriggerResult = try FeedTestFixtures.makeFeedItem(
            id: "modal-preload-fail",
            titleImage: "https://example.com/fail.png"
        )

        let sut = makeSUT(
            iam: iam,
            logger: logger,
            notifications: notifications,
            modalTimeout: 0
        ) { _ in false }

        sut.checkAndTriggerFeedModal()
        for _ in 0..<20 where notifications.showModalCalls == 0 {
            await Task.yield()
            try? await Task.sleep(nanoseconds: 10_000_000)
        }

        #expect(notifications.showModalCalls == 1)
        #expect(logger.messages.contains { $0.contains("Failed to preload feed modal image") })
    }
    
    private func makeSUT(
        repo: MockFeedRepositoryAPI? = nil,
        iam: MockGGInAppMessagingService? = nil,
        account: MockAccountService? = nil,
        logger: MockLoggerService? = nil,
        notifications: MockNotificationHelperService? = nil,
        modalTimeout: TimeInterval = 0,
        imagePreloader: @escaping (URL) async -> Bool = { _ in true }
    ) -> FeedService {
        let repo = repo ?? MockFeedRepositoryAPI()
        let iam = iam ?? MockGGInAppMessagingService()
        let account = account ?? MockAccountService()
        let logger = logger ?? MockLoggerService()
        let notifications = notifications ?? MockNotificationHelperService()
        
        return FeedService(
            apiRepo: repo,
            accountService: account,
            ggIAMService: iam,
            notificationService: notifications,
            logger: logger,
            feedModalTimeout: modalTimeout,
            imagePreloader: imagePreloader
        )
    }
}
