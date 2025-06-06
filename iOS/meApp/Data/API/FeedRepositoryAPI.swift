import Foundation
import UIKit

@MainActor
final class FeedRepositoryAPI: FeedRepositoryAPIProtocol {
    private let httpClient = HTTPClient.shared
    
    func fetchFeedItems() async throws -> [FeedItem] {
        return try await httpClient.get(.feed, needsAuth: true)
    }
    
    func updateFeedItem(feedPostId: String, feedAction: FeedAction) async throws {
        _ = try await httpClient.send(
            .markFeedAs(elementId: feedPostId),
            method: .post,
            body: feedAction,
            needsAuth: true
        ) as EmptyResponse
    }
}