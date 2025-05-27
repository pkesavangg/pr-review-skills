//
//  AppColors.swift
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
        let backgroundSecondary: Color
        
        // Text colors
        let textHeading: Color
        let textBody: Color
        let textSubheading: Color
        let textError: Color
        let textErrorDisabled: Color
        let textInverse: Color
        let textInverseSecondary: Color
        
        // Support colors
        let supportOverlay: Color
        let supportToastBackground: Color
        
        // Action colors
        let actionPrimary: Color
        let actionPrimaryDisabled: Color
        let actionSecondary: Color
        let actionSecondaryDisabled: Color
        
        // Brand colors
        let brandPrimary: Color
        
        // Icon colors
        let iconGoal: Color
        let iconStreak: Color
        let iconUtility: Color
        
        static func forTheme(_ theme: Theme) -> Palette {
            switch theme {
            case .primary:
                return Palette(
                    // Background
                    backgroundPrimary: Color("neutral-100"),
                    backgroundSecondary: Color("neutral-200"),
                    
                    // Text
                    textHeading: Color("neutral-900"),
                    textBody: Color("neutral-900"),
                    textSubheading: Color("neutral-600"),
                    textError: Color("red-900"),
                    textErrorDisabled: Color("red-100"),
                    textInverse: Color("neutral-100"),
                    textInverseSecondary: Color("neutral-200"),
                    
                    // Support
                    supportOverlay: Color("neutral-500"),
                    supportToastBackground: Color("blue-100"),
                    
                    // Action
                    actionPrimary: Color("blue-900"),
                    actionPrimaryDisabled: Color("blue-500"),
                    actionSecondary: Color("neutral-900"),
                    actionSecondaryDisabled: Color("neutral-400"),
                    
                    // Brand
                    brandPrimary: Color("teal-100"),
                    
                    // Icon
                    iconGoal: Color("green-100"),
                    iconStreak: Color("yellow-100"),
                    iconUtility: Color("neutral-400")
                )
            }
        }
    }
}
