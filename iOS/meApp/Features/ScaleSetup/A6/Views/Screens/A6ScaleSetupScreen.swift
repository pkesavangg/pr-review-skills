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
    
    // Track if view is being dismissed to prevent onDisappear from being called during presentation
    @State private var isBeingDismissed = false
    
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
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
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
            // Reset dismissal flag when view appears
            isBeingDismissed = false
            
            setupStore.dismissAction = {
                isBeingDismissed = true
                dismiss()
            }
            setupStore.configure(with: sku,
                                 discoveredScale: discoveredScale,
                                 discoveryEvent: discoveryEvent)
        }
        .onDisappear {
            // Only perform cleanup if the view is actually being dismissed, not just presented
            if isBeingDismissed {
                setupStore.cleanUp()
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        HStack {
            if setupStore.currentStep == .completeProfile {
                completeProfileFooter
            } else {
                ButtonView(text: commonLang.back,
                           type: .inlineTextPrimary,
                           size: .small,
                           isDisabled: setupStore.currentStep == .intro || setupStore.currentStep == .setupFinished) {
                    withAnimation {
                        hideKeyboard()
                    }
                    setupStore.moveToPreviousStep()
                }

                Spacer()

                ButtonView(text: setupStore.currentStep == .setupFinished ? commonLang.finish : commonLang.next,
                           type: .filledPrimary,
                           size: .small,
                           isDisabled: !setupStore.isNextEnabled) {
                    withAnimation {
                        hideKeyboard()
                    }
                    setupStore.moveToNextStep()
                }
            }
        }
    }

    /// Complete Profile Setup: Back / Skip / Next (MOB-1388).
    @ViewBuilder private var completeProfileFooter: some View {
        ButtonView(text: commonLang.back,
                   type: .inlineTextPrimary,
                   size: .small,
                   isDisabled: false,
                   useFrameForInlineText: true) {
            withAnimation {
                hideKeyboard()
            }
            setupStore.moveToPreviousStep()
        }
        Spacer()
        ButtonView(text: commonLang.skip, type: .inlineTextTertiary, size: .small, isDisabled: false) {
            withAnimation {
                hideKeyboard()
            }
            setupStore.handleCompleteProfileSkip()
        }
        .appAccessibility(id: AccessibilityID.scaleSetupProfileSkipButton)
        Spacer()
        ButtonView(text: commonLang.next,
                   type: .filledPrimary,
                   size: .small,
                   isDisabled: !setupStore.isNextEnabled) {
            withAnimation {
                hideKeyboard()
            }
            setupStore.handleCompleteProfileNext()
        }
        .appAccessibility(id: AccessibilityID.scaleSetupProfileNextButton)
    }
}

#Preview {
    A6ScaleSetupScreen(sku: "0378", discoveredScale: nil, discoveryEvent: nil)
        .environmentObject(Theme.shared)
}
