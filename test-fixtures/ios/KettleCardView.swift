// Fixture for references/ios/asset-references.md. Deliberate triggers — do not fix.
//
// Expected findings:
//   P1  asset referenced by a raw string literal at a call site (Image / Color / UIImage)
//   P1  asset name that doesn't exist in any .xcassets (the "icon.cirelce" typo case)
//   P2  asset name that isn't meaningful / breaks the catalog convention
//   P2  token constant whose name disagrees with its asset
//   Nit repeated SF Symbol string literal

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

// This is NOT the token file — literals here are findings, not definitions.
struct KettleCardView: View {

    var body: some View {
        VStack {
            // P1 — raw literal, and the name is misspelled: no "icon.cirelce" exists in
            //      any .xcassets, so this renders an empty view with no error at runtime.
            //      A "." is also invalid in the Android twin's resource name.
            Image("icon.cirelce")

            // P1 — raw literal; the same string is retyped in DeviceRowCard.swift and
            //      ConnectivityChip.swift. Wants ImageTokens.bluetooth.
            Image("bluetooth")

            // P1 + P2 — raw literal AND a Figma-export name that says nothing.
            Image("Group 3")

            // P1 + P2 — raw literal AND iteration noise baked into the name.
            Image("kettle_hero_final_v2")

            // P1 — Color literal outside Theme/Tokens/ColorTokens.swift.
            Text("Heating")
                .foregroundStyle(Color("Theme/theme-500"))

            // P1 — UIKit form of the same defect.
            #if canImport(UIKit)
            if let logo = UIImage(named: "img1") {
                Image(uiImage: logo)
            }
            #endif

            // Nit — the same SF Symbol literal repeated across many views.
            Image(systemName: "gear")
            Image(systemName: "gear")
        }
    }
}

// A token file is where a literal legitimately lives — exactly once. These two
// entries are still findings, but for a different reason: the constant names lie.
enum ImageTokens {
    // P2 — constant name disagrees with the asset it points at.
    static let bluetooth = Image("kettle_hero")

    // P2 — meaningless constant name over a perfectly good asset.
    static let icon1 = Image("ic_bluetooth")

    // Correct: name and asset describe the same thing.
    static let kettleHero = Image("kettle_hero")
}
