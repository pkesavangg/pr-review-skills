import Testing
import Foundation
@testable import meApp

@Suite(.serialized)
@MainActor
struct FeedRepositoryAPITests {

    // MARK: - SUT Factory

    private func makeSUT() -> (sut: FeedRepositoryAPI, http: MockHTTPClient) {
        let http = MockHTTPClient()
        let sut = FeedRepositoryAPI(httpClient: http)
        return (sut, http)
    }

    // MARK: - fetchFeedItems

    @Test("fetchFeedItems success: calls get with feed endpoint with auth, returns items")
    func fetchFeedItemsSuccess() async throws {
        let (sut, http) = makeSUT()
        let item = try FeedTestFixtures.makeFeedItem(id: "feed-1")
        http.getResult = [item]

        let result = try await sut.fetchFeedItems()

        #expect(http.getCalls == 1)
        #expect(http.lastGetNeedsAuth == true)
        guard case .feed = http.lastGetEndpoint else {
            Issue.record("Expected .feed endpoint"); return
        }
        #expect(result.count == 1)
        #expect(result.first?.feedPostId == "feed-1")
    }

    @Test("fetchFeedItems success: returns empty array when feed is empty")
    func fetchFeedItemsEmpty() async throws {
        let (sut, http) = makeSUT()
        http.getResult = [FeedItem]()

        let result = try await sut.fetchFeedItems()

        #expect(http.getCalls == 1)
        guard case .feed = http.lastGetEndpoint else {
            Issue.record("Expected .feed endpoint"); return
        }
        #expect(result.isEmpty)
    }

    @Test("fetchFeedItems failure: propagates noInternet error")
    func fetchFeedItemsNoInternet() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.noInternet

        await #expect(throws: HTTPError.noInternet) {
            try await sut.fetchFeedItems()
        }
        #expect(http.getCalls == 1)
    }

    @Test("fetchFeedItems failure: propagates serverError")
    func fetchFeedItemsServerError() async throws {
        let (sut, http) = makeSUT()
        http.getError = HTTPError.serverError

        await #expect(throws: HTTPError.serverError) {
            try await sut.fetchFeedItems()
        }
        #expect(http.getCalls == 1)
    }

    // MARK: - updateFeedItem

    @Test("updateFeedItem success: calls send with markFeedAs(elementId) POST with auth")
    func updateFeedItemSuccess() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let action = FeedAction(action: .read, osType: nil, meta: nil)

        try await sut.updateFeedItem(feedPostId: "post-123", feedAction: action)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        #expect(http.lastSendNeedsAuth == true)
        guard case .markFeedAs(let elementId) = http.lastSendEndpoint else {
            Issue.record("Expected .markFeedAs endpoint"); return
        }
        #expect(elementId == "post-123")
    }

    @Test("updateFeedItem success: click action routes to same markFeedAs endpoint")
    func updateFeedItemClickAction() async throws {
        let (sut, http) = makeSUT()
        http.sendResult = EmptyResponse()
        let action = FeedAction(action: .click, osType: "ios", meta: FeedActionMeta(variationId: 42))

        try await sut.updateFeedItem(feedPostId: "post-456", feedAction: action)

        #expect(http.sendCalls == 1)
        #expect(http.lastSendMethod == .post)
        guard case .markFeedAs(let elementId) = http.lastSendEndpoint else {
            Issue.record("Expected .markFeedAs endpoint"); return
        }
        #expect(elementId == "post-456")
    }

    @Test("updateFeedItem failure: propagates unauthorized error")
    func updateFeedItemUnauthorized() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.unauthorized
        let action = FeedAction(action: .read, osType: nil, meta: nil)

        await #expect(throws: HTTPError.unauthorized) {
            try await sut.updateFeedItem(feedPostId: "post-123", feedAction: action)
        }
        #expect(http.sendCalls == 1)
    }

    @Test("updateFeedItem failure: propagates timeout error")
    func updateFeedItemTimeout() async throws {
        let (sut, http) = makeSUT()
        http.sendError = HTTPError.timeout
        let action = FeedAction(action: .trigger, osType: nil, meta: nil)

        await #expect(throws: HTTPError.timeout) {
            try await sut.updateFeedItem(feedPostId: "post-789", feedAction: action)
        }
        #expect(http.sendCalls == 1)
    }
}
