import Foundation

struct BpmOperationRequest: Codable {
    let userId: String?
    let systolic: Double?
    let diastolic: Double?
    let pulse: Double?
    let meanArterial: String?
    let note: String?
    let source: String?
    let unit: String?
    let entryTimestamp: String?
    let operationType: String?
}
