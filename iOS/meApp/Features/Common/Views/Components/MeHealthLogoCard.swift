//
//  MeHealthLogoCard.swift
//  meApp
//
//  The Phase 2 brand lockup used on the Loading and Landing screens
//  (Figma 32224-28400): the "my everyday health" wordmark over a
//  "By Greater Goods" subtitle, on a rounded card.
//

import SwiftUI

/// Static text for the shared brand logo card.
enum MeHealthLogoStrings {
    /// VoiceOver reads the whole card as the brand name.
    static let accLogoLabel = "my everyday health, by Greater Goods"
}

/// Shared brand logo card: the "my everyday health" wordmark plus a
/// "By Greater Goods" subtitle on a `backgroundPrimary` rounded card.
/// The wordmark swaps to a light-text variant in dark mode so it stays
/// legible on the dark card.
struct MeHealthLogoCard: View {
    @Environment(\.appTheme) private var theme
    // The wordmark is a single asset with light/dark appearance variants baked
    // in, so UIKit resolves it against the window's interface style — the same
    // trait the card's `backgroundPrimary` (a dynamic asset color) resolves
    // against. Selecting the variant in code via `@Environment(\.colorScheme)`
    // desynced from the background inside a `NavigationStack` (the Landing
    // screen): that environment doesn't adopt the app's forced appearance
    // override, so the wordmark rendered same-colour-on-same-colour and vanished
    // while the card behind it followed the forced scheme. Letting the asset
    // catalog pick the variant keeps the wordmark and card in lockstep everywhere.

    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingXSM) {
            Image(AppAssets.meHealthLogo)
                .resizable()
                .aspectRatio(contentMode: .fit)
                // `.frame` defaults to center alignment: if the wordmark is ever
                // sized narrower than 210pt it would drift right of the leading-
                // aligned "By Greater Goods" subtitle. Pin it leading so the two
                // share a left edge (Figma 32224-28400).
                .frame(width: 210, alignment: .leading)

            Text(commonLang.byGreaterGoods)
                .fontOpenSans(.body5)
                .foregroundColor(theme.textBody)
        }
        .padding(.horizontal, .spacingXL)
        .padding(.vertical, .spacingLG)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusMD)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(MeHealthLogoStrings.accLogoLabel)
    }
}

#Preview("Light") {
    ZStack {
        Color("neutral-200").ignoresSafeArea()
        MeHealthLogoCard()
    }
    .environmentObject(Theme.shared)
}

// Guards this PR's headline fix — the wordmark being invisible in dark mode —
// so the light-on-dark variant is verifiable at a glance and against regression.
#Preview("Dark") {
    ZStack {
        Color("neutral-900").ignoresSafeArea()
        MeHealthLogoCard()
    }
    .environmentObject(Theme.shared)
    .preferredColorScheme(.dark)
}
