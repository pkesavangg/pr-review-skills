// iOS/meApp/Features/Settings/Views/Screens/WeightlessScreen.swift
// Screen presented as modal for configuring Weightless mode.

import SwiftUI
import Combine

struct WeightlessScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var router: Router<SettingsRoute>
    
    // Use the shared form from SettingsStore
    private var form: WeightlessForm { settingsStore.weightlessForm }
    
    private let strings = WeightlessStrings.self
    private let toast = ToastStrings.self
    private let commonLang = CommonStrings.self
    // Local helpers
    private var weightUnit: WeightUnit {
        settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb
    }
    
    private var weightPlaceholder: String {
        weightUnit == .kg ? strings.weightPlaceholderKg : strings.weightPlaceholderLbs
    }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: strings.title,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .linkBlueDefault,
                        size: .small,
                        // Disable when no changes or invalid.
                        isDisabled: (!form.isDirty || (form.isDirty && form.isInvalid))
                    ) {
                        withAnimation { hideKeyboard() }
                    }
                },
                onLeadingTap: { settingsStore.handleWeightlessExit(router: router) },
                onTrailingTap: { settingsStore.saveWeightless(router: router) },
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
                        CustomToggleView(isOn: $settingsStore.weightlessForm.isOn.value, text: "\(strings.modeLabel): \(settingsStore.weightlessForm.isOn.value ? CommonStrings.on : CommonStrings.off)")
                    }
                    .padding(.vertical, .spacingSM)
                    
                    // Weight input field
                    MetricInputField(
                        config: TextInputConfig(
                            label: weightPlaceholder,
                            inputType: .metric,
                            errorMessage: settingsStore.weightlessForm.getWeightError(unit: weightUnit),
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
