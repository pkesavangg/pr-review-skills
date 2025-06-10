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
    static let neutral900 = Color("neutral-900")
    
    // Brand colors
    static let teal100 = Color("teal-100")
    static let blue100 = Color("blue-100")
    static let blue500 = Color("blue-500")
    static let blue900 = Color("blue-900")
    
    // Status colors
    static let red100 = Color("red-100")
    static let red900 = Color("red-900")
    static let green100 = Color("green-100")
    static let yellow100 = Color("yellow-100")

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
            
            // Action
            actionPrimary: blue900,
            actionPrimaryDisabled: blue500,
            actionSecondary: neutral900,
            actionSecondaryDisabled: neutral400,
            actionTertiary: neutral600,
            actionTertiaryDisabled: neutral400,
            
            // Brand
            brandMeAppPrimary: teal100,
            brandWgPrimary: blue900,
            
            // Status
            statusSuccess: green100,
            statusError: red900,
            statusStreak: yellow100,
            statusUtility: neutral400,
            statusIconPrimary: blue900,
            statusIconSecondary: neutral600,
            statusIconDisabled: neutral400,
            statusIconLoading: blue500,
            statusIconLoadingError: red100
        )
    }
}
