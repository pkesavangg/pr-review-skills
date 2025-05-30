import Foundation

struct Notifications: Codable, Equatable {
    let shouldSendEntryNotifications: Bool
    let shouldSendWeightInEntryNotifications: Bool
}
