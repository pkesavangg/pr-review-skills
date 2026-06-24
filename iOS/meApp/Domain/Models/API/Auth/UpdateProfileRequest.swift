import Foundation

struct UpdateProfileRequest: Codable {
    let email: String
    let firstName: String
    let lastName: String?
    /// Required only when `productTypes` includes "weight" or "blood_pressure".
    let gender: Sex?
    let zipcode: String?
    /// Required only when `productTypes` includes "weight" or "blood_pressure".
    let dob: String?
    let weightUnit: String
    /// Required only when `productTypes` includes "weight".
    let height: Double?
    let activityLevel: ActivityLevel?
    /// Products the account has access to. Allowed: "weight", "blood_pressure", "baby".
    let productTypes: [String]?
    /// Preferred measurement units: "metric", "imperialLbOz", "imperialLbDecimal".
    let measurementUnits: String?
}
