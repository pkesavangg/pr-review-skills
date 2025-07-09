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
    var commonLang = CommonStrings.self

    private let scaleSetupLang = ScaleSetupStrings.self

    private var stepViews: [AnyView] { setupStore.stepViews }

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: scaleSetupLang.setupHeader(sku),
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: { EmptyView() },
                onLeadingTap: { setupStore.handleExit() },
                onTrailingTap: {},
                canShowPresentationIndicator: true
            )

            // Currently only the intro step is implemented; other steps will render placeholders.
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            )
            // Footer Buttons
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
                       isDisabled: setupStore.currentStep == .intro || setupStore.currentStep == .finish,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.moveToPreviousStep()
                }
            })

            Spacer()

            ButtonView(text: setupStore.currentStep == .finish ? commonLang.finish : commonLang.next,
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
    A6ScaleSetupScreen(sku: "0378")
        .environmentObject(Theme.shared)
} 
