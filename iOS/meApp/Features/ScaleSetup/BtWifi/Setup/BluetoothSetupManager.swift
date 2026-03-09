import Foundation

protocol BluetoothSetupManaging {
    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothServiceProtocol,
        considerForSession: Bool
    ) async

    func cancelWifi(on scale: Device, bluetoothService: BluetoothServiceProtocol) async
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

    func cancelWifi(on scale: Device, bluetoothService: BluetoothServiceProtocol) async {
        _ = await bluetoothService.cancelWifi(on: scale)
    }
}
