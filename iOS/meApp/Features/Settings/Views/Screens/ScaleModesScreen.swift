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
    let scale: Device
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
                            isDisabled: !scaleStore.hasModeChanges,
                            action: {
                                scaleStore.handleScaleModeSave()
                            }
                        ))
                    }
                },
                onLeadingTap: { router.navigateBack() },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScaleModesSelectionView(
                selectedMode: scaleStore.modeValue,
                isHeartRateEnabled: scaleStore.isHeartRateEnabled,
                isR4ScaleSetup: isR4ScaleSetup,
                onBIAButtonTap: {
                    scaleStore.openBIAModel()
                },
                onValueChanged: { scaleMode, heartRateEnabled in
                    scaleStore.modeValue = scaleMode
                    scaleStore.isHeartRateEnabled = heartRateEnabled
                }
            )
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .onAppear {
            scaleStore.onAppear(scale: scale)
        }
        .onChange(of: scaleStore.modeValue) {
            scaleStore.updateModeChangeTracking()
        }
    }


}

#Preview {
    ScaleModesScreen(scale: Device(
        id: "preview-scale-id",
        accountId: "preview-account",
        sku: "0412",
        deviceName: "Preview Scale",
        deviceType: "scale"       
    ))
        .environmentObject(Theme.shared)
        .environmentObject(Router<SettingsRoute>())
}
