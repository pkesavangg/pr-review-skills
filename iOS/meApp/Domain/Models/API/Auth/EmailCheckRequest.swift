import Foundation

/// Request body for `POST /v3/account/email-check` (no auth required).
struct EmailCheckRequest: Codable {
    let email: String
}
