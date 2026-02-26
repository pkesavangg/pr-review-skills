import Foundation

protocol BluetoothSetupManaging {
    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothService,
        considerForSession: Bool
    ) async

    func cancelWifi(on scale: Device, bluetoothService: BluetoothService) async
}

struct BluetoothSetupManager: BluetoothSetupManaging {
    func disconnectIfNeeded(
        broadcastId: String,
        bluetoothService: BluetoothService,
        considerForSession: Bool = true
    ) async {
        _ = await bluetoothService.disconnectDevice(
            broadcastId: broadcastId,
            considerForSession: considerForSession
        )
    }

    func cancelWifi(on scale: Device, bluetoothService: BluetoothService) async {
        await bluetoothService.cancelWifi(on: scale)
    }
}
