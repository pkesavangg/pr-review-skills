//  ConnectionPromptView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/07/25.

import SwiftUI

/// A generic prompt that shows a title, optional subtitle, and a pulsing connection indicator.
/// Use it for flows such as *Wake Your Scale* or *Gathering Networks* by customizing the title, subtitle, and icon.
struct ConnectionPromptView: View {
    @Environment(\.appTheme) private var theme

    // MARK: - Props
    let title: String
    let subtitle: String?
    let image: String
    /// Optional scale image asset name. If provided, displays the scale image instead of the connection indicator.
    let scaleImagePath: String?

    /// Designated initializer.
    /// - Parameters:
    ///   - title: Heading text.
    ///   - subtitle: Optional secondary text shown beneath the heading.
    ///   - image: Name of the asset to display inside the indicator (used when scaleImagePath is nil).
    ///   - scaleImagePath: Optional scale image asset name. If provided, displays scale image instead of connection indicator.
    init(title: String = ScaleSetupStrings.wakeYourScaleTitle, subtitle: String? = nil, image: String = AppAssets.bluetooth, scaleImagePath: String? = nil) {
        self.title = title
        self.subtitle = subtitle
        self.image = image
        self.scaleImagePath = scaleImagePath
    }

    // MARK: - Body
    var body: some View {
        VStack(spacing: .spacingMD) {
            VStack(spacing: .spacingXS) {
                Text(title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)

                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.center)
                }
            }
            .accessibilityElement(children: .combine)

            VStack(spacing: scaleImagePath == nil ? 0 : 90) {
                if let scaleImagePath {
                    Image(scaleImagePath)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 180, height: 180)
                        .cornerRadius(.radiusLG)
                        .themeDropShadow()
                        .padding(.top, .spacingXS)
                        .accessibilityHidden(true)
                }

                ConnectionIndicatorView(image: image)
                    .accessibilityHidden(true)
            }

        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
}

// MARK: - Previews
#Preview("Wake Scale") {
    ConnectionPromptView(
        title: ScaleSetupStrings.wakeYourScaleTitle,
        subtitle: ScaleSetupStrings.wakeYourScaleSubtitle,
        image: AppAssets.bluetooth
    )
    .environmentObject(Theme.shared)
}

#Preview("Gathering Networks") {
    ConnectionPromptView(
        title: ScaleSetupStrings.gatheringNetworksTitle,
        image: AppAssets.wifi
    )
    .environmentObject(Theme.shared)
} 
