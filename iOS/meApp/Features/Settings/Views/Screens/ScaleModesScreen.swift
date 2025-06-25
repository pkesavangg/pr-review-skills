//
//  ScaleModesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

struct ScaleModesScreen: View {
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    let lang = ScaleModesStrings.self
    
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: lang.modeTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: CommonStrings.save.uppercased(),
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: false,
                        action: {
                            // TODO: Add action
                        }
                    )
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )
            VStack(alignment: .leading, spacing: .spacingMD) {
                descriptionWithBIAButton
                Spacer()
            }
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Description with Inline Button
    private var descriptionWithBIAButton: some View {
        InlineButtonText(
            prefix: lang.biaExplanationPrefix,
            linkText: lang.biaButtonText,
            suffix: lang.biaExplanationSuffix
        ) {
            // TODO: Add action
        }
        .padding(.top, .spacingMD)
    }
}

#Preview {
    ScaleModesScreen()
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
}
