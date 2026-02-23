//
//  AppSyncScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 01/07/25.
//

import SwiftUI

struct AppSyncSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore: AppSyncSetupStore = .init()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) var dismiss
    
    // Track if view is being dismissed to prevent onDisappear from being called during presentation
    @State private var isBeingDismissed = false
    
    // MARK: - Input
    let sku: String
    var commonLang = CommonStrings.self
    let scaleSetupLang = ScaleSetupStrings.self
    
    // Directly retrieve the pre-built views from the store.
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
                onLeadingTap: {
                    setupStore.handleExit()
                },
                onTrailingTap: {},
                canShowPresentationIndicator: true
            )
            
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            ) { index in
                    // Apply padding for all steps except the AppSync scanner step
                    setupStore.steps[index] != .appSync
                }
            // Footer Buttons
            if setupStore.currentStep != .appSync {
                footerButtons
                    .padding(.horizontal, .spacingSM)
            }
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
                       isDisabled: setupStore.currentStep == .intro || setupStore.currentStep == .finish,
                       useFrameForInlineText: true) {
                withAnimation {
                    hideKeyboard()
                }
                setupStore.moveToPreviousStep()
            }
            
            Spacer()
            
            ButtonView(text: setupStore.currentStepIndex == setupStore.steps.count - 1 ? commonLang.finish : commonLang.next,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       customHorizontalPadding: .spacingXS / 2,
                       customVerticalPadding: .spacingXS / 4) {
                withAnimation {
                    hideKeyboard()
                }
                setupStore.moveToNextStep()
            }
        }
    }
}

#Preview {
    AppSyncSetupScreen(sku: "0343") // Body-comp scale
}

#Preview {
    AppSyncSetupScreen(sku: "0342") // Non-body-comp scale
}
