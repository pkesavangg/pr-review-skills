//
//  BabySetupDoneView.swift
//  meApp
//

import SwiftUI

/// "You're Done!" — final closing screen shown after both the full baby-profile
/// path and the skip path complete. Offers GO TO DASHBOARD and ADD A DEVICE actions.
struct BabySetupDoneView: View {
    @EnvironmentObject private var store: BabyScaleSetupStore
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.appTheme) private var theme
    private let lang = BabyScaleSetupStrings.Done.self

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundStyle(theme.textHeading)

                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundStyle(theme.textBody)
                }

                GifView(gifName: AppAssets.checkmarkSuccessGif, width: 160, height: 160)
                    .frame(width: 160, height: 160)
                    .frame(maxWidth: .infinity)
                    .accessibilityHidden(true)

                VStack(spacing: .spacingMD) {
                    ButtonView(
                        text: lang.goToDashboard,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: false
                    ) {
                        store.performExitCleanup()
                    }

                    ButtonView(
                        text: lang.addADevice,
                        type: .outlinedPrimary,
                        size: .large,
                        isDisabled: false
                    ) {
                        tabViewModel.pendingSettingsNavigation = .addEditScales
                        tabViewModel.selectedTab = .settings
                        store.performExitCleanup()
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .padding(.top, .spacingLG)
            .padding(.horizontal, .spacingSM)
        }
    }
}
