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
    private let babyWeightSegments: [BabyWeightUnit] = [.kg, .lb, .lbsOz]

    /// When `true`, the title and subtitle header is hidden (e.g. Settings -> Add Baby).
    var hideHeader: Bool = false
    /// When `true`, the weight unit selector and note are hidden (e.g. Settings → Add Baby).
    var hideUnitToggle: Bool = false
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

    /// Trailing label for the birth length field based on derived length unit.
    private var lengthTrailingLabel: String {
        form.derivedLengthUnit == .cm ? "(\(lang.cmUnit))" : "(\(lang.inUnit))"
    }

    /// Trailing label for the birth weight field based on selected weight unit.
    private var weightTrailingLabel: String {
        switch form.selectedWeightUnit {
        case .kg: return "(\(lang.kgUnit))"
        case .lb: return "(\(lang.lbsUnit))"
        case .lbsOz: return ""
        }
    }

    private var birthWeightFocusField: FocusField? {
        switch form.selectedWeightUnit {
        case .kg:
            return .babyKg
        case .lb:
            return .babyLb
        case .lbsOz:
            return .babyBirthWeight
        }
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
                            dismissKeyboardAndUnfocus()
                            if showSexPicker { showSexPicker = false }
                            withAnimation { showDatePicker.toggle() }
                        }

                        DatePickerView(
                            isPresented: $showDatePicker,
                            date: $form.birthday.value,
                            endDate: Date()
                        )
                    }
                    .padding(.top, -.spacingSM)

                    // Biological Sex
                    ActionListItemView(config: ActionListItemConfig(
                        title: labels.biologicalSex,
                        value: sexDisplayText,
                        chevronType: .upDown) {
                            dismissKeyboardAndUnfocus()
                            if showDatePicker { showDatePicker = false }
                            showSexPicker = true
                        })
                        .padding(.horizontal, .spacingSM)
                        .padding(.vertical, .spacingXS / 2)
                        .background(theme.backgroundPrimary)
                        .cornerRadius(.spacingXS)

                    // Birth Length + Birth Weight — tightly grouped (weight unit drives length unit)
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        birthLengthField
                        birthWeightField
                    }
                    .padding(.top, .spacingXS)

                    // Unit selector + note
                    if !hideUnitToggle {
                        unitSelectorSection
                    }
                }
                .padding(.top, .spacingLG)

                Spacer()
            }
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively)
        .onChange(of: focusedField) { _, _ in
            showDatePicker = false
            showSexPicker = false
        }
        .pickerSheet(
            isPresented: $showSexPicker,
            selectedValues: [selectedSex],
            options: [Sex.allCases],
            displayValue: { $0.rawValue.capitalized },
            title: labels.biologicalSex
        ) { vals in
            if let sex = vals.first {
                form.biologicalSex.value = sex.rawValue.capitalized
                form.biologicalSex.markAsTouched()
                form.biologicalSex.validate()
            }
        }
    }

    private func dismissKeyboardAndUnfocus() {
        focusedField = nil
        hideKeyboard()
    }

    // MARK: - Birth Length Field

    @ViewBuilder
    private var birthLengthField: some View {
        switch form.derivedLengthUnit {
        case .inches:
            MetricInputField(
                config: TextInputConfig(
                    label: labels.babyBirthLength,
                    inputType: .metric,
                    errorMessage: form.getBirthLengthError(),
                    focusField: .babyBirthLength,
                    maxLength: 3,
                    clearZeroValue: true,
                    trailingLabel: lengthTrailingLabel
                ),
                value: $form.birthLengthInches.value,
                focusedField: focusBinding
            ) {
                form.birthLengthInches.markAsTouched()
                form.birthLengthInches.validate()
                focusedField = birthWeightFocusField
            }
        case .cm:
            MetricInputField(
                config: TextInputConfig(
                    label: labels.babyBirthLength,
                    inputType: .metric,
                    errorMessage: form.getBirthLengthError(),
                    focusField: .babyCm,
                    maxLength: 4,
                    clearZeroValue: true,
                    trailingLabel: lengthTrailingLabel
                ),
                value: $form.birthLengthCm.value,
                focusedField: focusBinding
            ) {
                form.birthLengthCm.markAsTouched()
                form.birthLengthCm.validate()
                focusedField = birthWeightFocusField
            }
        }
    }

    // MARK: - Birth Weight Field

    @ViewBuilder
    private var birthWeightField: some View {
        switch form.selectedWeightUnit {
        case .kg:
            MetricInputField(
                config: TextInputConfig(
                    label: labels.babyBirthWeight,
                    inputType: .metric,
                    errorMessage: form.getBirthWeightError(),
                    focusField: .babyKg,
                    maxLength: 4,
                    clearZeroValue: true,
                    trailingLabel: weightTrailingLabel
                ),
                value: $form.birthWeightKg.value,
                focusedField: focusBinding
            ) {
                form.birthWeightKg.markAsTouched()
                form.birthWeightKg.validate()
                focusedField = nil
            }
        case .lb:
            MetricInputField(
                config: TextInputConfig(
                    label: labels.babyBirthWeight,
                    inputType: .metric,
                    errorMessage: form.getBirthWeightError(),
                    focusField: .babyLb,
                    maxLength: 4,
                    clearZeroValue: true,
                    trailingLabel: weightTrailingLabel
                ),
                value: $form.birthWeightLbs.value,
                focusedField: focusBinding
            ) {
                form.birthWeightLbs.markAsTouched()
                form.birthWeightLbs.validate()
                focusedField = nil
            }
        case .lbsOz:
            BirthWeightInputField(
                lbsValue: $form.birthWeightLbs.value,
                ozValue: $form.birthWeightOz.value,
                focusedField: focusBinding,
                label: lang.birthWeightLabel,
                errorMessage: form.getBirthWeightError()
            )
        }
    }

    // MARK: - Unit Selector Section

    private var unitSelectorSection: some View {
        VStack(spacing: .spacingXS) {
            // Centered pill unit selector
            HStack {
                Spacer()
                BabyWeightUnitPicker(
                    segments: babyWeightSegments,
                    selectedSegment: $form.selectedWeightUnit
                )
                .frame(width: 225)
                Spacer()
            }

            // Note text
            Text(lang.unitNoteText)
                .fontOpenSans(.body4)
                .foregroundColor(theme.textSubheading)
                .multilineTextAlignment(.center)
                .frame(maxWidth: 288)
                .frame(maxWidth: .infinity)
        }
    }
}
