import Foundation

struct Profile: Codable {
    var firstName: String
    var lastName: String
    var gender: Sex
    var zipcode: String
    var dob: String
    var weightUnit: WeightUnit
    var height: Double
    var activityLevel: ActivityLevel
}