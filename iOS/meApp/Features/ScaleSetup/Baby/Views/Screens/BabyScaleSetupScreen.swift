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
                },
                onLeadingTap: { setupStore.handleExit() },
                onTrailingTap: {},
                canShowPresentationIndicator: true
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
            guard !isBeingDismissed else { return }
            setupStore.cleanup()
        }
    }

    // MARK: - Footer Buttons
    private var footerButtons: some View {
        HStack {
            if setupStore.shouldShowBackButton() {
                ButtonView(
                    text: lang.Buttons.back,
                    type: .inlineTextPrimary,
                    size: .small,
                    isDisabled: setupStore.isBackButtonDisabled(),
                    useFrameForInlineText: true
                ) {
                    setupStore.handleBackButtonClick()
                }
            }

            Spacer()

            ButtonView(
                text: setupStore.nextButtonText,
                type: .filledPrimary,
                size: .small,
                isDisabled: !setupStore.isNextEnabled
            ) {
                setupStore.handleNextButtonClick()
            }
        }
    }
}
