//
//  ScaleInfoUtils.swift
//  meApp
//
//  Created by AI Assistant
//

import Foundation

/// Utility class for scale information management, providing scale details lookup and image path resolution.
/// Converted from TypeScript scale-info.service.ts to Swift.
class ScaleInfoUtils {

    // MARK: - Properties

    /// All available scales with resolved image paths
    public var scales: [ScaleItemInfo] = []

    // MARK: - Initialization

    init() {
        // Initialize scales with image paths resolved
      self.scales = SCALES.map { scale in
            var updatedScale = scale
            updatedScale = ScaleItemInfo(
                productName: scale.productName,
                sku: scale.sku,
                imgPath: resolveImagePath(for: scale.sku),
                setupType: scale.setupType,
                bodyComp: scale.bodyComp
            )
            return updatedScale
        }
    }

    // MARK: - Public Methods

    /// Get scale information by SKU
    /// - Parameter sku: The scale SKU to search for
    /// - Returns: ScaleItemInfo if found, nil otherwise
    public func getScaleInfo(bySku sku: String) -> ScaleItemInfo? {
        return scales.first { $0.sku == sku }
    }

    /// Get scale information by scale name with fallback logic
    /// - Parameter scaleName: The scale name to search for
    /// - Returns: ScaleItemInfo if found, nil otherwise
    public func getScaleInfo(byScaleName scaleName: String?) -> ScaleItemInfo? {
        guard let scaleName = scaleName?.trimmingCharacters(in: .whitespacesAndNewlines) else {
            return nil
        }

        let upperScaleName = scaleName.uppercased()
        var scaleInfo: ScaleItemInfo?

        switch upperScaleName {
        case "10376B", "0376B":
            scaleInfo = getScaleInfo(bySku: "0376")

        case "0202B", "1202B", "202B":
            scaleInfo = getScaleInfo(bySku: "0375")

        case "11251B", "1251B", "01251B":
            scaleInfo = getScaleInfo(bySku: "0380")

        case "1270B", "11270B", "01270B":
            scaleInfo = getScaleInfo(bySku: "0382")

        case "LS212-B":
            scaleInfo = getScaleInfo(bySku: "0383")
            if var info = scaleInfo {
                info = ScaleItemInfo(
                    productName: "Bluetooth Scale", // Custom nickname for LS212-B
                    sku: info.sku,
                    imgPath: info.imgPath,
                    setupType: info.setupType,
                    bodyComp: info.bodyComp
                )
                scaleInfo = info
            }

        case "GG BS 0412":
            scaleInfo = getScaleInfo(bySku: "0412")
            if var info = scaleInfo {
                info = ScaleItemInfo(
                    productName: "AccuCheck Verve Smart Scale", // Custom nickname for GG BS 0412
                    sku: info.sku,
                    imgPath: info.imgPath,
                    setupType: info.setupType,
                    bodyComp: info.bodyComp
                )
                scaleInfo = info
            }

        default:
            // Return a default scale info with null SKU if not found
            scaleInfo = ScaleItemInfo(
                productName: "Unknown Scale",
                sku: "",
                imgPath: nil,
                setupType: .bluetooth,
                bodyComp: false
            )
        }

        return scaleInfo
    }

    // MARK: - Private Methods

    /// Resolve image path for a given SKU
    /// - Parameter sku: The scale SKU
    /// - Returns: Image path string or nil
    private func resolveImagePath(for sku: String) -> String? {
        switch sku {
        case "0383", "0378":
            return "scale_images_png_0383" // PNG format for these SKUs

        case "0412":
            return "scale_images_svg_\(sku)" // SVG format for 0412

        default:
            return "scale_images_png_\(sku)" // Default to PNG format
        }
    }
}

// MARK: - Singleton Support (Optional)

extension ScaleInfoUtils {
    /// Shared instance for global access (similar to Angular service)
    static let shared = ScaleInfoUtils()
}

// MARK: - Additional Convenience Methods

extension ScaleInfoUtils {
    /// Check if a scale supports body composition by SKU
    /// - Parameter sku: The scale SKU
    /// - Returns: True if scale supports body composition, false otherwise
    public func supportsBodyComposition(sku: String) -> Bool {
        return getScaleInfo(bySku: sku)?.bodyComp ?? false
    }

    /// Get all scales of a specific setup type
    /// - Parameter setupType: The setup type to filter by
    /// - Returns: Array of ScaleItemInfo matching the setup type
    public func getScales(bySetupType setupType: ScaleSetupType) -> [ScaleItemInfo] {
        return scales.filter { $0.setupType == setupType }
    }

    /// Get all scales that support body composition
    /// - Returns: Array of ScaleItemInfo that support body composition
    public func getBodyCompositionScales() -> [ScaleItemInfo] {
        return scales.filter { $0.bodyComp }
    }

    /// Get all basic scales (weight-only)
    /// - Returns: Array of ScaleItemInfo that are weight-only
    public func getBasicScales() -> [ScaleItemInfo] {
        return scales.filter { !$0.bodyComp }
    }
}
