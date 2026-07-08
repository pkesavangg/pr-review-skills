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
    @Environment(\.appTheme) private var theme
    @FocusState private var focusedField: FocusField?
    private let lang = BabyScaleSetupStrings.BabyProfile.self
    private let labels = InputFieldLabels.self
    private let sexDisplay: (Sex) -> String = { $0.rawValue.capitalized }
    private let babyWeightSegments: [BabyWeightUnit] = [.lb, .lbsOz, .kg]

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
                        chevronType: .upDown) {
                            dismissKeyboardAndUnfocus()
                            if showDatePicker { showDatePicker = false }
                            presentSexPicker()
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
        }
        // Clear a stale "baby name already exists" 409 error as the user edits the
        // name. This view is shared by Signup, Scale Setup and My Kids; only Signup
        // re-homes this clear into its store, so the local clear is what keeps the
        // BabyScaleSetupStore-driven flows correct (the Signup store re-clears on the
        // same tick, so it stays correct there too).
        .onChange(of: form.name.value) { _, _ in
            form.duplicateNameError = nil
        }
    }

    private func dismissKeyboardAndUnfocus() {
        focusedField = nil
        hideKeyboard()
    }

    /// Presents the biological sex picker as a centered radio-button popup with
    /// CANCEL / SAVE (MOB-1224), replacing the previous wheel half-sheet.
    private func presentSexPicker() {
        let notifications = NotificationHelperService.shared
        notifications.showModal(ModalData(presentedView: AnyView(
            BiologicalSexPickerModalView(
                selected: selectedSex,
                displayValue: sexDisplay,
                onCancel: { notifications.dismissModal() },
                onSave: { sex in
                    // Store the API-expected lowercase raw value ("male"/"female").
                    // The UI capitalizes it for display via `sexDisplayText`; sending the
                    // capitalized form makes the server reject it ("Invalid value for sex").
                    form.biologicalSex.value = sex.rawValue
                    form.biologicalSex.markAsTouched()
                    form.biologicalSex.validate()
                    notifications.dismissModal()
                }
            )
        )))
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
            // Two side-by-side fields, each placeholder-left / unit-right, matching the
            // standard metric field styling used by the .lb/.kg cases (per design mock).
            // The shared birth-weight error is rendered under the lbs field so it reads as
            // one message beneath the row.
            HStack(alignment: .top, spacing: .spacingSM) {
                MetricInputField(
                    config: TextInputConfig(
                        label: labels.babyBirthWeight,
                        inputType: .metric,
                        errorMessage: form.getBirthWeightError(),
                        focusField: .babyBirthWeight,
                        maxLength: 3,
                        allowWholeNumbers: true,
                        trailingLabel: "(\(lang.lbsUnit))"
                    ),
                    value: $form.birthWeightLbs.value,
                    focusedField: focusBinding
                ) {
                    form.birthWeightLbs.markAsTouched()
                    form.birthWeightLbs.validate()
                    focusedField = .babyBirthWeightOz
                }
                .frame(maxWidth: .infinity)

                MetricInputField(
                    config: TextInputConfig(
                        label: labels.babyBirthWeight,
                        inputType: .metric,
                        focusField: .babyBirthWeightOz,
                        maxLength: 3,
                        clearZeroValue: true,
                        trailingLabel: "(\(lang.ozUnit))"
                    ),
                    value: $form.birthWeightOz.value,
                    focusedField: focusBinding
                ) {
                    form.birthWeightOz.markAsTouched()
                    form.birthWeightOz.validate()
                    focusedField = nil
                }
                .frame(maxWidth: .infinity)
            }
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

// MARK: - Biological Sex Picker

/// Radio-style Biological Sex dialog presented via `notificationService.showModal`.
///
/// Replaces the previous wheel half-sheet with a centered radio-button popup
/// (CANCEL / SAVE), matching the Android picker for cross-platform consistency (MOB-1224).
/// `onSave` returns the selected `Sex`; the caller is responsible for dismissing the modal.
struct BiologicalSexPickerModalView: View {
    @Environment(\.appTheme) private var theme

    let options: [Sex]
    let displayValue: (Sex) -> String
    let onCancel: () -> Void
    let onSave: (Sex) -> Void

    @State private var selection: Sex

    private let commonLang = CommonStrings.self
    private let labels = InputFieldLabels.self

    init(
        options: [Sex] = Sex.allCases,
        selected: Sex,
        displayValue: @escaping (Sex) -> String,
        onCancel: @escaping () -> Void,
        onSave: @escaping (Sex) -> Void
    ) {
        self.options = options
        self.displayValue = displayValue
        self.onCancel = onCancel
        self.onSave = onSave
        _selection = State(initialValue: selected)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            Text(labels.biologicalSex)
                .fontOpenSans(.heading4)
                .foregroundStyle(theme.textHeading)
                .accessibilityAddTraits(.isHeader)

            VStack(alignment: .leading, spacing: 0) {
                ForEach(options, id: \.self) { option in
                    radioRow(
                        title: displayValue(option),
                        isSelected: selection == option
                    ) { selection = option }
                }
            }

            actionButtons
        }
        .padding(.spacingMD)
        .background(theme.backgroundPrimary)
        .clipShape(.rect(cornerRadius: .radiusXL))
    }

    // MARK: - Components

    private func radioRow(title: String, isSelected: Bool, onTap: @escaping () -> Void) -> some View {
        Button(action: onTap) {
            HStack(spacing: .spacingSM) {
                ZStack {
                    Circle()
                        .strokeBorder(
                            isSelected ? theme.actionPrimary : theme.textBody.opacity(0.4),
                            lineWidth: 2
                        )
                        .frame(width: 22, height: 22)
                    if isSelected {
                        Circle()
                            .fill(theme.actionPrimary)
                            .frame(width: 12, height: 12)
                    }
                }
                Text(title)
                    .fontOpenSans(.body1)
                    .foregroundStyle(theme.textHeading)
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
            .padding(.vertical, .spacingSM)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(isSelected ? [.isButton, .isSelected] : .isButton)
    }

    private var actionButtons: some View {
        HStack(spacing: .spacingMD) {
            Spacer()
            ButtonView(
                text: commonLang.cancel,
                type: .inlineTextTertiary,
                size: .small,
                isDisabled: false,
                action: onCancel
            )
            ButtonView(
                text: commonLang.save,
                type: .inlineTextPrimary,
                size: .small,
                isDisabled: false
            ) {
                onSave(selection)
            }
        }
    }
}
