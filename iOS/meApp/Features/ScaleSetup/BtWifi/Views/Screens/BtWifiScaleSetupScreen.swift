//
//  BtWifiScaleSetupScreen.swift
//  meApp
//
//  Created by Cursor AI on 12/01/25.
//

import SwiftUI

/// Multi-step setup flow for BtWifi scales.
struct BtWifiScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = BtWifiScaleSetupStore()
    @Environment(\.appTheme) private var theme
    // Registers a de-activation handler so the BottomTabBarView can ask whether it is
    // safe to leave this screen when the user taps another tab.
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    @Environment(\.dismiss) private var dismiss
    
    // MARK: - Input
    let sku: String
    let discoveredScale: Device?
    let discoveryEvent: DeviceDiscoveryEvent?
    let savedScale: Device? // if the scale was previously saved in settings open the BtWifiScaleSetup for the wifi setup
    let commonLang = CommonStrings.self
    
    private let scaleSetupLang = ScaleSetupStrings.self
    
    // Custom init so callers can omit optional params.
    init(sku: String, discoveredScale: Device? = nil, discoveryEvent: DeviceDiscoveryEvent? = nil, savedScale: Device? = nil) {
        self.sku = sku
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        self.savedScale = savedScale
    }
    
    private var stepViews: [AnyView] { setupStore.stepViews }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: scaleSetupLang.setupHeader(sku),
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    Button {
                        setupStore.showHelpModal()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                },
                onLeadingTap: { setupStore.handleExit() },
                onTrailingTap: {},
                canShowPresentationIndicator: savedScale == nil, // Show back button only if not opening from saved scale
            )
            
            // Step views with swiper
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews,
                shouldApplyHorizontalPadding: { index in
                    // Omit horizontal padding ONLY for the Scale Metrics customization screen.
                    !(setupStore.steps[index] == .viewSettings &&
                      setupStore.currentCustomizeSetting == .scaleMetrics)
                }
            )
            
            // Footer Buttons - hide for specific steps if needed
            if setupStore.shouldShowFooter() {
                footerButtons
                    .padding(.spacingSM)
            }
        }
        .environmentObject(setupStore)
        .onAppear {
            // Register a tab de-activation handler so attempts to switch tabs while the
            // Wi-Fi setup flow is active will first show the exit confirmation alert.
            registerDeactivation {
                await setupStore.confirmExit()
            }
            setupStore.dismissAction = dismiss
            setupStore.configure(with: sku,
                                 discoveredScale: discoveredScale,
                                 discoveryEvent: discoveryEvent,
                                 saveScale: savedScale)
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
        // Clear the deactivation handler when the view disappears to avoid stale closures.
        .onDisappear {
            registerDeactivation { true }
        }
    }
    
    private var footerButtons: some View {
        HStack {
            if setupStore.currentStep == .availableWifiList {
                if setupStore.scaleSetupError == .none  {
                    Spacer()
                    ButtonView(text: commonLang.skip, type: .inlineTextTertiary, size: .large, isDisabled: false, action: {
                        setupStore.handleSkipWifiStep()
                    })
                    Spacer()
                }
            } else if setupStore.currentStep == .scaleConnected {
                // Show centered finish button for scaleConnected step
                Spacer()
                ButtonView(text: setupStore.nextButtonText,
                           type: .filledPrimary,
                           size: .small,
                           isDisabled: !setupStore.isNextEnabled,
                           action: {
                    withAnimation {
                        hideKeyboard()
                        setupStore.handleNextButtonClick()
                    }
                })
                Spacer()
            } else {
                ButtonView(text: commonLang.back,
                           type: .inlineTextPrimary,
                           size: .small,
                           isDisabled: setupStore.shouldDisableBackButton(),
                           action: {
                    withAnimation {
                        hideKeyboard()
                        setupStore.handleBackButtonClick()
                    }
                })
                Spacer()
                ButtonView(text: setupStore.nextButtonText,
                           type: .filledPrimary,
                           size: .small,
                           isDisabled: !setupStore.isNextEnabled,
                           action: {
                    withAnimation {
                        hideKeyboard()
                        setupStore.handleNextButtonClick()
                    }
                })
            }
        }
    }
}

#Preview {
    BtWifiScaleSetupScreen(sku: SettingsConstants.defaultR4Sku, discoveredScale: nil, discoveryEvent: nil)
        .environmentObject(Theme.shared)
}
