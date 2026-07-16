//
//  BabyScaleSetupScreen.swift
//  meApp
//

import SwiftUI

/// Multi-step setup flow for Baby scales (0220/0222).
struct BabyScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = BabyScaleSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    @Environment(\.dismiss) private var dismiss

    @State private var isBeingDismissed = false

    // MARK: - Input
    let sku: String
    let discoveredScale: Device?
    let discoveryEvent: DeviceDiscoveryEvent?
    private let lang = BabyScaleSetupStrings.self
    private let commonLang = CommonStrings.self

    init(sku: String,
         discoveredScale: Device? = nil,
         discoveryEvent: DeviceDiscoveryEvent? = nil) {
        self.sku = sku
        self.discoveredScale = discoveredScale
        self.discoveryEvent = discoveryEvent
    }

    private var stepViews: [AnyView] { setupStore.stepViews }

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // MARK: - Header
                NavbarHeaderView(
                    title: lang.setupHeader(sku),
                    leadingContent: {
                        AppIconView(
                            icon: AppAssets.xmarkSmall,
                            size: IconSize(width: 24, height: 24)
                        )
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

                // MARK: - Step Views
                SwiperView(
                    selectedIndex: $setupStore.currentStepIndex,
                    views: stepViews
                )

                // MARK: - Footer
                if setupStore.shouldShowFooter() {
                    footerButtons
                        .padding(.spacingSM)
                }
            }
            .background(theme.backgroundSecondary)

            // MARK: - Skip Dialog Overlay
            skipDialogOverlay
        }
        .animation(.easeInOut, value: setupStore.showSkipDialog)
        .animation(.easeInOut, value: setupStore.showSkipEditDialog)
        .environmentObject(setupStore)
        .onAppear {
            isBeingDismissed = false
            registerDeactivation {
                await setupStore.confirmExit()
            }
            setupStore.dismissAction = {
                isBeingDismissed = true
                dismiss()
            }
            setupStore.configure(with: sku,
                                 discoveredScale: discoveredScale,
                                 discoveryEvent: discoveryEvent)
        }
        .navigationBarBackButtonHidden(true)
        .onDisappear {
            guard !isBeingDismissed else {
                registerDeactivation { true }
                setupStore.cleanup()
                return
            }
            setupStore.cleanup()
        }
        .screenAccessibilityRoot(AccessibilityID.babyScaleSetupScreenRoot)
    }

    /// Steps where the footer should show "FINISH" instead of "NEXT".
    private var finishSteps: Set<BabyScaleSetupStep> {
        [.babyAdded, .done]
    }

    // MARK: - Footer Buttons
    private var footerButtons: some View {
        let isFinish = finishSteps.contains(setupStore.currentStep)
        let nextButtonText = isFinish ? commonLang.finish : setupStore.nextButtonText

        return HStack {
            ButtonView(
                text: commonLang.back,
                type: .inlineTextPrimary,
                size: .small,
                isDisabled: setupStore.isBackButtonDisabled(),
                useFrameForInlineText: true
            ) {
                withAnimation { hideKeyboard() }
                setupStore.handleBackButtonClick()
            }
            .appAccessibility(id: AccessibilityID.scaleSetupBackButton)

            Spacer()

            if setupStore.currentStep == .babyProfile {
                ButtonView(
                    text: commonLang.skip,
                    type: .inlineTextTertiary,
                    size: .small,
                    isDisabled: false
                ) {
                    withAnimation { hideKeyboard() }
                    setupStore.showSkipBabyProfileDialog()
                }
                .appAccessibility(id: AccessibilityID.scaleSetupSkipButton)

                Spacer()
            }

            ButtonView(
                text: nextButtonText,
                type: .filledPrimary,
                size: .small,
                isDisabled: !setupStore.isNextEnabled,
                customHorizontalPadding: nextButtonText == commonLang.next ? .spacingXS / 2 : .spacingXS,
                customVerticalPadding: .spacingXS / 4
            ) {
                withAnimation { hideKeyboard() }
                setupStore.handleNextButtonClick()
            }
            .appAccessibility(id: AccessibilityID.scaleSetupNextButton)
        }
    }

    // MARK: - Skip Dialog Overlay

    @ViewBuilder
    private var skipDialogOverlay: some View {
        if setupStore.showSkipDialog {
            skipOverlay(
                title: lang.SkipDialog.title,
                message: lang.SkipDialog.message,
                onConfirm: { setupStore.handleSkipConfirmed() },
                onCancel: { setupStore.handleSkipCancelled() }
            )
        } else if setupStore.showSkipEditDialog {
            skipOverlay(
                title: lang.SkipEditDialog.title,
                message: lang.SkipEditDialog.message,
                onConfirm: { setupStore.handleSkipEditConfirmed() },
                onCancel: { setupStore.handleSkipEditCancelled() }
            )
        }
    }

    private func skipOverlay(
        title: String,
        message: String,
        onConfirm: @escaping () -> Void,
        onCancel: @escaping () -> Void
    ) -> some View {
        ZStack {
            theme.supportOverlay
                .ignoresSafeArea()

            VStack(spacing: .spacingMD) {
                Text(title)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                    .multilineTextAlignment(.center)

                Text(message)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textBody)
                    .multilineTextAlignment(.center)

                ButtonView(
                    text: lang.SkipDialog.yesSkip,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false
                ) { onConfirm() }
                .appAccessibility(id: AccessibilityID.babySkipDialogConfirmButton)

                ButtonView(
                    text: lang.SkipDialog.goBack,
                    type: .inlineTextPrimary,
                    size: .large,
                    isDisabled: false
                ) { onCancel() }
                .appAccessibility(id: AccessibilityID.babySkipDialogCancelButton)
            }
            .padding(.spacingMD)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusMD)
            .padding(.horizontal, .spacingMD)
            .transition(.scale.combined(with: .opacity))
        }
    }
}
