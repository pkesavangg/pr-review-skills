//
//  BabyProfileFormView.swift
//  meApp
//

import SwiftUI

/// "Complete Baby Profile" — form for name, birthday, sex, birth length/weight.
/// Decoupled from any specific store so it can be reused in Scale Setup, Signup, and My Kids.
struct BabyProfileFormView: View {
    @ObservedObject var form: BabyProfileSetupForm
    @Binding var showDatePicker: Bool
    @Binding var showSexPicker: Bool
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    private let lang = BabyScaleSetupStrings.BabyProfile.self
    private let labels = InputFieldLabels.self

    /// When `true`, the title and subtitle header is hidden (e.g. Settings -> Add Baby).
    var hideHeader: Bool = false
    /// Custom title text. Defaults to scale setup strings if nil.
    var headerTitle: String?
    /// Custom subtitle text. Defaults to scale setup strings if nil.
    var headerSubtitle: String?

    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    /// Display text for the biological sex picker.
    private var sexDisplayText: String {
        let val = form.biologicalSex.value
        return val.isEmpty ? "" : val.capitalized
    }

    /// The currently selected `Sex` value derived from the form string, defaulting to `.male`.
    private var selectedSex: Sex {
        Sex(rawInput: form.biologicalSex.value) ?? .male
    }

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: .spacingSM)
                // Header (scale setup / signup)
                if !hideHeader {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(headerTitle ?? lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                        Text(headerSubtitle ?? lang.subtitle)
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
                            errorMessage: form.getNameError(),
                            focusField: .babyName
                        ),
                        value: $form.name.value,
                        focusedField: focusBinding,
                        onCommit: {
                            form.name.markAsTouched()
                            form.name.validate()
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            if !isEditing {
                                form.name.markAsTouched()
                                form.name.validate()
                            }
                        }
                    )

                    // Baby's Birthday
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        Text(lang.birthdayLabel)
                            .fontOpenSans(.body3)
                            .foregroundColor(theme.textSubheading)

                        DateLabelView(
                            date: form.birthday.value,
                            isSelected: showDatePicker
                        ) {
                            withAnimation { showDatePicker.toggle() }
                        }

                        DatePickerView(
                            isPresented: $showDatePicker,
                            date: $form.birthday.value,
                            endDate: Date()
                        )
                    }

                    // Biological Sex
                    ActionListItemView(config: ActionListItemConfig(
                        title: labels.biologicalSex,
                        value: sexDisplayText,
                        chevronType: .upDown) { showSexPicker = true })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.spacingXS)

                    // Birth Length
                    MetricInputField(
                        config: TextInputConfig(
                            label: labels.babyBirthLength,
                            inputType: .metric,
                            errorMessage: form.getBirthLengthError(),
                            focusField: .babyBirthLength,
                            maxLength: 3,
                            clearZeroValue: true
                        ),
                        value: $form.birthLengthInches.value,
                        focusedField: focusBinding
                    ) {
                        form.birthLengthInches.markAsTouched()
                        form.birthLengthInches.validate()
                        focusedField = .babyBirthWeight
                    }

                    // Birth Weight (lb + oz) — single compound field
                    BirthWeightInputField(
                        lbsValue: $form.birthWeightLbs.value,
                        ozValue: $form.birthWeightOz.value,
                        focusedField: focusBinding,
                        label: lang.birthWeightLabel,
                        errorMessage: form.getBirthWeightError()
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
            isPresented: $showSexPicker,
            selectedValues: [selectedSex],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: labels.biologicalSex,
            onUpdate: { vals in // swiftlint:disable:this trailing_closure
                if let sex = vals.first {
                    form.biologicalSex.value = sex.rawValue.capitalized
                    form.biologicalSex.markAsTouched()
                    form.biologicalSex.validate()
                }
            }
        )
    }
}
