//
//  Color.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//

import SwiftUI

enum AppColors {
    enum Theme {
        case primary

        var palette: Palette {
            Palette.forTheme(self)
        }
    }

    struct Palette {
        // Background colors
        let backgroundPrimary: Color
        let backgroundPrimaryDisabled: Color
        let backgroundSecondary: Color

        // Text colors
        let textHeading: Color
        let textBody: Color
        let textSubheading: Color
        let textDisabled: Color
        let textError: Color
        let textErrorDisabled: Color
        let textInverse: Color
        let textInverseSecondary: Color

        // Support colors
        let supportOverlay: Color
        let supportToastBackground: Color
        let glow: Color

        // Action colors
        let actionPrimary: Color
        let actionPrimaryPressed: Color
        let actionPrimaryDisabled: Color
        let actionSecondary: Color
        let actionSecondaryPressed: Color
        let actionSecondaryDisabled: Color
        let actionTertiary: Color
        let actionTertiaryPressed: Color
        let actionTertiaryDisabled: Color
        let actionInverse: Color
        let actionInversePressed: Color
        let actionInverseDisabled: Color
        let actionInverseSecondary: Color
        let actionSuccess: Color
        let actionSuccessPressed: Color
        let actionSuccessDisabled: Color
        let actionError: Color
        let actionErrorPressed: Color
        let actionErrorDisabled: Color

        // Brand colors
        let brandMeAppPrimary: Color
        let brandWgPrimary: Color
        let babyScaleColor: Color
        let weightScaleColor: Color
        let bpmColor: Color

        // Icon colors
        let statusSuccess: Color
        let statusError: Color
        let statusStreak: Color
        let statusUtilityPrimary: Color
        let statusUtilitySecondary: Color
        let statusIconPrimary: Color
        let statusIconSecondary: Color
        let statusIconPrimaryDisabled: Color
        let statusIconSecondaryDisabled: Color
        let statusIconLoading: Color
        let statusIconLoadingError: Color

        // Logo colors
        let logoPrimary: Color
        let logoSecondary: Color

        let ggBackground: Color
        let ggPrimary: Color
        let ggSecondary100: Color
        let ggSecondary900: Color

        // Promo palette colors
        let promoBlue100: Color
        let promoBlue900: Color
        let promoGreen100: Color
        let promoGreen900: Color
        let promoRed100: Color
        let promoRed900: Color

        // GG Named Colors (asset-backed defaults provided via extension)
        let ggSecondary: Color
        let ggSecondaryPressed: Color
        let ggSecondaryDisabled: Color

        let promoRed: Color
        let promoRedPressed: Color
        let promoRedDisabled: Color

        let promoBlue: Color
        let promoBluePressed: Color
        let promoBlueDisabled: Color

        let promoGreen: Color
        let promoGreenPressed: Color
        let promoGreenDisabled: Color

        static func forTheme(_ theme: Theme) -> Palette {
            switch theme {
            case .primary:
                return ColorTokens.Palette.primary
            }
        }
    }
}

extension AppColors.Palette {
    /// Per-product accent color used for product-type labels and values (per the
    /// Me.Health 2.0 design): weight → blue, blood pressure → green, baby → purple.
    /// Single source of truth so every surface (dashboard header, entry header,
    /// history rows, latest-entry value) stays consistent. Other UI stays neutral.
    func productAccentColor(for type: EntryType) -> Color {
        switch type {
        case .scale: return weightScaleColor
        case .bpm:   return bpmColor
        case .baby:  return babyScaleColor
        }
    }
}
