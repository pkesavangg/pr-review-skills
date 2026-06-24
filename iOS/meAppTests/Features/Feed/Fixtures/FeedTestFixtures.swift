import Foundation
@testable import meApp

enum FeedTestError: Error, Equatable {
    case networkFailure
    case invalidData
}

enum FeedTestFixtures {
    static func makeFeedItem(
        id: String = "feed-1",
        isUnread: Bool = true,
        titleImage: String = "https://example.com/feed-1.png"
    ) throws -> FeedItem {
        let json = """
        {
          "accountId": "test-account",
          "feedPostId": "\(id)",
          "elementId": "\(id)",
          "feedType": "link",
          "isUnread": \(isUnread),
          "messageTypeText": "promo",
          "titleText": "Promo title",
          "subtitleModalText": "Modal subtitle",
          "subtitleFeedText": "Feed subtitle",
          "titleImage": "\(titleImage)",
          "linkTarget": "https://example.com",
          "linkText": "Read more",
          "trigger": null,
          "expiresAt": "2099-01-01T00:00:00Z"
        }
        """
        let data = Data(json.utf8)
        return try JSONDecoder().decode(FeedItem.self, from: data)
    }
}
