import Foundation
@testable import meApp
import Testing

@Suite(.serialized)
@MainActor
struct IAMScreenViewModelTests {

    private func makeSUT() -> (sut: IAMScreenViewModel, feed: MockContentViewModelFeedService) {
        TestDependencyContainer.reset()
        let feed = MockContentViewModelFeedService()
        DependencyContainer.shared.register(feed as FeedServiceProtocol)
        return (IAMScreenViewModel(), feed)
    }

    private func waitUntil(
        timeoutNanoseconds: UInt64 = 2_000_000_000,
        pollNanoseconds: UInt64 = 20_000_000,
        condition: @escaping @MainActor () -> Bool
    ) async -> Bool {
        let deadline = ContinuousClock.now + .nanoseconds(Int64(timeoutNanoseconds))
        while !condition() && ContinuousClock.now < deadline {
            try? await Task.sleep(nanoseconds: pollNanoseconds)
        }
        return condition()
    }

    @Test("refreshFeed asks the feed service to fetch feed items")
    func refreshFeedFetchesItems() async {
        let (sut, feed) = makeSUT()

        sut.refreshFeed()

        let fetched = await waitUntil { feed.fetchFeedItemsCalls == 1 }
        #expect(fetched == true)
    }

    @Test("refreshFeed triggers a fetch on each invocation")
    func refreshFeedFetchesEachTime() async {
        let (sut, feed) = makeSUT()

        sut.refreshFeed()
        _ = await waitUntil { feed.fetchFeedItemsCalls == 1 }
        sut.refreshFeed()

        let fetchedTwice = await waitUntil { feed.fetchFeedItemsCalls == 2 }
        #expect(fetchedTwice == true)
    }
}
