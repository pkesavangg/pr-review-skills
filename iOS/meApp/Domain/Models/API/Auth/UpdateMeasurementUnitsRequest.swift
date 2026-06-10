import Foundation

/// Request body for `PATCH /v3/account/measurement-units`.
/// `measurementUnits` must be one of "metric", "imperialLbOz", "imperialLbDecimal".
struct UpdateMeasurementUnitsRequest: Codable {
    let measurementUnits: String
}
