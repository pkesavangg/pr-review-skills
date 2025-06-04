import Foundation

struct Tokens: Codable, Equatable {
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
}
