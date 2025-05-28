import Foundation

struct AccountResponse: Codable {
    let account: AccountDTO
    let accessToken: String?
    let refreshToken: String?
    let expiresAt: String?
}
