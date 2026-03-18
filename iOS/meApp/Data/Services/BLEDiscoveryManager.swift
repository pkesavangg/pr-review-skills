import Foundation
import GGBluetoothSwiftPackage

@MainActor
protocol BLEDiscoveryManaging {
    func stopScan(using sdk: BluetoothSDKClient)
    func clearDevices(using sdk: BluetoothSDKClient)
    func pauseScan(using sdk: BluetoothSDKClient)
    func resumeScan(using sdk: BluetoothSDKClient, clearOnlyPairing: Bool)
    func scanForPairing(using sdk: BluetoothSDKClient)
}

@MainActor
final class BLEDiscoveryManager: BLEDiscoveryManaging {
    func stopScan(using sdk: BluetoothSDKClient) {
        sdk.stop()
    }

    func clearDevices(using sdk: BluetoothSDKClient) {
        sdk.clearDevices()
    }

    func pauseScan(using sdk: BluetoothSDKClient) {
        sdk.pauseScan()
    }

    func resumeScan(using sdk: BluetoothSDKClient, clearOnlyPairing: Bool) {
        sdk.resumeScan(clearOnlyPairing)
    }

    func scanForPairing(using sdk: BluetoothSDKClient) {
        sdk.scanForPairing()
    }
}
