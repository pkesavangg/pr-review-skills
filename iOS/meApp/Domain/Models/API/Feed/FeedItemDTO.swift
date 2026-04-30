import Foundation

struct FeedItemDTO: Codable {
    let elementId: String
    let isUnread: Bool
    let messageTypeText: String
    let titleText: String
    let subtitleModalText: String
    let subtitleFeedText: String
    let titleImage: String
    let linkTarget: String?
    let linkText: String?
    let trigger: String?
    let expiresAt: String?
}
