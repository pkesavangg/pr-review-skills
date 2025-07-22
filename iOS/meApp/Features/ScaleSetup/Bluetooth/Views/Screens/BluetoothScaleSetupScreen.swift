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
            
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews) { index in
                    setupStore.steps[index] != .selectUser
                }
            footerButtons
                .padding(.spacingSM)
        }
        .onAppear {
            setupStore.dismissAction = dismiss
            setupStore.configure(with: sku)
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
