import Foundation

struct BpmOperationDTO: Codable, Sendable {
    let accountId: String?
    let systolic: Double?
    let diastolic: Double?
    let pulse: Double?
    let meanArterial: String?
    let note: String?
    let irregularHb: Bool?
    let source: String?
    let unit: String?
    let entryTimestamp: String?
    let operationType: String?
    let serverTimestamp: String?
}

extension BpmOperationDTO: Identifiable {
    var id: String { entryTimestamp ?? UUID().uuidString }
    var date: Date? {
        guard let entryTimestamp = entryTimestamp else { return nil }
        let formatter = ISO8601DateFormatter()
        return formatter.date(from: entryTimestamp)
    }

    func copy(with newTimestamp: String) -> BpmOperationDTO {
        .init(
            accountId: accountId,
            systolic: systolic,
            diastolic: diastolic,
            pulse: pulse,
            meanArterial: meanArterial,
            note: note,
            irregularHb: irregularHb,
            source: source,
            unit: unit,
            entryTimestamp: newTimestamp,
            operationType: operationType,
            serverTimestamp: serverTimestamp
        )
    }
}

extension BpmOperationDTO {
    func toAPIRequest() -> BpmOperationRequest {
        return BpmOperationRequest(
            userId: self.accountId,
            systolic: self.systolic,
            diastolic: self.diastolic,
            pulse: self.pulse,
            meanArterial: self.meanArterial,
            note: self.note,
            irregularHb: self.irregularHb,
            source: self.source,
            unit: self.unit,
            entryTimestamp: self.entryTimestamp,
            operationType: self.operationType
        )
    }
}
