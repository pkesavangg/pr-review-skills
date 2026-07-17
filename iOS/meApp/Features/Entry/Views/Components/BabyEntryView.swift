//
//  BabyEntryView.swift
//  meApp
//

import SwiftUI

struct BabyEntryView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var entryStore: EntryStore
    @Binding var focusedField: FocusField?
    var onSaveCompleted: (() -> Void)?

    private let babyLang = ManualEntryStrings.self
    private let labels = InputFieldLabels.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                // Weight input — switches based on selected unit
                VStack(alignment: .leading, spacing: 0) {
                    switch entryStore.babyWeightUnit {
                    case .kg:
                        MetricInputField(
                            config: TextInputConfig(
                                label: babyLang.kg,
                                inputType: .metric,
                                errorMessage: entryStore.babyWeightError,
                                focusField: .babyKg,
                                maxLength: 6,
                                clearZeroValue: true,
                                decimalPlaces: 3
                            ),
                            value: $entryStore.babyForm.kg.value,
                            focusedField: $focusedField,
                            accessibilityIdentifier: AccessibilityID.babyWeightField
                        ) {
                            focusedField = .inches
                        }
                    case .lb:
                        MetricInputField(
                            config: TextInputConfig(
                                label: babyLang.lb,
                                inputType: .metric,
                                errorMessage: entryStore.babyWeightError,
                                focusField: .babyLb,
                                maxLength: 6,
                                clearZeroValue: true,
                                decimalPlaces: 3
                            ),
                            value: $entryStore.babyForm.lb.value,
                            focusedField: $focusedField,
                            accessibilityIdentifier: AccessibilityID.babyWeightField
                        ) {
                            focusedField = .inches
                        }
                    case .lbsOz:
                        HStack(spacing: .spacingSM) {
                            MetricInputField(
                                config: TextInputConfig(
                                    label: babyLang.pounds,
                                    inputType: .metric,
                                    // The combined pounds+ounces weight error renders in this field's
                                    // built-in error slot (like the length field), so it scales with
                                    // Dynamic Type instead of relying on a fixed negative offset.
                                    errorMessage: entryStore.babyWeightError,
                                    focusField: .pounds,
                                    maxLength: 3,
                                    allowWholeNumbers: true
                                ),
                                value: $entryStore.babyForm.pounds.value,
                                focusedField: $focusedField,
                                accessibilityIdentifier: AccessibilityID.babyWeightField
                            ) {
                                focusedField = .ounces
                            }

                            MetricInputField(
                                config: TextInputConfig(
                                    label: babyLang.ounces,
                                    inputType: .metric,
                                    focusField: .ounces,
                                    // Cents-style auto-decimal entry (same as the length field) so
                                    // typing "4" renders "0.4" and "159" renders "15.9". maxLength is
                                    // 3 digits → caps input at "99.9"; the ounces validator (≤ 15.9)
                                    // rejects anything above 15.9.
                                    maxLength: 3,
                                    clearZeroValue: true,
                                    decimalPlaces: 1
                                ),
                                value: $entryStore.babyForm.ounces.value,
                                focusedField: $focusedField
                            ) {
                                focusedField = .inches
                            }
                        }
                    }
                }

                // Length input — switches based on selected unit
                switch entryStore.babyLengthUnit {
                case .inches:
                    MetricInputField(
                        config: TextInputConfig(
                            label: babyLang.inches,
                            inputType: .metric,
                            errorMessage: entryStore.babyLengthError,
                            focusField: .inches,
                            maxLength: 3,
                            clearZeroValue: true
                        ),
                        value: $entryStore.babyForm.inches.value,
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.babyLengthField
                    ) {
                        focusedField = .notes
                    }
                case .cm:
                    MetricInputField(
                        config: TextInputConfig(
                            label: babyLang.cm,
                            inputType: .metric,
                            errorMessage: entryStore.babyLengthError,
                            focusField: .babyCm,
                            maxLength: 5,
                            clearZeroValue: true
                        ),
                        value: $entryStore.babyForm.cm.value,
                        focusedField: $focusedField,
                        accessibilityIdentifier: AccessibilityID.babyLengthField
                    ) {
                        focusedField = .notes
                    }
                }

                // Notes
                AppInputField(
                    config: TextInputConfig(
                        label: babyLang.notes,
                        inputType: .notes,
                        focusField: .notes
                    ),
                    value: $entryStore.babyForm.notes.value,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.babyNotesField
                )
                .padding(.top, .spacingXS)

                // Date heading
                Text(labels.date)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .padding(.top, .spacingSM)

                HStack(spacing: .spacingSM) {
                    DateLabelView(
                        date: entryStore.babyForm.date.value,
                        isSelected: entryStore.showDatePicker
                    ) {
                        toggleDatePicker()
                    }
                    .accessibilityHint(babyLang.accDateHint)
                    .appAccessibility(id: AccessibilityID.manualEntryDateButton)
                    TimeLabelView(
                        time: entryStore.babyForm.time.value,
                        isSelected: entryStore.showTimePicker
                    ) {
                        toggleTimePicker()
                    }
                    .accessibilityHint(babyLang.accTimeHint)
                    .appAccessibility(id: AccessibilityID.manualEntryTimeButton)
                }
                .padding(.top, .spacingXS)

                DatePickerView(
                    isPresented: $entryStore.showDatePicker,
                    date: $entryStore.babyForm.date.value,
                    // Dates before the baby's birthday are disabled so an entry can't
                    // be dated before the baby was born (MOB-1567).
                    startDate: entryStore.babyEntryMinimumDate,
                    endDate: Date()
                )
                .onChange(of: entryStore.showDatePicker) { _, isPresented in
                    if isPresented {
                        dismissKeyboardAndUnfocus()
                        if entryStore.showTimePicker { entryStore.showTimePicker = false }
                    }
                }

                TimePickerView(
                    isPresented: $entryStore.showTimePicker,
                    time: $entryStore.babyForm.time.value,
                    selectedDate: entryStore.babyForm.date.value,
                    endTime: entryStore.maxSelectableTime
                )
                .onChange(of: entryStore.showTimePicker) { _, isPresented in
                    if isPresented {
                        dismissKeyboardAndUnfocus()
                        if entryStore.showDatePicker { entryStore.showDatePicker = false }
                    }
                }
            }

            // Save button
            ButtonView(
                text: commonLang.save,
                type: .filledPrimary,
                size: .large,
                isDisabled: !entryStore.isBabyFormValid || entryStore.isSaving
            ) {
                Task {
                    focusedField = nil
                    await entryStore.saveBabyEntry()
                    onSaveCompleted?()
                }
            }
            .appAccessibility(id: AccessibilityID.babySaveButton)
        }
    }

    // MARK: - Helpers

    private func dismissKeyboardAndUnfocus() {
        focusedField = nil
        hideKeyboard()
    }

    private func toggleDatePicker() {
        dismissKeyboardAndUnfocus()
        withAnimation {
            entryStore.showDatePicker.toggle()
        }
        if entryStore.showTimePicker { entryStore.showTimePicker = false }
    }

    private func toggleTimePicker() {
        dismissKeyboardAndUnfocus()
        withAnimation {
            entryStore.showTimePicker.toggle()
        }
        if entryStore.showDatePicker { entryStore.showDatePicker = false }
    }
}
