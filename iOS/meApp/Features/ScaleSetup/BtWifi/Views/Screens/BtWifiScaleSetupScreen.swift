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
    @Environment(\.dismiss) private var dismiss
    
    // MARK: - Input
    let sku: String
    let discoveredScale: Device?
    let discoveryEvent: DeviceDiscoveryEvent?
    let commonLang = CommonStrings.self
    
    private let scaleSetupLang = ScaleSetupStrings.self
    
    // Custom init so callers can omit optional params.
    init(sku: String, discoveredScale: Device? = nil, discoveryEvent: DeviceDiscoveryEvent? = nil) {
        self.sku = sku
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
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
                canShowPresentationIndicator: true
            )
            
            // Step views with swiper
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            )
            
            // Footer Buttons - hide for specific steps if needed
            if setupStore.shouldShowFooter() {
                footerButtons
                    .padding(.spacingSM)
            }
        }
        .onAppear {
            setupStore.dismissAction = dismiss
            setupStore.configure(with: sku,
                                 discoveredScale: discoveredScale,
                                 discoveryEvent: discoveryEvent)
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: commonLang.back,
                       type: .inlineTextPrimary,
                       size: .small,
                       isDisabled: setupStore.shouldDisableBackButton(),
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToPreviousStep()
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

#Preview {
    BtWifiScaleSetupScreen(sku: "0412", discoveredScale: nil, discoveryEvent: nil)
        .environmentObject(Theme.shared)
}
