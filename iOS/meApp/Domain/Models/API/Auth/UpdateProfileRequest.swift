import Foundation

struct UpdateProfileRequest: Codable {
    let email: String
    let firstName: String
    let lastName: String?
    let gender: Sex
    let zipcode: String?
    let dob: String
    let weightUnit: String
    let height: Double
    let activityLevel: ActivityLevel?
}
