//
//  BloodPressureEntryView.swift
//  meApp
//

import SwiftUI

struct BloodPressureEntryView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var entryStore: EntryStore
    @Binding var focusedField: FocusField?
    var onSaveCompleted: (() -> Void)?

    private let bpLang = ManualEntryStrings.self
    private let labels = InputFieldLabels.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                // Systolic
                MetricInputField(
                    config: TextInputConfig(
                        label: bpLang.systolic,
                        inputType: .metric,
                        errorMessage: bpMessage(for: entryStore.bpForm.systolic),
                        focusField: .systolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $entryStore.bpForm.systolic.value,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.bpSystolicField
                ) {
                    focusedField = .diastolic
                }

                // Diastolic
                MetricInputField(
                    config: TextInputConfig(
                        label: bpLang.diastolic,
                        inputType: .metric,
                        errorMessage: bpMessage(for: entryStore.bpForm.diastolic),
                        focusField: .diastolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $entryStore.bpForm.diastolic.value,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.bpDiastolicField
                ) {
                    focusedField = .pulse
                }

                // Pulse
                MetricInputField(
                    config: TextInputConfig(
                        label: bpLang.pulse,
                        inputType: .metric,
                        errorMessage: bpMessage(for: entryStore.bpForm.pulse),
                        focusField: .pulse,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $entryStore.bpForm.pulse.value,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.bpPulseField
                ) {
                    focusedField = .notes
                }

                // Notes
                AppInputField(
                    config: TextInputConfig(
                        label: bpLang.notes,
                        inputType: .notes,
                        focusField: .notes
                    ),
                    value: $entryStore.bpForm.notes.value,
                    focusedField: $focusedField,
                    accessibilityIdentifier: AccessibilityID.bpNotesField
                )
                .padding(.top, .spacingXS)

                // Date
                Text(labels.date)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .padding(.top, .spacingSM)

                HStack(spacing: .spacingSM) {
                    DateLabelView(
                        date: entryStore.bpForm.date.value,
                        isSelected: entryStore.showDatePicker
                    ) {
                        toggleDatePicker()
                    }
                    .accessibilityHint(bpLang.accDateHint)
                    .appAccessibility(id: AccessibilityID.manualEntryDateButton)
                    TimeLabelView(
                        time: entryStore.bpForm.time.value,
                        isSelected: entryStore.showTimePicker
                    ) {
                        toggleTimePicker()
                    }
                    .accessibilityHint(bpLang.accTimeHint)
                    .appAccessibility(id: AccessibilityID.manualEntryTimeButton)
                }
                .padding(.top, .spacingXS)

                DatePickerView(
                    isPresented: $entryStore.showDatePicker,
                    date: $entryStore.bpForm.date.value,
                    startDate: Date(timeIntervalSince1970: 946684800),
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
                    time: $entryStore.bpForm.time.value,
                    selectedDate: entryStore.bpForm.date.value,
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
                isDisabled: !entryStore.bpForm.isValid || entryStore.isSaving
            ) {
                Task {
                    focusedField = nil
                    await entryStore.saveBPEntry()
                    onSaveCompleted?()
                }
            }
            .appAccessibility(id: AccessibilityID.bpSaveButton)
        }
    }

    // MARK: - Helpers

    /// Returns error if present, otherwise warning — displayed in the input's built-in message slot.
    private func bpMessage<T>(for control: FormControl<T>) -> String? {
        entryStore.getBPError(for: control) ?? entryStore.getBPWarning(for: control)
    }

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
