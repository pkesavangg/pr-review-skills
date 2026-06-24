import Foundation

struct Profile: Codable {
    var firstName: String
    var lastName: String
    var email: String?
    var gender: Sex
    var zipcode: String
    var dob: String
    var weightUnit: WeightUnit
    var height: Double
    var activityLevel: ActivityLevel
    /// Products the account is signing up for. Defaults to `["weight"]` server-side
    /// when omitted. Only sent on signup; nil on profile patches.
    var productTypes: [String]?
    /// Preferred measurement units. Required server-side when `productTypes`
    /// includes "baby". nil on profile patches.
    var measurementUnits: String?
}
