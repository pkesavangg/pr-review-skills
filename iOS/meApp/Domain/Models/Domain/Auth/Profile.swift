import Foundation

struct Profile: Codable {
    var id: String
    var firstName: String
    var lastName: String
    var gender: String
    var zipcode: String
    var dob: String
    var weightUnit: String
    var height: Double
    var activityLevel: String
}