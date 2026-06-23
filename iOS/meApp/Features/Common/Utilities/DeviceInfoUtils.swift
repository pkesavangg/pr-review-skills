//
//  DeviceInfoUtils.swift
//  meApp
//
//  Created by AI Assistant
//

import Foundation

/// Utility class for scale information management, providing scale details lookup and image path resolution.
/// Converted from TypeScript scale-info.service.ts to Swift.
class DeviceInfoUtils {

    // MARK: - Properties

    /// All available scales with resolved image paths
    public var scales: [DeviceItemInfo] = []

    // MARK: - Initialization

    init() {
        // Initialize scales and BPM devices with image paths resolved
        let allDevices = SCALES + BPMS
        self.scales = allDevices.map { scale in
            var updatedScale = scale
            updatedScale = DeviceItemInfo(
                productName: scale.productName,
                sku: scale.sku,
                imgPath: resolveImagePath(for: scale.sku) ?? "",
                setupType: scale.setupType,
                bodyComp: scale.bodyComp
            )
            return updatedScale
        }
    }

    // MARK: - Public Methods

    /// Get scale information by SKU
    /// Maps SKU for display lookup (e.g., 0022 -> 0383) for SCALES lookup only.
    /// - Parameter sku: The scale SKU to search for (original SKU, e.g., "0022")
    /// - Returns: DeviceItemInfo if found, nil otherwise
    public func getDeviceInfo(bySku sku: String) -> DeviceItemInfo? {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        if let row = scales.first(where: { $0.sku == lookupSku }) {
            return row
        }
        let bpmPrimary = primaryBpmSetupSku(for: lookupSku)
        if bpmPrimary != lookupSku, let row = scales.first(where: { $0.sku == bpmPrimary }) {
            return row
        }
        return nil
    }

    // Get scale information by scale name with fallback logic
    // - Parameter scaleName: The scale name to search for
    // - Returns: DeviceItemInfo if found, nil otherwise
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    public func getDeviceInfo(byDeviceName scaleName: String?) -> DeviceItemInfo? {
        guard let scaleName = scaleName?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }

        let upperDeviceName = scaleName.uppercased()
        var scaleInfo: DeviceItemInfo?

        switch upperDeviceName {
        case "10376B", "0376B":
            scaleInfo = getDeviceInfo(bySku: "0376")

        case "0202B", "1202B", "202B":
            scaleInfo = getDeviceInfo(bySku: "0375")

        case "11251B", "1251B", "01251B":
            scaleInfo = getDeviceInfo(bySku: "0380")

        case "1270B", "11270B", "01270B":
            scaleInfo = getDeviceInfo(bySku: "0382")

        case "LS212-B":
            scaleInfo = getDeviceInfo(bySku: "0383")
            if var info = scaleInfo {
                info = DeviceItemInfo(
                    productName: "Bluetooth Scale", // Custom nickname for LS212-B
                    sku: info.sku,
                    imgPath: info.imgPath,
                    setupType: info.setupType,
                    bodyComp: info.bodyComp
                )
                scaleInfo = info
            }

        case "GG-RPM 0022":
            scaleInfo = getDeviceInfo(bySku: "0383")
            if var info = scaleInfo {
                info = DeviceItemInfo(
                    productName: "Bluetooth Scale", // Custom nickname for gG-RPM 0022
                    sku: info.sku,
                    imgPath: info.imgPath,
                    setupType: info.setupType,
                    bodyComp: info.bodyComp
                )
                scaleInfo = info
            }

        case "GG BS 0412":
            scaleInfo = getDeviceInfo(bySku: "0412")
            if var info = scaleInfo {
                info = DeviceItemInfo(
                    productName: "AccuCheck Verve Smart Scale", // Custom nickname for GG BS 0412
                    sku: info.sku,
                    imgPath: info.imgPath,
                    setupType: info.setupType,
                    bodyComp: info.bodyComp
                )
                scaleInfo = info
            }

        case "GG BS 0220":
            scaleInfo = getDeviceInfo(bySku: "0220")

        case "GG BS 0222":
            scaleInfo = getDeviceInfo(bySku: "0222")

        default:
            // Check if the device name matches a BPM device
            if let bpmInfo = getBpmInfo(byDeviceName: upperDeviceName) {
                scaleInfo = bpmInfo
            } else {
                // Return a default scale info with null SKU if not found
                scaleInfo = DeviceItemInfo(
                    productName: "Unknown Scale",
                    sku: "",
                    imgPath: "",
                    setupType: .bluetooth,
                    bodyComp: false
                )
            }
        }

