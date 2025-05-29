import Foundation

struct Integrations: Codable, Equatable {
    let isFitbitOn: Bool
    let isGoogleFitOn: Bool
    let isMFPOn: Bool
    let isUAOn: Bool
    let isFitbitValid: Bool
    let isGoogleFitValid: Bool
    let isMFPValid: Bool
    let isUAValid: Bool
    let healthkit: Bool
    let isHealthConnectOn: Bool
}
