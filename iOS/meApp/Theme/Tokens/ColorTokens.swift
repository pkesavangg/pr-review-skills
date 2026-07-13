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
    static let green800 = Color("green-800")
    static let green900 = Color("green-900")
    static let yellow100 = Color("yellow-100")
    static let accucheck = Color("accucheck")

    // Blood-pressure classification colors (AHA), light + dark variants
    static let bpElevated = Color("bp-elevated")
    static let bpStage1 = Color("bp-stage1")
    static let bpCrisis = Color("bp-crisis")

    static let ggBackground = Color("ggBackground")
    static let ggPrimary = Color("ggPrimary")
    static let ggSecondary100 = Color("ggSecondary100")
    static let ggSecondary200 = Color("ggSecondary200")
    static let ggSecondary800 = Color("ggSecondary800")
    static let ggSecondary900 = Color("ggSecondary900")

    // Promo colors (asset-backed)
    static let promoBlue100 = Color("promoBlue100")
    static let promoBlue200 = Color("promoBlue200")
    static let promoBlue800 = Color("promoBlue800")
    static let promoBlue900 = Color("promoBlue900")
    static let promoGreen100 = Color("promoGreen100")
    static let promoGreen200 = Color("promoGreen200")
    static let promoGreen800 = Color("promoGreen800")
    static let promoGreen900 = Color("promoGreen900")
    static let promoRed100 = Color("promoRed100")
    static let promoRed200 = Color("promoRed200")
    static let promoRed800 = Color("promoRed800")
    static let promoRed900 = Color("promoRed900")

    // Device / scale accent colors
    static let weightScale = Color("weightScale")
    static let bpm = Color("bpm")
    static let babyScale = Color("babyScale")
    // MARK: - App Palette Definitions
    struct Palette {
        static let primary = AppColors.Palette(
            // Background
            backgroundPrimary: neutral100,
            backgroundPrimaryDisabled: neutral300,
            backgroundSecondary: neutral200,

            // Text
            textHeading: neutral800,
            textBody: neutral800,
            textSubheading: neutral700,
            textDisabled: neutral400,
            textError: red800,
            textErrorDisabled: red100,
            textInverse: neutral100,
            textInverseSecondary: neutral200,

            // Support
            supportOverlay: neutral600,
            supportToastBackground: blue100,
            glow: neutral500,

            // Action
            actionPrimary: neutral800,
            actionPrimaryPressed: neutral900,
            actionPrimaryDisabled: neutral400,
            actionSecondary: neutral100,
            actionSecondaryPressed: neutral200,
            actionSecondaryDisabled: neutral300,
            actionTertiary: neutral700,
            actionTertiaryPressed: neutral750,
            actionTertiaryDisabled: neutral400,

            actionInverse: neutral100,
            actionInversePressed: neutral200,
            actionInverseDisabled: neutral300,
            actionInverseSecondary: neutral200,
            actionSuccess: green800,
            actionSuccessPressed: green900,
            actionSuccessDisabled: green100,

            actionError: red800,
            actionErrorPressed: red900,
            actionErrorDisabled: red100,

            // Brand
            brandMeAppPrimary: teal100,
            brandWgPrimary: blue800,
            babyScaleColor: babyScale,
            weightScaleColor: weightScale,
            bpmColor: bpm,

            // Status
            statusSuccess: green800,
            statusError: red800,
            statusStreak: yellow100,
            statusUtilityPrimary: neutral400,
            statusUtilitySecondary: neutral800,
            statusIconPrimary: neutral800,
            statusIconSecondary: neutral700,
            statusIconPrimaryDisabled: neutral400,
            statusIconSecondaryDisabled: neutral400,
            statusIconLoading: blue100,
            statusIconLoadingError: red100,

            // Logos
            logoPrimary: neutral800,
            logoSecondary: neutral100,

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
            promoRed900: promoRed900,

            ggSecondary: ggSecondary800,
            ggSecondaryPressed: ggSecondary900,
            ggSecondaryDisabled: ggSecondary200,

            // Promo Red
            promoRed: promoRed800,
            promoRedPressed: promoRed900,
            promoRedDisabled: promoRed200,

            // Promo Blue
            promoBlue: promoBlue800,
            promoBluePressed: promoBlue900,
            promoBlueDisabled: promoBlue200,

            // Promo Green
            promoGreen: promoGreen800,
            promoGreenPressed: promoGreen900,
            promoGreenDisabled: promoGreen200
        )
    }
}
