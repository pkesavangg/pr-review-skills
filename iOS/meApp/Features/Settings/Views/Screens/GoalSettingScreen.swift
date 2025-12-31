// iOS/meApp/Features/Settings/Views/Screens/GoalSettingScreen.swift
// Sheet-style screen for creating/updating a Goal (maintain / lose / gain).
// Follows the same interaction/UX pattern as `WeightlessScreen`.

import SwiftUI
import Combine

struct GoalSettingScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var settingsStore: SettingsStore
    @EnvironmentObject private var router: Router<SettingsRoute>
    @Environment(\.registerTabDeactivationHandler) private var registerDeactivation
    
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
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                trailingContent: {
                    ButtonView(
                        text: commonLang.save,
                        type: .inlineTextPrimary,
                        size: .small,
                        isDisabled: !settingsStore.isGoalFormValid(focusedField: focusedField),
                    ) {
                        hideKeyboard()
                        settingsStore.saveGoal(router: router)
                    }
                },
                onLeadingTap: { settingsStore.handleGoalExit(router: router) },
                onTrailingTap: {},
                canShowBorder: true
            )
            
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: .spacingMD) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        if settingsStore.activeAccount?.goalSettings?.goalType != nil {
                            VStack(alignment: .leading, spacing: .spacingMD) {
                                GoalProgressView()
                                Text(strings.updateGoalHeading)
                                    .fontOpenSans(.heading4)
                                    .foregroundColor(theme.textHeading)
                            }
                        }
                        Text(strings.description)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    SegmentedButtonView(
                        segments: GoalTypeSegment.allCases,
                        selectedSegment: $settingsStore.selectedSegment
                    )
                    .onChange(of: settingsStore.selectedSegment) {
                        settingsStore.handleGoalTypeChange(settingsStore.selectedSegment)
                    }
                    .padding(.horizontal, .spacing2XL)
                    
                    VStack(spacing: 4) {
                        // Current Weight Input (hidden for maintain)
                        if settingsStore.selectedSegment == .loseGain {
                            MetricInputField(
                                config: TextInputConfig(
                                    label: labels.startingWeightLabel(weightUnit == .kg),
                                    placeholder: "0.0",
                                    inputType: .metric,
                                    errorMessage: settingsStore.goalForm.getError(for: settingsStore.goalForm.currentWeight, isMetric: weightUnit == .kg),
                                    focusField: .currentWeight,
                                    maxLength: 4,
                                    maxValue: 999.9
                                ),
                                value: $settingsStore.goalForm.currentWeight.value,
                                focusedField: $focusedField,
                                onCommit: { focusedField = .goalWeight }
                            )
                        }
                        
                        // Goal Weight Input
                        MetricInputField(
                            config: TextInputConfig(
                                label: labels.goalWeightLabel(weightUnit == .kg),
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
                }
                .padding(.horizontal, .spacingSM)
                .padding(.top, .spacingLG)
            }
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .onAppear {
            settingsStore.populateGoalFormIfNeeded()
            // Sync the selected segment with the current goal type value
            let currentSegment = GoalTypeSegment.fromGoalType(settingsStore.goalForm.goalType.value)
            settingsStore.selectedSegment = currentSegment
            
            registerDeactivation {
                // Allow immediate tab switch when no changes.
                if !settingsStore.goalForm.isDirty {
                    router.navigateBack()
                    return true
                }
                
                let confirmed = await settingsStore.confirmDiscardGoalChanges()
                if confirmed {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1) {
                        router.navigateBack()
                        settingsStore.resetGoalForm()
                    }
                }
                return confirmed
            }
        }
        .onDisappear {
            registerDeactivation { true }
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
        .environmentObject(Router<SettingsRoute>())
}
