import Foundation

struct OperationListResponseDTO: Codable {
    let operations: [OperationDTO]
    let timestamp: String
}
