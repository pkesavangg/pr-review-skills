///
///  DeviceTypeFromSkuTests.swift
///  meAppTests
///
///  Regression tests for DeviceType.fromSku(_:) ensuring all device categories
///  are classified correctly after the dynamic SKU-based classification was introduced.
///

@testable import meApp
import Testing

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
