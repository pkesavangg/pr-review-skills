//
//  BloodPressureEntryView.swift
//  meApp
//

import SwiftUI

struct BloodPressureEntryView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var entryStore: EntryStore
    @Binding var focusedField: FocusField?

    private let bpLang = ManualEntryStrings.self
    private let labels = InputFieldLabels.self
    private let commonLang = CommonStrings.self

    var body: some View {
        VStack(spacing: .spacingLG) {
            VStack(alignment: .leading, spacing: 0) {
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
                    focusedField: $focusedField
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
                    focusedField: $focusedField
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
                    focusedField: $focusedField
                ) {
                    focusedField = .notes
                }

                // Notes
                notesField
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
                    TimeLabelView(
                        time: entryStore.bpForm.time.value,
                        isSelected: entryStore.showTimePicker
                    ) {
                        toggleTimePicker()
                    }
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
                    entryStore.notificationService.showToast(
                        ToastModel(title: ToastStrings.success, message: ToastStrings.entryAdded)
                    )
                    entryStore.resetBPForm()
                }
            }
        }
    }

    // MARK: - Helpers

    /// Returns error if present, otherwise warning — displayed in the input's built-in message slot.
    private func bpMessage<T>(for control: FormControl<T>) -> String? {
        entryStore.getBPError(for: control) ?? entryStore.getBPWarning(for: control)
    }

    private var notesField: some View {
        ZStack(alignment: .topLeading) {
            TextEditor(text: $entryStore.bpForm.notes.value)
                .font(.body1)
                .foregroundColor(theme.textBody)
                .scrollContentBackground(.hidden)
                .padding(.horizontal, CGFloat.spacingXS)
                .padding(.vertical, CGFloat.spacingXS)
                .frame(minHeight: 100)
                .background(theme.backgroundPrimary)
                .cornerRadius(BorderRadius.sm)
                .onTapGesture {
                    focusedField = .notes
                }

            if entryStore.bpForm.notes.value.isEmpty {
                Text(bpLang.notes)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.horizontal, CGFloat.spacingXS + 4)
                    .padding(.vertical, CGFloat.spacingXS + 8)
                    .allowsHitTesting(false)
            }
        }
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
