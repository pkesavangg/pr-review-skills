//
//  ScaleSetupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//
import Foundation

// MARK: - Strings lookup
struct ScaleSetupStrings {
    static let troubleSettingUp = "If you're having trouble setting up your scale, press the help button in the top right to connect with our team."

    // Default fallback content
    private static let defaultContent = ScaleSetupInfoContent(
        imageName: "default_scale_image",
        title: "Smart Scale",
        scaleName: "Scale Setup",
        description: troubleSettingUp,
        buttonTitle: nil
    )

    // Scale type names (previously called "subtitle")
    private static let scaleNameWiFi = "Wi-Fi Smart Scale Setup"
    private static let scaleNameBodyFat = "AppSync Body Fat Scale"
    private static let scaleNameBathroom = "AppSync Bathroom Scale"
    private static let scaleNameBasicBathroom = "Basic AppSync Bathroom Scale"

    /// Returns all UI strings and asset names for the given scale SKU
    static func info(for sku: String) -> ScaleSetupInfoContent {
        switch sku {
        case "0396", "0397":
            return makeContent(sku, image: AppAssets.scale0396_0397, scaleName: scaleNameWiFi, button: "Get your scale's MAC address")

        case "0341":
            return makeContent(sku, image: AppAssets.scale0341, scaleName: scaleNameBodyFat)

        case "0342":
            return makeContent(sku, image: AppAssets.scale0342, scaleName: scaleNameBathroom)

        case "0343":
            return makeContent(sku, image: AppAssets.scale0343, scaleName: scaleNameBodyFat)

        case "0345":
            return makeContent(sku, image: AppAssets.scale0345, scaleName: scaleNameBodyFat)

        case "0346":
            return makeContent(sku, image: AppAssets.scale0346, scaleName: scaleNameBodyFat)

        case "0347":
            return makeContent(sku, image: AppAssets.scale0347, scaleName: scaleNameBodyFat)

        case "0358":
            return makeContent(sku, image: AppAssets.scale0358, scaleName: scaleNameBasicBathroom)

        case "0359":
            return makeContent(sku, image: AppAssets.scale0359, scaleName: scaleNameBasicBathroom)

        case "0364":
            return makeContent(sku, image: AppAssets.scale0364, scaleName: scaleNameBathroom)

        case "0369":
            return makeContent(sku, image: AppAssets.scale0369, scaleName: scaleNameBodyFat)

        case "0370":
            return makeContent(sku, image: AppAssets.scale0370, scaleName: scaleNameBodyFat)

        case "0371":
            return makeContent(sku, image: AppAssets.scale0371, scaleName: scaleNameBathroom)

        default:
            return defaultContent
        }
    }

    /// Centralized factory to reduce repetition in `info(for:)`
    private static func makeContent(_ sku: String, image: String, scaleName: String, button: String? = nil) -> ScaleSetupInfoContent {
        ScaleSetupInfoContent(
            imageName: image,
            title: "Model \(sku)",
            scaleName: scaleName,
            description: troubleSettingUp,
            buttonTitle: button
        )
    }
}
