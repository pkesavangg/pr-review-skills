import Foundation

struct Tokens: Codable, Equatable {
    let id: String
    let accessToken: String
    let refreshToken: String
    let expiresAt: String
}
