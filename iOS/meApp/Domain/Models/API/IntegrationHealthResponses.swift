import Foundation

/// Response DTO returned from POST /integrations/health (Health Connect/HealthKit integration create/update).
struct HealthIntegrationResponse: Codable, @unchecked Sendable {
    let deviceId: String
    let type: IntegrationType
    let preferences: [String: AnyCodable]
    let integratedAt: String
    let updatedAt: String
}

/// Response DTO returned from POST /integrations/health/log (measurement log upload).
struct HealthIntegrationLogResponse: Codable, @unchecked Sendable {
    let type: IntegrationType
    let sentAt: String
    let timestamp: String

    // Optional measurements – server may return `null`
    let weight: Int?
    let bodyFat: Int?
    let muscleMass: Int?
    let water: Int?
    let bmi: Int?

    let data: [String: AnyCodable]
} 
