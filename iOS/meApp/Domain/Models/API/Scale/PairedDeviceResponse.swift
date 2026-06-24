import Foundation

/// Response object for the Me App 2.0 unified device endpoints:
/// `POST /v3/paired-device/` (201), `GET /v3/paired-device/` (200, array), and
/// `PATCH /v3/paired-device/:deviceId` (200). All write to the same `paired_scale` table on the
/// server. The `GET` list response may omit the optional hardware fields (`mac`, `password`, …).
struct PairedDeviceResponse: Codable, Sendable, Identifiable, Equatable {
    let id: String
    /// `weight_scale`, `baby_scale`, or `bpm`.
    let deviceType: String?
    /// Connection type (e.g. `btWifiR4`, `bpmBluetooth`).
    let type: String?
    let nickname: String?
    let sku: String?
    let mac: String?
    let broadcastId: Int?
    let password: Int?
    let userNumber: Int?
    let name: String?
    let peripheralIdentifier: String?
    let createdAt: String?
}
