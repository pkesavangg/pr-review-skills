//
//  ScaleModesScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

/// A screen that allows users to configure scale modes and settings.
/// Supports both regular scale mode configuration and R4 scale setup workflows.
struct ScaleModesScreen: View {
    // MARK: - Environment Objects
    @EnvironmentObject var router: Router<SettingsRoute>
    @Environment(\.appTheme) private var theme
    
    // MARK: - Observed Objects
    @ObservedObject var scaleStore = ScaleStore()
    
    // MARK: - Properties
    let scale: Device
    let isR4ScaleSetup: Bool
    private let lang = ScaleModesStrings.self

    // MARK: - Initializer
    init(scale: Device, isR4ScaleSetup: Bool = false) {
        self.scale = scale
        self.isR4ScaleSetup = isR4ScaleSetup
    }

    // MARK: - Body
    var body: some View {
        VStack(alignment: .center, spacing: 0) {
            NavbarHeaderView(
                title: isR4ScaleSetup ? lang.r4scaleSetupTitle : lang.modeTitle,
                leadingContent: { 
                    Image(AppAssets.chevronLeft)
                        .accessibilityLabel("Back")
                },
                trailingContent: {
                    Group {
                        if isR4ScaleSetup {
                            Button(action: {
                                scaleStore.handleHelp()
                            }) {
                                Image(AppAssets.helpCircle)
                                    .accessibilityLabel("Help")
                            }
                        } else {
                            ButtonView(
                                text: CommonStrings.save.uppercased(),
                                type: .inlineTextPrimary,
                                size: .small,
                                isDisabled: !scaleStore.hasModeChanges,
                                action: {
                                    scaleStore.handleScaleModeSave()
                                }
                            )
                            .accessibilityLabel("Save scale mode preferences")
                        }
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
            Task {
                await scaleStore.loadScale(scale)
            }
        }

    }
}

// MARK: - Preview
#Preview("Scale Modes Screen - Light") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "Preview Scale",
            deviceType: "scale"       
        )
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}

#Preview("Scale Modes Screen - Dark") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "Preview Scale",
            deviceType: "scale"       
        )
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
    .preferredColorScheme(.dark)
}

#Preview("R4 Scale Setup - Light") {
    ScaleModesScreen(
        scale: Device(
            id: "preview-r4-scale-id",
            accountId: "preview-account",
            sku: "0412",
            deviceName: "R4 Scale Setup",
            deviceType: "scale"       
        ),
        isR4ScaleSetup: true
    )
    .environmentObject(Theme.shared)
    .environmentObject(Router<SettingsRoute>())
}
