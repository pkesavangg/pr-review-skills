import Foundation
@testable import meApp

enum BluetoothTestFixtures {
    static func makeDevice(
        id: String = "device-1",
        accountId: String = "101",
        deviceName: String = "Scale",
        broadcastId: Int64? = nil,
        broadcastIdString: String? = "ABC123",
        protocolType: String? = "A6",
        isConnected: Bool? = true,
        bathScale: BathScale? = nil
    ) -> Device {
        Device(
            id: id,
            accountId: accountId,
            deviceName: deviceName,
            broadcastId: broadcastId,
            broadcastIdString: broadcastIdString,
            protocolType: protocolType,
            isConnected: isConnected,
            bathScale: bathScale
        )
    }
}
