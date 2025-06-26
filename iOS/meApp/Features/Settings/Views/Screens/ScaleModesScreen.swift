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
    @ObservedObject var scaleStore = ScaleStore()
    var isR4ScaleSetup: Bool = false
    let lang = ScaleModesStrings.self

    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: isR4ScaleSetup ? lang.r4scaleSetupTitle : lang.modeTitle,
                leadingContent: { Image(AppAssets.chevronLeft) },
                trailingContent: {
                    if isR4ScaleSetup {
                        AnyView(Button(action: {
                            scaleStore.handleHelp()
                        },label: { Image(AppAssets.helpCircle) }))
                    } else {
                        AnyView(ButtonView(
                            text: CommonStrings.save.uppercased(),
                            type: .inlineTextPrimary,
                            size: .small,
                            isDisabled: false,
                            action: {
                                scaleStore.handleSave()
                            }
                        ))
                    }
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            VStack(alignment: .leading, spacing: .spacingLG) {
                descriptionWithBIAButton

                SegmentedButtonView(
                    segments: ScaleModes.allCases,
                    selectedSegment: $scaleStore.modeValue
                )

                Group {
                    if scaleStore.modeValue == .allBodyMetrics {
                        AllBodyMetricsView()
                    } else if scaleStore.modeValue == .weightOnly {
                        WeightOnlyView()
                    }
                }

                Spacer()
            }
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
    }

    // MARK: - Description with Inline Button
    private var descriptionWithBIAButton: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            if isR4ScaleSetup {
                Text(lang.changeScaleModeTitle)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
            }

            InlineButtonText(
                prefix: lang.biaExplanationPrefix,
                linkText: lang.biaButtonText,
                suffix: lang.biaExplanationSuffix
            ) {
                scaleStore.openBIAModel()
            }
        }
        .padding(.top, .spacingMD)
    }
}

#Preview {
    ScaleModesScreen()
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
}
