//
//  A6ScaleSetupScreen.swift
//  meApp
//
//  Created by Cursor AI on 08/07/25.
//

import SwiftUI

/// Multi-step setup flow for A6 / LCBT scales.
struct A6ScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = A6ScaleSetupStore()
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
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 24, height: 24))
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
            
            // Currently only the intro step is implemented; other steps will render placeholders.
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            )
            
            if !(setupStore.currentStep == .wakeUp || setupStore.currentStep == .connectingBluetooth) {
                // Footer Buttons
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
                       isDisabled: setupStore.currentStep == .intro || setupStore.currentStep == .setupFinished,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToPreviousStep()
                }
            })
            
            Spacer()
            
            ButtonView(text: setupStore.currentStep == .setupFinished ? commonLang.finish : commonLang.next,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToNextStep()
                }
            })
        }
    }
}

#Preview {
    A6ScaleSetupScreen(sku: "0378", discoveredScale: nil, discoveryEvent: nil)
        .environmentObject(Theme.shared)
}
