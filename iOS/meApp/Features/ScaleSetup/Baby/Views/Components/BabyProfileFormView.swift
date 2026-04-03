//
//  BabyProfileFormView.swift
//  meApp
//

import SwiftUI

/// "Complete Baby Profile" — form for name, birthday, sex, birth length/weight.
/// Uses the same UI components as the signup flow's AddBabyStepView.
struct BabyProfileFormView: View {
    @EnvironmentObject var store: BabyScaleSetupStore
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    private let lang = BabyScaleSetupStrings.BabyProfile.self
    private let labels = InputFieldLabels.self

    /// When `true`, the title and subtitle header is hidden (e.g. Settings -> Add Baby).
    var hideHeader: Bool = false

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    /// Display text for the biological sex picker.
    private var sexDisplayText: String {
        let val = store.babyProfileForm.biologicalSex.value
        return val.isEmpty ? "" : val.capitalized
    }

    /// The currently selected `Sex` value derived from the form string, defaulting to `.male`.
    private var selectedSex: Sex {
        Sex(rawInput: store.babyProfileForm.biologicalSex.value) ?? .male
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: .spacingSM)
                // Header (scale setup only)
                if !hideHeader {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                        Text(lang.subtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textHeading)
                    }
                }

                // Form fields
                VStack(alignment: .leading, spacing: .spacingMD) {
                    // Baby Name
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.babyName,
                            inputType: .text,
                            errorMessage: store.babyProfileForm.getNameError(),
                            focusField: .babyName
                        ),
                        value: $store.babyProfileForm.name.value,
                        focusedField: focusBinding,
                        onCommit: {
                            store.babyProfileForm.name.markAsTouched()
                            store.babyProfileForm.name.validate()
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            if !isEditing {
                                store.babyProfileForm.name.markAsTouched()
                                store.babyProfileForm.name.validate()
                            }
                        }
                    )

                    // Baby's Birthday
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        Text(lang.birthdayLabel)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)

                        DateLabelView(
                            date: store.babyProfileForm.birthday.value,
                            isSelected: store.showBabyDatePicker
                        ) {
                            withAnimation { store.showBabyDatePicker.toggle() }
                        }

                        DatePickerView(
                            isPresented: $store.showBabyDatePicker,
                            date: $store.babyProfileForm.birthday.value,
                            endDate: Date()
                        )
                    }

                    // Biological Sex
                    ActionListItemView(config: ActionListItemConfig(
                        title: labels.biologicalSex,
                        value: sexDisplayText,
                        chevronType: .upDown) { store.showBabySexPicker = true })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.spacingXS)

                    // Birth Length
                    MetricInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthLength,
                            inputType: .metric,
                            errorMessage: store.babyProfileForm.getBirthLengthError(),
                            focusField: .babyBirthLength,
                            maxLength: 3,
                            clearZeroValue: true
                        ),
                        value: $store.babyProfileForm.birthLengthInches.value,
                        focusedField: focusBinding
                    ) {
                        store.babyProfileForm.birthLengthInches.markAsTouched()
                        store.babyProfileForm.birthLengthInches.validate()
                        focusedField = .babyBirthWeight
                    }
                    .padding(.top, .spacingSM)

                    // Birth Weight (lb + oz) — single compound field
                    BirthWeightInputField(
                        lbsValue: $store.babyProfileForm.birthWeightLbs.value,
                        ozValue: $store.babyProfileForm.birthWeightOz.value,
                        focusedField: focusBinding,
                        label: lang.birthWeightLabel,
                        errorMessage: store.babyProfileForm.getBirthWeightError()
                    )
                }
                .padding(.top, .spacingLG)

                Spacer()
            }
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively)
        .onTapGesture {
            hideKeyboard()
        }
        .pickerSheet(
            isPresented: $store.showBabySexPicker,
            selectedValues: [selectedSex],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: labels.biologicalSex
        ) { vals in
            if let sex = vals.first {
                store.babyProfileForm.biologicalSex.value = sex.rawValue.capitalized
                store.babyProfileForm.biologicalSex.markAsTouched()
                store.babyProfileForm.biologicalSex.validate()
            }
        }
    }
}
