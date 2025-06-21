// iOS/meApp/Features/Settings/Views/Screens/GoalSettingScreen.swift
// Sheet-style screen for creating/updating a Goal (maintain / lose / gain).
// Follows the same interaction/UX pattern as `WeightlessScreen`.

import SwiftUI
import Combine

struct GoalSettingScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var settingsStore: SettingsStore
    @Environment(\.dismiss) private var dismiss

    @State private var focusedField: FocusField? = nil

    private let strings = GoalStrings.self
    private let commonLang = CommonStrings.self
    private let labels = InputFieldLabels.self

    // Helpers
    private var weightUnit: WeightUnit {
        settingsStore.activeAccount?.weightSettings?.weightUnit ?? .lb
    }

    var body: some View {
        VStack(spacing: 0) {
            // Header
            NavbarHeaderView(
                title: strings.title,
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !settingsStore.isGoalFormValid,
                    ) {
                        hideKeyboard()
                        settingsStore.saveGoal(dismiss: dismiss)
                    }
                },
                onLeadingTap: { settingsStore.handleGoalExit(dismiss: dismiss) },
                onTrailingTap: {},
                canShowBorder: true
            )

            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    // Current goal summary (if any)
                    currentGoalCard()
                        .padding(.bottom, .spacingSM)

                    Text(strings.description)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .padding(.bottom, .spacingMD)
                    Text(settingsStore.goalForm.goalType.value)
                        
                    SegmentedButtonView(
                        segments: GoalTypeSegment.allCases,
                        selectedSegment: $settingsStore.selectedSegment
                    )
                    .onChange(of: settingsStore.selectedSegment) {
                        settingsStore.goalForm.goalType.value = settingsStore.selectedSegment.goalTypeValue
                    }
                    .padding(.top, .spacingSM)

                    VStack(spacing: 4) {
                        // Current Weight Input (disabled for maintain)
                        MetricInputField(
                            config: TextInputConfig(
                                label: "\(labels.currentWeight) (\(weightUnit.rawValue))",
                                placeholder: "0.0",
                                inputType: .metric,
                                errorMessage: settingsStore.goalForm.getError(for: settingsStore.goalForm.currentWeight, isMetric: weightUnit == .kg),
                                isDisabled: settingsStore.goalForm.goalType.value == GoalType.maintain.rawValue,
                                focusField: .currentWeight,
                                maxLength: 4,
                                maxValue: 999.9
                            ),
                            value: $settingsStore.goalForm.currentWeight.value,
                            focusedField: $focusedField,
                            onCommit: { focusedField = .goalWeight }
                        )
                        .disabled(settingsStore.goalForm.goalType.value == GoalType.maintain.rawValue)

                        // Goal Weight Input
                        MetricInputField(
                            config: TextInputConfig(
                                label: "\(labels.goalWeight) (\(weightUnit.rawValue))",
                                placeholder: "0.0",
                                inputType: .metric,
                                errorMessage: settingsStore.goalForm.getError(for: settingsStore.goalForm.goalWeight, isMetric: weightUnit == .kg),
                                focusField: .goalWeight,
                                maxLength: 4,
                                maxValue: 999.9
                            ),
                            value: $settingsStore.goalForm.goalWeight.value,
                            focusedField: $focusedField,
                            onCommit: { focusedField = nil }
                        )
                    }
                    .padding(.top, .spacingMD)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingMD)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            settingsStore.populateGoalFormIfNeeded()
            settingsStore.selectedSegment = GoalTypeSegment.fromGoalType(settingsStore.goalForm.goalType.value)
        }
        .navigationBarHidden(true)
    }

    // MARK: - Current Goal Card
    @ViewBuilder
    private func currentGoalCard() -> some View {
        if let goalSettings = settingsStore.activeAccount?.goalSettings,
           let goalType = goalSettings.goalType {
            VStack(alignment: .leading, spacing: .spacingXS) {
                Text(strings.subtitle)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                Group {
                    HStack { Text(strings.goalTypeLabel).bold(); Spacer(); Text(goalType.rawValue.capitalized) }
                    if let initial = goalSettings.initialWeight {
                        HStack {
                            Text(strings.startingWeightLabel).bold(); Spacer();
                            Text(formatDisplayWeight(initial))
                        }
                    }
                    if let gWeight = goalSettings.goalWeight {
                        HStack {
                            Text(strings.goalWeightLabel).bold(); Spacer();
                            Text(formatDisplayWeight(gWeight))
                        }
                    }
                }
//                .fontOpenSans(.body3)
                .foregroundColor(theme.textBody)
            }
        }
    }

    private func formatDisplayWeight(_ stored: Double) -> String {
        let intVal = Int(stored)
        let display = weightUnit == .kg ? ConversionTools.convertStoredToKg(intVal) : ConversionTools.convertStoredToLbs(intVal)
        return String(format: "%.1f %@", display, weightUnit.rawValue)
    }
}

#Preview {
    GoalSettingScreen()
        .environmentObject(SettingsStore())
} 
