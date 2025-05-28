import Foundation

struct BathScaleOperationListResponse: Codable {
    let operations: [BathScaleOperationDTO]
    let timestamp: String
}
