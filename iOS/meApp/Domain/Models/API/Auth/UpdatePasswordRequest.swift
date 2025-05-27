import Foundation

struct UpdatePasswordRequest: Codable {
    let oldPassword: String
    let newPassword: String
}
