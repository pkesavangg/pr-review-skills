import Foundation

/// Response body for `POST /v3/account/email-check`.
/// `isAvailable` is true when the email is not already registered.
struct EmailCheckResponse: Codable {
    let isAvailable: Bool
}
