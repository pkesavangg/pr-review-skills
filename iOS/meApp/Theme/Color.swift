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
                return ColorTokens.Palette.primary
            }
        }
    }
}


