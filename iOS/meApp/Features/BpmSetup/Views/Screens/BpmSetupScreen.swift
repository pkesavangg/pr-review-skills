///
///  BpmSetupScreen.swift
///  meApp
///

import SwiftUI

/// Multi-step setup flow for BPM (Blood Pressure Monitor) devices.
struct BpmSetupScreen: View {
    // MARK: - State & Environment
    @StateObject private var setupStore = BpmSetupStore()
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss

    @State private var isBeingDismissed = false

    // MARK: - Input
    let sku: String

    private let commonLang = CommonStrings.self

    init(sku: String) {
        self.sku = sku
    }

    private var stepViews: [AnyView] { setupStore.stepViews }

    /// Steps where the footer should show "FINISH" instead of "NEXT".
    private var finishSteps: Set<BpmSetupStep> {
        [.paired, .complete]
    }

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: BpmSetupStrings.setupHeader(sku),
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

            SwiperView(
                selectedIndex: $setupStore.currentStepIndex,
                views: stepViews
            ) { index in
                ![.nickname, .selectUser].contains(setupStore.steps[index])
            }

            footerButtons
                .padding(.spacingSM)
        }
        .onAppear {
            isBeingDismissed = false
            setupStore.dismissAction = {
                isBeingDismissed = true
                dismiss()
            }
            setupStore.configure(with: sku)
        }
        .onDisappear {
            if isBeingDismissed {
                setupStore.cleanUp()
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
    }

    private var footerButtons: some View {
        let isFinish = finishSteps.contains(setupStore.currentStep)
        let nextButtonText = isFinish ? commonLang.finish : commonLang.next

        return HStack {
            ButtonView(
                text: commonLang.back,
                type: .inlineTextPrimary,
                size: .small,
                isDisabled: setupStore.isBackDisabled,
                useFrameForInlineText: true
            ) {
                withAnimation { hideKeyboard() }
                setupStore.moveToPreviousStep()
            }

            Spacer()

            ButtonView(
                text: nextButtonText,
                type: .filledPrimary,
                size: .small,
                isDisabled: !setupStore.isNextEnabled,
                customHorizontalPadding: nextButtonText == commonLang.next ? .spacingXS / 2 : .spacingXS,
                customVerticalPadding: .spacingXS / 4
            ) {
                withAnimation { hideKeyboard() }
                setupStore.moveToNextStep()
            }
        }
    }
}

#Preview {
    BpmSetupScreen(sku: "0603")
        .environmentObject(Theme.shared)
}
