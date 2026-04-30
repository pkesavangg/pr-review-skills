//  AppTypographyConformance.swift
//  meApp
//
//  bridges the app Typography -> ggInAppMessagingPackage IAMTypography
//
//  Created by Cursor AI on 03/07/25.

import SwiftUI

#if canImport(ggInAppMessagingPackage)
import ggInAppMessagingPackage

/// Concrete implementation of `IAMTypography` backed by the app's `Typography` token set.
@available(iOS 17.0, macOS 14.0, *)
struct AppTypographyTokens: IAMTypography {
    // MARK: - Helper
    private func openSans(size: CGFloat, weight: Font.Weight) -> Font {
        return .custom("OpenSans-Regular", size: size).weight(weight)
    }

    // Headings – following CustomTextStyle sizes
    var heading1: Font { openSans(size: 60, weight: .heavy)}
    var heading2: Font { openSans(size: 50, weight: .heavy)}
    var heading3: Font { openSans(size: 36, weight: .bold)}
    var heading4: Font { openSans(size: 24, weight: .bold)}
    var heading5: Font { openSans(size: 16, weight: .bold)}

    // Sub-headings
    var subHeading1: Font { openSans(size: 16, weight: .regular) }
    var subHeading2: Font { openSans(size: 14, weight: .regular) }

    // Body
    var body1: Font { openSans(size: 20, weight: .regular) }
    var body2: Font { openSans(size: 16, weight: .regular) }
    var body3: Font { openSans(size: 14, weight: .regular) }

    // Links
    var link1: Font { openSans(size: 16, weight: .semibold) }
    var link2: Font { openSans(size: 12, weight: .semibold) }

    // Buttons
    var button1: Font { openSans(size: 16, weight: .semibold) }
    var button2: Font { openSans(size: 14, weight: .semibold) }
}

#endif 
