//
//  CompleteProfileSetupView.swift
//  meApp
//
//  Complete Profile Setup step (MOB-1388). Shown during BtWifi (R4/0412) scale setup only
//  when the account is missing the biological sex / height the weight product requires.
//  Pre-filled from the account (or defaults); Next saves to the profile, Skip proceeds
//  without saving. Footer buttons live in `BtWifiScaleSetupScreen`.
//

import SwiftUI

struct CompleteProfileSetupView: View {
    @EnvironmentObject var store: BtWifiScaleSetupStore
    @Environment(\.appTheme) private var theme
    @State private var focusedField: FocusField?

    private let lang = BtWifiScaleSetupStrings.CompleteProfileStrings.self
    private let identityDisplay: (String) -> String = { $0 }
    private let capitalizedSexDisplay: (Sex) -> String = { $0.rawValue.capitalized }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: .spacingMD) {
                header
                genderRow
                heightRow
                goalSection
            }
            .padding(.top, .spacingLG)
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively)
        .pickerSheet(
            isPresented: $store.showProfileGenderPicker,
            selectedValues: [store.profileGender],
            options: [Sex.allCases],
            displayValue: capitalizedSexDisplay,
            title: lang.biologicalSex
        ) { vals in
            if let sex = vals.first {
                store.updateProfileGender(sex)
            }
        }
        .pickerSheet(
            isPresented: $store.showProfileHeightInchesPicker,
            selectedValues: store.selectedProfileHeightInches,
            options: ConversionTools.heightInchesOptions,
            displayValue: identityDisplay,
            pickerType: .heightInches,
            title: lang.height
        ) { newValues in
            store.updateProfileHeight(fromMetric: false, values: newValues)
        }
        .pickerSheet(
            isPresented: $store.showProfileHeightCmPicker,
            selectedValues: store.selectedProfileHeightCm,
            options: ConversionTools.heightCmOptions,
            displayValue: identityDisplay,
            pickerType: .heightCm,
            title: lang.height
        ) { newValues in
            store.updateProfileHeight(fromMetric: true, values: newValues)
        }
    }

    // MARK: - Sections

    private var header: some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(lang.title)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)
            Text(lang.subtitle)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
        }
    }

    private var genderRow: some View {
        ActionListItemView(config: ActionListItemConfig(
            title: lang.biologicalSex,
            value: store.profileGenderText,
            chevronType: .upDown) { store.showProfileGenderPicker = true })
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingXS / 2)
            .background(theme.backgroundPrimary)
            .cornerRadius(8)
            .appAccessibility(id: AccessibilityID.scaleSetupProfileGenderRow)
    }

    private var heightRow: some View {
        ActionListItemView(config: ActionListItemConfig(
            title: lang.height,
            value: store.profileHeightText,
            chevronType: .upDown) { store.presentProfileHeightPicker() })
            .padding(.horizontal, .spacingSM)
            .padding(.vertical, .spacingXS / 2)
            .background(theme.backgroundPrimary)
            .cornerRadius(8)
            .appAccessibility(id: AccessibilityID.scaleSetupProfileHeightRow)
    }

    private var goalSection: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            HStack(spacing: .spacingXS) {
                Text(lang.setAGoal)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                Text(lang.optional)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textSubheading)
            }

            SegmentedButtonView(
                segments: GoalTypeSegment.allCases,
                selectedSegment: $store.profileGoalSegment,
                accessibilityIdentifierProvider: { segment in
                    segment == .maintain
                        ? AccessibilityID.scaleSetupProfileGoalMaintainTab
                        : AccessibilityID.scaleSetupProfileGoalLoseGainTab
                }
            )

            VStack(spacing: 4) {
                if store.profileGoalSegment == .loseGain {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.startingWeight,
                            placeholder: "0.0",
                            inputType: .metric,
                            focusField: .currentWeight,
                            maxLength: 4,
                            maxValue: 999.9,
                            trailingLabel: store.profileWeightUnitLabel
                        ),
                        value: $store.profileCurrentWeight,
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.scaleSetupProfileStartingWeightField,
                        onCommit: { focusedField = .goalWeight }
                    )
                }

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.goalWeight,
                        placeholder: "0.0",
                        inputType: .metric,
                        focusField: .goalWeight,
                        maxLength: 4,
                        maxValue: 999.9,
                        trailingLabel: store.profileWeightUnitLabel
                    ),
                    value: $store.profileGoalWeight,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.scaleSetupProfileGoalWeightField,
                    onCommit: { focusedField = nil }
                )
            }
        }
        .padding(.top, .spacingSM)
    }
}
