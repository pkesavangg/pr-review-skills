import Foundation

protocol BluetoothSetupManaging {
    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothServiceProtocol,
        considerForSession: Bool
    ) async

    func cancelWifi(broadcastId: String, bluetoothService: BluetoothServiceProtocol) async
}

struct BluetoothSetupManager: BluetoothSetupManaging {
    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothServiceProtocol,
        considerForSession: Bool = true
    ) async {
        _ = await bluetoothService.disconnectDevice(
            broadcastId: broadcastId,
            considerForSession: considerForSession
        )
    }

    func cancelWifi(broadcastId: String, bluetoothService: BluetoothServiceProtocol) async {
        _ = await bluetoothService.cancelWifi(broadcastId: broadcastId)
    }
}
