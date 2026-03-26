import Foundation

struct BpmOperationListResponse: Codable {
    let operations: [BpmOperationDTO]
    let timestamp: String
}
