import Foundation
@testable import meApp

@MainActor
final class MockFeedRepositoryAPI: FeedRepositoryAPIProtocol {
    var fetchFeedItemsResult: Result<[FeedItem], Error> = .success([])
    private(set) var fetchFeedItemsCalls = 0
    
    var updateFeedItemResult: Result<Void, Error> = .success(())
    private(set) var updateFeedItemCalls = 0
    private(set) var lastUpdatedFeedPostId: String?
    private(set) var lastUpdatedFeedAction: FeedAction?
    
    func fetchFeedItems() async throws -> [FeedItem] {
        fetchFeedItemsCalls += 1
        return try fetchFeedItemsResult.get()
    }
    
    func updateFeedItem(feedPostId: String, feedAction: FeedAction) async throws {
        updateFeedItemCalls += 1
        lastUpdatedFeedPostId = feedPostId
        lastUpdatedFeedAction = feedAction
        try updateFeedItemResult.get()
    }
}
