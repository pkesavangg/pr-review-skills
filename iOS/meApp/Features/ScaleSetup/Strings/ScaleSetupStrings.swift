//
//  ScaleSetupStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//

import Foundation

// MARK: - Content model
struct ScaleSetupInfoContent {
    /// Asset name for the main scale image
    let imageName: String
    /// Title displayed at the top of the modal/screen
    let title: String
    /// Subtitle shown below the title
    let subtitle: String
    /// Descriptive body copy
    let description: String
    /// Optional button title shown below the description
    let buttonTitle: String?
}

// MARK: - Strings lookup
/// Static lookup helper that returns the correct string bundle for a given scale SKU.
///
/// Extend the `switch` with additional SKUs when new models are added.
struct ScaleSetupStrings {
    /// Returns all copy and asset names for the supplied SKU.
    /// - Parameter sku: The unique identifier (e.g., "0397") for the scale model.
    /// - Returns: `ScaleSetupInfoContent` containing all strings and asset identifiers.
    static func info(for sku: String) -> ScaleSetupInfoContent {
        switch sku {
        case "0397", "0396": // 0396 & 0397 share the same artwork
            return ScaleSetupInfoContent(
                imageName: "0396_0397",
                title: "Model \(sku)",
                subtitle: "Wi-Fi Smart Scale Setup",
                description: "If you're having trouble setting up your scale, press the help button in the top right to connect with our team.",
                buttonTitle: "Get your scale's MAC address"
            )

        default:
            // Fallback – always return *something* so the UI does not crash on unknown SKUs.
            return ScaleSetupInfoContent(
                imageName: "default_scale_image",
                title: "Smart Scale",
                subtitle: "Scale Setup",
                description: "If you're having trouble setting up your scale, press the help button in the top right to connect with our team.",
                buttonTitle: nil
            )
        }
    }
}
