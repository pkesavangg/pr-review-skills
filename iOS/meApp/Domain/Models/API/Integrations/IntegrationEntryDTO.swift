// IntegrationEntryDTO.swift
// DTO for integration entry data

import Foundation

struct IntegrationEntryDTO: Codable {
    let type: IntegrationType
    let sentAt: String
    let timestamp: String
    let weight: Double?
    let bodyFat: Double?
    let muscleMass: Double?
    let water: Double?
    let bmi: Double?
    let data: [String: AnyCodable]?
}
