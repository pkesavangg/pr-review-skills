//
//  BabyProfileFormView.swift
//  meApp
//

import SwiftUI

/// "Complete Baby Profile" — form for name, birthday, sex, birth length/weight.
struct BabyProfileFormView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    private let lang = BabyScaleSetupStrings.BabyProfile.self

    private let sexOptions = [
        BabyScaleSetupStrings.BabyProfile.male,
        BabyScaleSetupStrings.BabyProfile.female
    ]

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    /// Display text for the biological sex picker.
    private var sexDisplayText: String {
        let val = store.babyProfileForm.biologicalSex.value
        return val.isEmpty ? lang.biologicalSexLabel : val
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingMD) {
                // Header
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)

                    Text(lang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }

                // Name
                AppInputField(
                    config: TextInputConfig(
                        label: lang.namePlaceholder,
                        placeholder: lang.namePlaceholder,
                        inputType: .text,
                        errorMessage: store.babyProfileForm.getNameError(),
                        focusField: .firstName
                    ),
                    value: $store.babyProfileForm.name.value,
                    focusedField: focusBinding
                )

                // Birthday
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.birthdayLabel)
                        .fontOpenSans(.body3)
                        .foregroundColor(theme.textBody)

                    DatePicker(
                        "",
                        selection: $store.babyProfileForm.birthday.value,
                        in: ...Date(),
                        displayedComponents: .date
                    )
                    .datePickerStyle(.compact)
                    .labelsHidden()
                }

                // Biological Sex
                Menu {
                    ForEach(sexOptions, id: \.self) { option in
                        Button(option) {
                            store.babyProfileForm.biologicalSex.value = option
                        }
                    }
                } label: {
                    HStack {
                        Text(sexDisplayText)
                            .fontOpenSans(.body1)
                            .foregroundColor(
                                store.babyProfileForm.biologicalSex.value.isEmpty
                                    ? theme.textBody.opacity(0.5)
                                    : theme.textBody
                            )
                        Spacer()
                        Image(systemName: "chevron.down")
                            .foregroundColor(theme.textBody)
                    }
                    .padding(.horizontal, .spacingSM)
                    .padding(.vertical, .spacingSM)
                    .background(theme.backgroundPrimary)
                    .cornerRadius(.radiusSM)
                }

                // Birth Length — inline: "Birth Length" label | value | "in"
                VStack(alignment: .leading, spacing: 0) {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.birthLengthLabel,
                            inputType: .metric,
                            focusField: .inches,
                            maxLength: 4,
                            maxValue: 99.9
                        ),
                        value: $store.babyProfileForm.birthLengthInches.value,
                        focusedField: focusBinding
                    ) {
                        focusedField = .pounds
                    }

                    if let error = store.babyProfileForm.getBirthLengthError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                            .padding(.top, 2)
                    }
                }

                // Birth Weight — inline: "Birth Weight" label | lbs | "lb" | oz | "oz"
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: .spacingSM) {
                        MetricInputField(
                            config: TextInputConfig(
                                label: ManualEntryStrings.pounds,
                                inputType: .metric,
                                focusField: .pounds,
                                maxLength: 3,
                                allowWholeNumbers: true
                            ),
                            value: $store.babyProfileForm.birthWeightLbs.value,
                            focusedField: focusBinding
                        ) {
                            focusedField = .ounces
                        }

                        MetricInputField(
                            config: TextInputConfig(
                                label: ManualEntryStrings.ounces,
                                inputType: .metric,
                                focusField: .ounces,
                                maxLength: 3,
                                clearZeroValue: true
                            ),
                            value: $store.babyProfileForm.birthWeightOz.value,
                            focusedField: focusBinding
                        )
                    }

                    if let error = store.babyProfileForm.getBirthWeightError() {
                        Text(error)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textError)
                            .padding(.leading, .spacingSM)
                            .padding(.top, 2)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
            .padding(.top, .spacingLG)
        }
        .scrollDismissesKeyboard(.interactively)
    }
}
