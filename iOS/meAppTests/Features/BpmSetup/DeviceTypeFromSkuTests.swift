///
///  DeviceTypeFromSkuTests.swift
///  meAppTests
///
///  Regression tests for DeviceType.fromSku(_:) ensuring all device categories
///  are classified correctly after the dynamic SKU-based classification was introduced.
///

import Testing
@testable import meApp

@Suite(.serialized)
struct DeviceTypeFromSkuTests {

    // MARK: - Weight scales always return .scale

    @Test("nil SKU returns .scale")
    func nilSkuReturnsScale() {
        #expect(DeviceType.fromSku(nil) == .scale)
    }

    @Test("empty SKU returns .scale")
    func emptySkuReturnsScale() {
        #expect(DeviceType.fromSku("") == .scale)
    }

    @Test("unknown SKU returns .scale")
    func unknownSkuReturnsScale() {
        #expect(DeviceType.fromSku("9999") == .scale)
    }

    @Test("standard weight scale SKU 0001 returns .scale")
    func standardScaleSkuReturnsScale() {
        #expect(DeviceType.fromSku("0001") == .scale)
    }

    // MARK: - BPM SKUs return .bpm

    @Test("primary BPM SKU 0603 returns .bpm")
    func sku0603ReturnsBpm() {
        #expect(DeviceType.fromSku("0603") == .bpm)
    }

    @Test("primary BPM SKU 0604 returns .bpm")
    func sku0604ReturnsBpm() {
        #expect(DeviceType.fromSku("0604") == .bpm)
    }

    @Test("primary BPM SKU 0634 returns .bpm")
    func sku0634ReturnsBpm() {
        #expect(DeviceType.fromSku("0634") == .bpm)
    }

    @Test("primary BPM SKU 0636 returns .bpm")
    func sku0636ReturnsBpm() {
        #expect(DeviceType.fromSku("0636") == .bpm)
    }

    @Test("primary BPM SKU 0661 returns .bpm")
    func sku0661ReturnsBpm() {
        #expect(DeviceType.fromSku("0661") == .bpm)
    }

    @Test("primary BPM SKU 0663 returns .bpm")
    func sku0663ReturnsBpm() {
        #expect(DeviceType.fromSku("0663") == .bpm)
    }

    @Test("alternate BPM SKU 0664 returns .bpm")
    func sku0664ReturnsBpm() {
        #expect(DeviceType.fromSku("0664") == .bpm)
    }

    @Test("alternate BPM SKU 0665 returns .bpm")
    func sku0665ReturnsBpm() {
        #expect(DeviceType.fromSku("0665") == .bpm)
    }

    @Test("alternate BPM SKU 0667 returns .bpm")
    func sku0667ReturnsBpm() {
        #expect(DeviceType.fromSku("0667") == .bpm)
    }

    @Test("alternate BPM SKU 0639 returns .bpm")
    func sku0639ReturnsBpm() {
        #expect(DeviceType.fromSku("0639") == .bpm)
    }

    // MARK: - Baby scale SKUs return .babyScale

    @Test("baby scale SKU 0220 returns .babyScale")
    func sku0220ReturnsBabyScale() {
        #expect(DeviceType.fromSku("0220") == .babyScale)
    }

    @Test("baby scale SKU 0222 returns .babyScale")
    func sku0222ReturnsBabyScale() {
        #expect(DeviceType.fromSku("0222") == .babyScale)
    }
}

// MARK: - DeviceType.serverValue / fromServerValue (Me App 2.0 unified API)

@Suite(.serialized)
struct DeviceTypeServerValueTests {

    // MARK: - serverValue

    @Test("scale.serverValue returns weight_scale")
    func scaleServerValue() {
        #expect(DeviceType.scale.serverValue == "weight_scale")
    }

    @Test("babyScale.serverValue returns baby_scale")
    func babyScaleServerValue() {
        #expect(DeviceType.babyScale.serverValue == "baby_scale")
    }

    @Test("bpm.serverValue returns bpm")
    func bpmServerValue() {
        #expect(DeviceType.bpm.serverValue == "bpm")
    }

    // MARK: - fromServerValue

    @Test("fromServerValue weight_scale returns .scale")
    func fromServerValueWeightScale() {
        #expect(DeviceType.fromServerValue("weight_scale") == .scale)
    }

    @Test("fromServerValue baby_scale returns .babyScale")
    func fromServerValueBabyScale() {
        #expect(DeviceType.fromServerValue("baby_scale") == .babyScale)
    }

    @Test("fromServerValue bpm returns .bpm")
    func fromServerValueBpm() {
        #expect(DeviceType.fromServerValue("bpm") == .bpm)
    }

    @Test("fromServerValue nil returns nil")
    func fromServerValueNil() {
        #expect(DeviceType.fromServerValue(nil) == nil)
    }

    @Test("fromServerValue unknown string returns nil")
    func fromServerValueUnknown() {
        #expect(DeviceType.fromServerValue("unknown_device") == nil)
    }

    @Test("fromServerValue is case-insensitive")
    func fromServerValueCaseInsensitive() {
        #expect(DeviceType.fromServerValue("WEIGHT_SCALE") == .scale)
        #expect(DeviceType.fromServerValue("Baby_Scale") == .babyScale)
        #expect(DeviceType.fromServerValue("BPM") == .bpm)
    }

    // MARK: - Round-trip

    @Test("serverValue/fromServerValue round-trip for all cases")
    func serverValueRoundTrip() {
        for deviceType in [DeviceType.scale, .babyScale, .bpm] {
            #expect(DeviceType.fromServerValue(deviceType.serverValue) == deviceType)
        }
    }
}
