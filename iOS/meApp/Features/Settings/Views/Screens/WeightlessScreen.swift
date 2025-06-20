// iOS/meApp/Features/Settings/Views/Screens/WeightlessScreen.swift
// Screen presented as modal for configuring Weightless mode.

import SwiftUI
import Combine

struct WeightlessScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.dismiss) private var dismiss
    
    private let strings = WeightlessStrings.self
    private let toast = ToastStrings.self
    private let commonLang = CommonStrings.self
    private let inputLabels = InputFieldLabels.self
    // Local helpers
    private var weightUnit: WeightUnit {
        settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb
    }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: strings.title,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        // Disable when no changes or invalid.
                        isDisabled: (!settingsStore.weightlessForm.isDirty || (settingsStore.weightlessForm.isDirty && (settingsStore.weightlessForm.isOn.value ? settingsStore.weightlessForm.isInvalid : false))),
                    ) {
                        settingsStore.saveWeightless(dismiss: dismiss)
                        withAnimation { hideKeyboard() }
                    }
                },
                onLeadingTap: { settingsStore.handleWeightlessExit(dismiss: dismiss) },
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
                        CustomToggleView(isOn: $settingsStore.weightlessForm.isOn.value, text: "\(strings.modeLabel): \(settingsStore.weightlessForm.isOn.value ? commonLang.on : commonLang.off)")
                    }
                    .padding(.vertical, .spacingSM)
                    
                    // Weight input field
                    MetricInputField(
                        config: TextInputConfig(
                            label: inputLabels.weightLessLabel(weightUnit == .kg),
                            inputType: .metric,
                            errorMessage: settingsStore.weightlessForm.getWeightError(for: settingsStore.weightlessForm.weight,  unit: weightUnit),
                            isDisabled: !settingsStore.weightlessForm.isOn.value,
                            maxLength: 4,
                            maxValue: 999.9
                        ),
                        value: $settingsStore.weightlessForm.weight.value,
                        focusedField: .constant(nil)
                    )
                    .disabled(!settingsStore.weightlessForm.isOn.value)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingMD)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear { settingsStore.populateWeightlessFormIfNeeded() }
        .navigationBarHidden(true)
    }
}

#Preview {
    WeightlessScreen()
        .environmentObject(SettingsStore())
        .environmentObject(Router<SettingsRoute>())
}
