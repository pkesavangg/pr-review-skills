import SwiftUI

/// Multi-step setup flow for WiFi scales.
struct WifiScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = WifiScaleSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    
    // Track if view is being dismissed to prevent onDisappear from being called during presentation
    @State private var isBeingDismissed = false
    
    // MARK: - Input
    let sku: String
    let commonLang = CommonStrings.self
    
    private let scaleSetupLang = ScaleSetupStrings.self
    
    init(sku: String) {
        self.sku = sku
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
            
            // Step views with swiper
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews,
                shouldApplyHorizontalPadding: { index in
                    let step = setupStore.steps[index]
                    return step != .errorSelect && step != .selectUser && step != .apMode
                }
            )
            
            // Footer Buttons
            footerButtons
                .padding(.spacingSM)
        }
        .environmentObject(setupStore)
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
                setupStore.resetSkipCheckNetwork()
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
                       isDisabled: setupStore.shouldDisableBackButton(),
                       useFrameForInlineText: true,
                       action: {
                withAnimation {
                    hideKeyboard()
                }
                setupStore.handleBackButtonClick()
            })
            Spacer()
            ButtonView(text: setupStore.nextButtonText,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       customHorizontalPadding: .spacingXS / 2,
                       customVerticalPadding: .spacingXS / 4,
                       action: {
                withAnimation {
                    hideKeyboard()
                }
                setupStore.handleNextButtonClick()
            })
        }
        .overlay {
            HStack {
                if setupStore.currentStep == .permissions && !setupStore.isForGetMac {
                    ButtonView(text: commonLang.skip, type: .inlineTextTertiary, size: .
                               large, isDisabled: false, action: {
                        withAnimation {
                            hideKeyboard()
                        }
                        setupStore.handleSkipWifiStep()
                    })
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }
}

#Preview {
    WifiScaleSetupScreen(sku: "0385")
        .environmentObject(Theme.shared)
}
