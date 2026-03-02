import Foundation
import GGBluetoothSwiftPackage

@MainActor
protocol BLEDiscoveryManaging {
    func stopScan(using sdk: GGBluetoothSwiftPackage)
    func clearDevices(using sdk: GGBluetoothSwiftPackage)
    func pauseScan(using sdk: GGBluetoothSwiftPackage)
    func resumeScan(using sdk: GGBluetoothSwiftPackage, clearOnlyPairing: Bool)
    func scanForPairing(using sdk: GGBluetoothSwiftPackage)
}

@MainActor
final class BLEDiscoveryManager: BLEDiscoveryManaging {
    func stopScan(using sdk: GGBluetoothSwiftPackage) {
        sdk.stop()
    }

    func clearDevices(using sdk: GGBluetoothSwiftPackage) {
        sdk.clearDevices()
    }

    func pauseScan(using sdk: GGBluetoothSwiftPackage) {
        sdk.pauseScan()
    }

    func resumeScan(using sdk: GGBluetoothSwiftPackage, clearOnlyPairing: Bool) {
        sdk.resumeScan(clearOnlyPairing)
    }

    func scanForPairing(using sdk: GGBluetoothSwiftPackage) {
        sdk.scanForPairing()
    }
}
