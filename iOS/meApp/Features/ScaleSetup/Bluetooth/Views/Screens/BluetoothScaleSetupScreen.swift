///
///  BluetoothScaleSetupScreen.swift
///  meApp
///
///  Created by Cursor AI on 18/07/25.
///

import SwiftUI

/// Multi-step setup flow for Bluetooth (A3) scales.
struct BluetoothScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = BluetoothScaleSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    
    // Track if view is being dismissed to prevent onDisappear from being called during presentation
    @State private var isBeingDismissed = false
    
    // MARK: - Input
    let sku: String
    
    private let commonLang = CommonStrings.self
    
    // Custom init so callers can omit optional params.
    init(sku: String) {
        self.sku = sku
    }
    
    private var stepViews: [AnyView] { setupStore.stepViews }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: ScaleSetupStrings.setupHeader(sku),
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
                    .appAccessibility(id: AccessibilityID.scaleSetupHelpButton)
                },
                onLeadingTap: { setupStore.handleExit() },
                onTrailingTap: {},
                canShowPresentationIndicator: true,
                leadingAccessibilityID: AccessibilityID.scaleSetupCloseButton
            )
            
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews) { index in
                    setupStore.steps[index] != .selectUser
                }
            footerButtons
                .padding(.spacingSM)
        }
        .onAppear {
            // Reset dismissal flag when view appears
            isBeingDismissed = false
            
            setupStore.dismissAction = {
                isBeingDismissed = true
                dismiss()
            }
            setupStore.configure(with: sku)
        }
        .onDisappear {
            // Only perform cleanup if the view is actually being dismissed, not just presented
            if isBeingDismissed {
                setupStore.cleanUp()
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
        .screenAccessibilityRoot(AccessibilityID.bluetoothScaleSetupScreenRoot)
    }

    private var footerButtons: some View {
        HStack {
            if setupStore.currentStep == .completeProfile {
                completeProfileFooter
            } else {
                ButtonView(text: commonLang.back,
                           type: .inlineTextPrimary,
                           size: .small,
                           isDisabled: setupStore.isBackDisabled) {
                    withAnimation {
                        hideKeyboard()
                    }
                    setupStore.moveToPreviousStep()
                }
                .appAccessibility(id: AccessibilityID.scaleSetupBackButton)

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
                .appAccessibility(id: AccessibilityID.scaleSetupNextButton)
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
        .appAccessibility(id: AccessibilityID.scaleSetupBackButton)
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
    BluetoothScaleSetupScreen(sku: "0375")
        .environmentObject(Theme.shared)
}
