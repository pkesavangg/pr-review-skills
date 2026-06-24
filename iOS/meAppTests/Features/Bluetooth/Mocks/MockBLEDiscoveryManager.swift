import Foundation
@testable import meApp

@MainActor
final class MockBLEDiscoveryManager: BLEDiscoveryManaging {
    private(set) var stopScanCalls = 0
    private(set) var clearDevicesCalls = 0
    private(set) var pauseScanCalls = 0
    private(set) var resumeScanCalls = 0
    private(set) var scanForPairingCalls = 0
    private(set) var lastResumeClearOnlyPairing: Bool?

    func stopScan(using sdk: BluetoothSDKClient) {
        stopScanCalls += 1
    }

    func clearDevices(using sdk: BluetoothSDKClient) {
        clearDevicesCalls += 1
    }

    func pauseScan(using sdk: BluetoothSDKClient) {
        pauseScanCalls += 1
    }

    func resumeScan(using sdk: BluetoothSDKClient, clearOnlyPairing: Bool) {
        resumeScanCalls += 1
        lastResumeClearOnlyPairing = clearOnlyPairing
    }

    func scanForPairing(using sdk: BluetoothSDKClient) {
        scanForPairingCalls += 1
    }
}