        return scaleInfo
    }

    // MARK: - Private Methods

    /// Resolve image path for a given SKU
    /// Maps SKU for display lookup (e.g., 0022 -> 0383) for SCALES lookup only.
    /// - Parameter sku: The scale SKU (original SKU, e.g., "0022")
    /// - Returns: Image path string or nil
    private func resolveImagePath(for sku: String) -> String? {
        // Map SKU for SCALES lookup only (0022 is not in SCALES, but 0383 is)
        let lookupSku = DeviceHelper.mapSkuForDisplay(sku)
        let allDevices = SCALES + BPMS
        if let path = allDevices.first(where: { $0.sku == lookupSku })?.imgPath {
            return path
        }
        let bpmPrimary = primaryBpmSetupSku(for: lookupSku)
        return allDevices.first { $0.sku == bpmPrimary }?.imgPath
    }
}

// MARK: - Singleton Support (Optional)

extension DeviceInfoUtils {
    /// Shared instance for global access (similar to Angular service)
    static let shared = DeviceInfoUtils()
}

// MARK: - Additional Convenience Methods

extension DeviceInfoUtils {
    /// Get BPM device info by device name (broadcast name from SDK).
    /// - Parameter deviceName: The uppercased BPM device name
    /// - Returns: DeviceItemInfo if the name matches a known BPM device
    public func getBpmInfo(byDeviceName deviceName: String) -> DeviceItemInfo? {
        // First try matching by SKU codes embedded in the device name (e.g. "gG BPM 0603").
        let sortedCodes = bpmSkus.sorted { $0.count > $1.count }
        for code in sortedCodes where deviceName.contains(code) {
            guard let item = bpmCatalogItem(forEnteredCode: code) else { continue }
            return getDeviceInfo(bySku: item.sku)
        }

        // Fallback: match by broadcast name prefix (e.g. "1490BT" matches "1490BT1").
        // Some BPM monitors append a user number to their broadcast name.
        let upperName = deviceName.uppercased()
        for bpm in BPMS {
            guard let broadcastName = bpm.broadcastName, !broadcastName.isEmpty else { continue }
            if upperName.hasPrefix(broadcastName.uppercased()) {
                return getDeviceInfo(bySku: bpm.sku)
            }
        }

        return nil
    }

    /// Checks whether the given SKU belongs to a BPM device.
    public func isBpmDevice(sku: String) -> Bool {
        return bpmSkus.contains(sku)
    }

    /// Check if a scale supports body composition by SKU
    /// - Parameter sku: The scale SKU
    /// - Returns: True if scale supports body composition, false otherwise
    public func supportsBodyComposition(sku: String) -> Bool {
        return getDeviceInfo(bySku: sku)?.bodyComp ?? false
    }

    /// Get all scales of a specific setup type
    /// - Parameter setupType: The setup type to filter by
    /// - Returns: Array of DeviceItemInfo matching the setup type
    public func getDevices(bySetupType setupType: DeviceSetupType) -> [DeviceItemInfo] {
        return scales.filter { $0.setupType == setupType }
    }

    /// Get all scales that support body composition
    /// - Returns: Array of DeviceItemInfo that support body composition
    public func getBodyCompositionScales() -> [DeviceItemInfo] {
        return scales.filter { $0.bodyComp }
    }

    /// Get all basic scales (weight-only)
    /// - Returns: Array of DeviceItemInfo that are weight-only
    public func getBasicScales() -> [DeviceItemInfo] {
        return scales.filter { !$0.bodyComp }
    }
}
