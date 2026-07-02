import Foundation
@testable import meApp
import Testing

/// Tests for the server `deviceType` mapping added in MOB-383: the `DeviceType` server-value
/// bridge plus its propagation through `Device(from:)` and `Device.toDTO()`.
@Suite(.serialized)
@MainActor
struct DeviceTypeServerValueTests {

    // MARK: - DeviceType server-value bridge

    @Test("serverValue maps local cases to server identifiers")
    func serverValueMapping() {
        #expect(DeviceType.scale.serverValue == "weight_scale")
        #expect(DeviceType.babyScale.serverValue == "baby_scale")
        #expect(DeviceType.bpm.serverValue == "bpm")
    }

    @Test("fromServerValue maps server identifiers back to local cases")
    func fromServerValueMapping() {
        #expect(DeviceType.fromServerValue("weight_scale") == .scale)
        #expect(DeviceType.fromServerValue("baby_scale") == .babyScale)
        #expect(DeviceType.fromServerValue("bpm") == .bpm)
    }

    @Test("fromServerValue is case-insensitive and returns nil for unknown/absent values")
    func fromServerValueUnknown() {
        #expect(DeviceType.fromServerValue("BPM") == .bpm)
        #expect(DeviceType.fromServerValue("unknown") == nil)
        #expect(DeviceType.fromServerValue(nil) == nil)
    }

    // MARK: - Device(from:) prefers server deviceType

    @Test("Device(from:) uses server deviceType when present (mapped to local raw value)")
    func deviceFromDTOPrefersServerDeviceType() {
        let dto = ScaleTestFixtures.makeScaleDTO(id: "s1", sku: "R4-001", deviceType: "bpm")

        let device = Device(from: dto)

        #expect(device.deviceType == DeviceType.bpm.rawValue)
    }

    @Test("Device(from:) falls back to SKU derivation when server deviceType absent")
    func deviceFromDTOFallsBackToSku() {
        let dto = ScaleTestFixtures.makeScaleDTO(id: "s1", sku: "R4-001", deviceType: nil)

        let device = Device(from: dto)

        // R4-001 is a weight scale SKU → local "scale" raw value.
        #expect(device.deviceType == DeviceType.fromSku("R4-001").rawValue)
    }

    @Test("Device(from:) ignores unknown server deviceType and falls back to SKU")
    func deviceFromDTOUnknownServerValue() {
        let dto = ScaleTestFixtures.makeScaleDTO(id: "s1", sku: "R4-001", deviceType: "mystery")

        let device = Device(from: dto)

        #expect(device.deviceType == DeviceType.fromSku("R4-001").rawValue)
    }

    // MARK: - toDTO emits server deviceType

    @Test("toDTO maps local deviceType to server value")
    func toDTOEmitsServerDeviceType() {
        let device = ScaleTestFixtures.makeDevice(id: "s1")
        device.deviceType = DeviceType.babyScale.rawValue

        let dto = device.toDTO()

        #expect(dto.deviceType == "baby_scale")
    }

    @Test("server deviceType round-trips through Device and back to DTO")
    func deviceTypeRoundTrip() {
        let dto = ScaleTestFixtures.makeScaleDTO(id: "s1", sku: "R4-001", deviceType: "weight_scale")

        let roundTripped = Device(from: dto).toDTO()

        #expect(roundTripped.deviceType == "weight_scale")
    }
}
