import SwiftUI

/// Multi-step setup flow for WiFi scales.
struct WifiScaleSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = WifiScaleSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    
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
            
            // Step views with swiper
            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews,
                shouldApplyHorizontalPadding: { _ in true }
            )
            
            // Footer Buttons
            footerButtons
                .padding(.spacingSM)
        }
        .environmentObject(setupStore)
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
                       isDisabled: setupStore.shouldDisableBackButton(),
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.handleBackButtonClick()
                }
            })
            Spacer()
            ButtonView(text: setupStore.nextButtonText,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !setupStore.isNextEnabled,
                       action: {
                withAnimation {
                    hideKeyboard()
                    setupStore.handleNextButtonClick()
                }
            })
        }
        .overlay {
            HStack {
                if setupStore.currentStep == .permissions {
                    ButtonView(text: commonLang.skip, type: .inlineTextTertiary, size: .
                               large, isDisabled: false, action: {
                        setupStore.handleSkipWifiStep()
                    })
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
        }
    }
}

#Preview {
    WifiScaleSetupScreen(sku: "0376")
        .environmentObject(Theme.shared)
}
