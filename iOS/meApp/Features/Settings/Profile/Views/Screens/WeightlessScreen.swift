// iOS/meApp/Features/Settings/Views/Screens/WeightlessScreen.swift
// Screen presented as modal for configuring Weightless mode.

import Combine
import SwiftUI

struct WeightlessScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var router: Router<SettingsRoute>
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    @State private var focusedField: FocusField?
    
    private let strings = WeightlessStrings.self
    private let toast = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let inputLabels = InputFieldLabels.self
    // Local helpers
    private var weightUnit: WeightUnit {
        settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb
    }
    
    var body: some View {
        // Ensure form is synced with account state before rendering (only when pristine)
        _ = {
            // Sync form with account state - this will only populate if form is not dirty
            settingsStore.populateWeightlessFormIfNeeded()
        }()
        
        return VStack(spacing: 0) {
            NavbarHeaderView(
                title: strings.title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        // Disable when no changes or invalid.
                        isDisabled: !settingsStore.isWeightLessFormValid,
                    ) {
                        settingsStore.saveWeightless(router: router)
                        withAnimation { hideKeyboard() }
                    }
                },
                onLeadingTap: { settingsStore.handleWeightlessExit(router: router) },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            ScrollView {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(strings.subtitle)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    Text(strings.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    
                    // Toggle
                    HStack {
// swiftlint:disable:next line_length
                        CustomToggleView(isOn: $settingsStore.weightlessForm.isOn.value, text: "\(strings.modeLabel): \(settingsStore.weightlessForm.isOn.value ? commonLang.on : commonLang.off)")
                    }
                    .padding(.vertical, .spacingSM)
                    
                    // Weight input field
                    MetricInputField(
                        config: TextInputConfig(
                            label: inputLabels.weightLessLabel(weightUnit == .kg),
                            inputType: .metric,
                            errorMessage: settingsStore.weightlessForm.getWeightError(for: settingsStore.weightlessForm.weight, unit: weightUnit),
                            isDisabled: !settingsStore.weightlessForm.isOn.value,
                            focusField: .weight,
                            maxLength: 4,
                            maxValue: 999.9
                        ),
                        value: $settingsStore.weightlessForm.weight.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                    }
                    .disabled(!settingsStore.weightlessForm.isOn.value)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingMD)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onTapGesture {
            focusedField = nil
            hideKeyboard()
        }
        .onAppear {
            // Populate form synchronously immediately when screen appears
            settingsStore.populateWeightlessFormIfNeeded()

            registerDeactivation {
                // If there are no actual changes, allow immediate tab switch.
                if !settingsStore.hasWeightlessChanges {
                    router.navigateBack()
                    return true
                }

                // Otherwise ask for confirmation via SettingsStore.
                let confirmed = await settingsStore.confirmDiscardWeightlessChanges()
                if confirmed {
                    Task { @MainActor in
                        try? await Task.sleep(nanoseconds: 1_000_000_000)
                        router.navigateBack()
                        settingsStore.resetWeightlessForm()
                    }
                }
                return confirmed
            }
        }
        .onDisappear {
            // Remove deactivation handler when leaving the screen.
            registerDeactivation { true }
        }
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button(commonLang.done) {
                    withAnimation {
                        focusedField = nil
                    }
                }
            }
        }
        .navigationBarHidden(true)
    }
}

#Preview {
    WeightlessScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
}
