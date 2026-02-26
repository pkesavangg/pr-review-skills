import Foundation
import GGBluetoothSwiftPackage

@MainActor
final class BLEDiscoveryManager {
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
