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
    let isReconnect: Bool // indicates if this is a reconnect flow
    let isDuplicated: Bool // indicates if this is handling a duplicate user error
    let commonLang = CommonStrings.self
    let isWifiSetupOnly: Bool
    private let scaleSetupLang = ScaleSetupStrings.self
    
    // Custom init so callers can omit optional params.
    init(sku: String, 
         discoveredScale: Device? = nil, 
         discoveryEvent: DeviceDiscoveryEvent? = nil, 
         savedScale: Device? = nil,
         isReconnect: Bool = false,
         isDuplicated: Bool = false,
         isWifiSetupOnly: Bool = false
    ) {
        self.sku = sku
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
        self.savedScale = savedScale
        self.isReconnect = isReconnect
        self.isDuplicated = isDuplicated
        self.isWifiSetupOnly = isWifiSetupOnly
    }
    
    private var stepViews: [AnyView] { setupStore.stepViews }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: isWifiSetupOnly ? BtWifiScaleSetupStrings.WifiScreenStrings.title : scaleSetupLang.setupHeader(sku),
                leadingContent: {
                    AppIconView(
                        icon: isWifiSetupOnly ? AppAssets.chevronLeft : AppAssets.xmarkSmall,
                        size: IconSize(width: 24, height: 24)
                    )
                    .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    !isWifiSetupOnly
                    ? AnyView(Button {
                        setupStore.showHelpModal()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    })
                    : AnyView(EmptyView())
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
                                 saveScale: savedScale,
                                 isReconnect: isReconnect,
                                 isDuplicated: isDuplicated, isWifiSetupOnly: isWifiSetupOnly)
            
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
            // Hide Back/Next buttons when used for settings WiFi configuration
            if savedScale != nil {
                // Settings WiFi configuration - no footer buttons
                EmptyView()
            } else if setupStore.currentStep == .availableWifiList {
                // Show Skip only when no network is already connected.
                if setupStore.scaleSetupError == .none && setupStore.connectedWifiNetwork == nil {
                    Spacer()
                    ButtonView(text: commonLang.skip, type: .inlineTextTertiary, size: .large, isDisabled: false, action: {
                        setupStore.handleSkipWifiStep()
                    })
                    Spacer()
                } else {
                    // Otherwise, show the standard Back/Next buttons.
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
    BtWifiScaleSetupScreen(sku: SettingsConstants.defaultR4Sku, discoveredScale: nil, discoveryEvent: nil, isReconnect: false, isDuplicated: false)
        .environmentObject(Theme.shared)
}
