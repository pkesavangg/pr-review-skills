// IntegratedDeviceInfoDTO.swift
// DTO for device info returned by integration APIs

import Foundation

struct IntegratedDeviceInfoDTO: Codable {
    let operationType: OperationType
    let scopes: IntegrationDataDTO
    let isCurrentDeviceDeleted: Bool
}
