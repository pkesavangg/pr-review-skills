// IntegrationDataDTO.swift

import Foundation

// MARK: - IntegrationDataDTO
struct IntegrationDataDTO: Codable {
    let deviceId: String
    let type: IntegrationType
    let preferences: Preferences?
    let integratedAt: String?
    let updatedAt: String?
}