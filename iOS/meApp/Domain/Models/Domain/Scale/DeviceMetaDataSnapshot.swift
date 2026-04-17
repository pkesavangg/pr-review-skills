import Foundation

/// Value-type snapshot of DeviceMetaData. Sendable, safe across async boundaries.
struct DeviceMetaDataSnapshot: Equatable, Sendable {
    let modelNumber: String?
    let serialNumber: String?
    let firmwareRevision: String?
    let hardwareRevision: String?
    let softwareRevision: String?
    let manufacturerName: String?
    let systemId: String?
    let latestVersion: String?
    let isSynced: Bool
}
