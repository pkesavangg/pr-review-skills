import Foundation
import GGBluetoothSwiftPackage
@testable import meApp

@MainActor
final class MockBLEDiscoveryManager: BLEDiscoveryManaging {
    private(set) var stopScanCalls = 0
    private(set) var clearDevicesCalls = 0
    private(set) var pauseScanCalls = 0
    private(set) var resumeScanCalls = 0
    private(set) var scanForPairingCalls = 0
    private(set) var lastResumeClearOnlyPairing: Bool?

    func stopScan(using sdk: GGBluetoothSwiftPackage) {
        stopScanCalls += 1
    }

    func clearDevices(using sdk: GGBluetoothSwiftPackage) {
        clearDevicesCalls += 1
    }

    func pauseScan(using sdk: GGBluetoothSwiftPackage) {
        pauseScanCalls += 1
    }

    func resumeScan(using sdk: GGBluetoothSwiftPackage, clearOnlyPairing: Bool) {
        resumeScanCalls += 1
        lastResumeClearOnlyPairing = clearOnlyPairing
    }

    func scanForPairing(using sdk: GGBluetoothSwiftPackage) {
        scanForPairingCalls += 1
    }
}
