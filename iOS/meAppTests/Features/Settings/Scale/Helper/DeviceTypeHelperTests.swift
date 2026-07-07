import Foundation
@testable import meApp
import Testing

/// Pure-logic tests for `DeviceTypeHelper`. Exercises all three static methods with
/// deterministic inputs — the primitive `determineDeviceModelType(sku:scaleType:deviceType:)`
/// (which needs no `@Model`) is covered exhaustively, and the two `Device`-based methods are
/// driven through their SCALES-hit, bathScale-fallback, and deviceType-fallback branches.
@Suite(.serialized)
@MainActor
struct DeviceTypeHelperTests {

    // A SKU that is guaranteed to miss the SCALES catalog (real SKUs are numeric like "0383"),
    // forcing control flow into the scaleType / deviceType fallback branches.
    private let missingSku = "ZZZZ"

    // MARK: - Primitive method · nil-sku branch

    @Test("primitive: nil sku with scaleType routes through DeviceSourceType")
    func primitiveNilSkuScaleTypeBranch() {
        typealias H = DeviceTypeHelper
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "wifi", deviceType: nil) == .wifi)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "espTouchWifi", deviceType: nil) == .wifi)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "bluetooth", deviceType: nil) == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "lcbt", deviceType: nil) == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "lcbt scale", deviceType: nil) == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "bluetooth scale", deviceType: nil) == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "appsync", deviceType: nil) == .appsync)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "appsync scale", deviceType: nil) == .appsync)
        #expect(H.determineDeviceModelType(sku: nil, scaleType: "btWifiR4", deviceType: nil) == .bluetoothR4)
    }

    @Test("primitive: nil sku with unknown scaleType falls back to bluetoothA6")
    func primitiveNilSkuUnknownScaleType() {
        // DeviceSourceType(rawValue:) fails → defaults to .bluetoothScale → .bluetoothA6
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: "not-a-real-type", deviceType: nil) == .bluetoothA6)
    }

    @Test("primitive: nil sku, nil scaleType, deviceType bpm returns bpm")
    func primitiveNilSkuBpmDeviceType() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: nil, deviceType: "bpm") == .bpm)
        // Case-insensitive: DeviceType.bpm.rawValue == "bpm"
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: nil, deviceType: "BPM") == .bpm)
    }

    @Test("primitive: nil sku, nil scaleType, non-bpm deviceType returns default bluetoothA6")
    func primitiveNilSkuDefaultFallback() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: nil, deviceType: nil) == .bluetoothA6)
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: nil, deviceType: "wifi") == .bluetoothA6)
    }

    @Test("primitive: nil sku scaleType takes priority over bpm deviceType")
    func primitiveNilSkuScaleTypeWinsOverBpm() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: nil, scaleType: "wifi", deviceType: "bpm") == .wifi)
    }

    // MARK: - Primitive method · non-nil sku missing from SCALES

    @Test("primitive: missing sku falls through to scaleType branch")
    func primitiveMissingSkuScaleTypeBranch() {
        typealias H = DeviceTypeHelper
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: "wifi", deviceType: nil) == .wifi)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: "appsync scale", deviceType: nil) == .appsync)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: "btWifiR4", deviceType: nil) == .bluetoothR4)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: "bluetooth", deviceType: nil) == .bluetoothA6)
    }

    @Test("primitive: missing sku, nil scaleType, bpm deviceType returns bpm")
    func primitiveMissingSkuBpm() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "bpm") == .bpm)
    }

    @Test("primitive: missing sku, nil scaleType, deviceType switch")
    func primitiveMissingSkuDeviceTypeSwitch() {
        typealias H = DeviceTypeHelper
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "bluetooth") == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "bluetoothscale") == .bluetoothA6)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "wifi") == .wifi)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "wifiscale") == .wifi)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "appsync") == .appsync)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "appsyncscale") == .appsync)
        #expect(H.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: "garbage") == .bluetoothA6)
    }

    @Test("primitive: missing sku, nil scaleType, nil deviceType hits final default")
    func primitiveMissingSkuFinalDefault() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: missingSku, scaleType: nil, deviceType: nil) == .bluetoothA6)
    }

    @Test("primitive: missing sku scaleType takes priority over deviceType")
    func primitiveMissingSkuScaleTypeWinsOverDeviceType() {
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: missingSku, scaleType: "wifi", deviceType: "bpm") == .wifi)
    }

    // MARK: - Primitive method · SCALES-hit branch (real numeric SKUs)

    @Test("primitive: real SKUs resolve via the SCALES catalog setupType")
    func primitiveScalesLookupHits() {
        typealias H = DeviceTypeHelper
        #expect(H.determineDeviceModelType(sku: "0375", scaleType: nil, deviceType: nil) == .bluetoothA6) // .bluetooth
        #expect(H.determineDeviceModelType(sku: "0378", scaleType: nil, deviceType: nil) == .bluetoothA6) // .lcbt
        #expect(H.determineDeviceModelType(sku: "0385", scaleType: nil, deviceType: nil) == .wifi)         // .wifi
        #expect(H.determineDeviceModelType(sku: "0384", scaleType: nil, deviceType: nil) == .wifi)         // .espTouchWifi
        #expect(H.determineDeviceModelType(sku: "0340", scaleType: nil, deviceType: nil) == .appsync)      // .appSync
        #expect(H.determineDeviceModelType(sku: "0412", scaleType: nil, deviceType: nil) == .bluetoothR4)  // .btWifiR4
        #expect(H.determineDeviceModelType(sku: "0220", scaleType: nil, deviceType: nil) == .babyScale)    // .babyScale
    }

    @Test("primitive: SKU 0022 is remapped to 0383 before the SCALES lookup")
    func primitiveSkuRemap() {
        // mapSkuForDisplay("0022") -> "0383" (setupType .lcbt) -> .bluetoothA6
        #expect(DeviceTypeHelper.determineDeviceModelType(sku: "0022", scaleType: nil, deviceType: nil) == .bluetoothA6)
    }

    // MARK: - determineDeviceModelType(for:)

    @Test("device: nil sku returns default bluetoothA6")
    func deviceNilSkuDefault() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = nil
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bluetoothA6)
    }

    @Test("device: real SKU resolves via SCALES catalog")
    func deviceScalesLookup() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = "0385" // .wifi
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .wifi)
    }

    @Test("device: unknown SKU falls back to bathScale scaleType")
    func deviceBathScaleFallback() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = missingSku

        device.bathScale = BathScale(scaleType: "wifi", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .wifi)

        device.bathScale = BathScale(scaleType: "appsync scale", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .appsync)

        device.bathScale = BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bluetoothR4)

        device.bathScale = BathScale(scaleType: "bluetooth", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bluetoothA6)
    }

    @Test("device: unknown SKU, no bathScale, uses deviceType fallbacks")
    func deviceDeviceTypeFallback() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = missingSku
        device.bathScale = nil

        device.deviceType = "bpm"
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bpm)

        device.deviceType = "wifi"
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .wifi)

        device.deviceType = "appsync"
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .appsync)

        device.deviceType = "garbage"
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bluetoothA6)

        device.deviceType = nil
        #expect(DeviceTypeHelper.determineDeviceModelType(for: device) == .bluetoothA6)
    }

    // MARK: - determineDeviceModelTypeString(for:)

    @Test("string: nil sku returns Unknown")
    func stringNilSku() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = nil
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Unknown")
    }

    @Test("string: real SKUs resolve via SCALES catalog")
    func stringScalesLookup() {
        let device = ScaleTestFixtures.makeDevice()

        device.sku = "0385" // .wifi
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "WiFi")

        device.sku = "0375" // .bluetooth
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth")

        device.sku = "0340" // .appSync
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "AppSync")

        device.sku = "0412" // .btWifiR4
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth/Wi-Fi")

        device.sku = "0220" // .babyScale
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth")
    }

    @Test("string: unknown SKU falls back to bathScale scaleType")
    func stringBathScaleFallback() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = missingSku

        device.bathScale = BathScale(scaleType: "wifi", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "WiFi")

        device.bathScale = BathScale(scaleType: "appsync scale", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "AppSync")

        device.bathScale = BathScale(scaleType: DeviceSourceType.btWifiR4.rawValue, bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth/Wi-Fi")

        device.bathScale = BathScale(scaleType: "bluetooth", bodyComp: true)
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth")
    }

    @Test("string: unknown SKU, no bathScale, uses deviceType fallbacks")
    func stringDeviceTypeFallback() {
        let device = ScaleTestFixtures.makeDevice()
        device.sku = missingSku
        device.bathScale = nil

        device.deviceType = "bluetooth"
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Bluetooth")

        device.deviceType = "wifi"
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "WiFi")

        device.deviceType = "appsync"
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "AppSync")

        device.deviceType = "garbage"
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Unknown")

        device.deviceType = nil
        #expect(DeviceTypeHelper.determineDeviceModelTypeString(for: device) == "Unknown")
    }
}
