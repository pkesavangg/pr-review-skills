//
//  ColorTokens.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 27/05/25.
//


import SwiftUI

struct ColorTokens {
    // Neutral colors
    static let neutral100 = Color("neutral-100")
    static let neutral200 = Color("neutral-200")
    static let neutral300 = Color("neutral-300")
    static let neutral400 = Color("neutral-400")
    static let neutral500 = Color("neutral-500")
    static let neutral600 = Color("neutral-600")
    static let neutral700 = Color("neutral-700")
    static let neutral750 = Color("neutral-750")
    static let neutral800 = Color("neutral-800")
    static let neutral900 = Color("neutral-900")
    
    // Brand colors
    static let teal100 = Color("teal-100")
    static let blue100 = Color("blue-100")
    static let blue500 = Color("blue-500")
    static let blue900 = Color("blue-900")
    static let blue800 = Color("blue-800")
    
    // Status colors
    static let red100 = Color("red-100")
    static let red800 = Color("red-800")
    static let red900 = Color("red-900")
    static let green100 = Color("green-100")
    static let yellow100 = Color("yellow-100")
    static let accucheck = Color("accucheck")
    
    static let ggBackground = Color("ggBackground")
    static let ggPrimary = Color("ggPrimary")
    static let ggSecondary100 = Color("ggSecondary100")
    static let ggSecondary900 = Color("ggSecondary900")

    // Promo colors (asset-backed)
    static let promoBlue100 = Color("promoBlue100")
    static let promoBlue900 = Color("promoBlue900")
    static let promoGreen100 = Color("promoGreen100")
    static let promoGreen900 = Color("promoGreen900")
    static let promoRed100 = Color("promoRed100")
    static let promoRed900 = Color("promoRed900")
    // MARK: - App Palette Definitions
    struct Palette {
        static let primary = AppColors.Palette(
            // Background
            backgroundPrimary: neutral100,
            backgroundPrimaryDisabled: neutral300,
            backgroundSecondary: neutral200,
            
            // Text
            textHeading: neutral900,
            textBody: neutral900,
            textSubheading: neutral600,
            textError: red900,
            textErrorDisabled: red100,
            textInverse: neutral100,
            textInverseSecondary: neutral200,
            
            // Support
            supportOverlay: neutral500,
            supportToastBackground: blue100,
            glow: neutral700,
            
            // Action
            actionPrimary: blue800,
            actionPrimaryPressed: blue900,
            actionPrimaryDisabled: blue500,
            actionSecondary: neutral800,
            actionSecondaryPressed: neutral900,
            actionSecondaryDisabled: neutral400,
            actionTertiary: neutral700,
            actionTertiaryPressed: neutral750,
            actionTertiaryDisabled: neutral400,
            
            actionInverse: neutral100,
            actionInversePressed: neutral200,
            actionInverseDisabled: neutral300,
            actionInverseSecondary: neutral200,
            
            actionError: red800,
            actionErrorPressed: red900,
            actionErrorDisabled: red100,
            
            // Brand
            brandMeAppPrimary: teal100,
            brandWgPrimary: blue900,
            
            // Status
            statusSuccess: green100,
            statusError: red900,
            statusStreak: yellow100,
            statusUtilityPrimary: neutral400,
            statusUtilitySecondary: neutral900,
            statusIconPrimary: blue900,
            statusIconSecondary: neutral600,
            statusIconPrimaryDisabled: blue500,
            statusIconSecondaryDisabled: neutral400,
            statusIconLoading: blue500,
            statusIconLoadingError: red100,
            
            // Logos
            logoPrimary: neutral100,
            logoSecondary: neutral900,
            
            ggBackground: ggBackground,
            ggPrimary: ggPrimary,
            ggSecondary100: ggSecondary100,
            ggSecondary900: ggSecondary900,
            
            // Promo
            promoBlue100: promoBlue100,
            promoBlue900: promoBlue900,
            promoGreen100: promoGreen100,
            promoGreen900: promoGreen900,
            promoRed100: promoRed100,
            promoRed900: promoRed900
        )
    }
}
