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
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: commonLang.back,
                       type: .inlineTextPrimary,
                       size: .small,
                       isDisabled: setupStore.isBackDisabled,
                       action: {
                withAnimation { setupStore.moveToPreviousStep() }
            })
            
            Spacer()
            
            ButtonView(text: setupStore.currentStep == .setupFinished ? commonLang.finish : commonLang.next,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       action: {
                withAnimation { setupStore.moveToNextStep() }
            })
        }
    }
}

#Preview {
    BluetoothScaleSetupScreen(sku: "0375")
        .environmentObject(Theme.shared)
}
