//
//  BabyHistoryEditSheet.swift
//  meApp
//

import SwiftUI

/// Bottom-sheet form for editing a baby history entry.
/// Allows editing weight, length, date, and notes. Saves via delete-old + create-new.
///
/// Validation is driven by the shared `BabyEntryForm` (the same form used by manual
/// baby entry) — not by ad-hoc inline rules and not by the BP sheet's validation.
/// Only the visual styling follows `BPHistoryEditSheet`.
struct BabyHistoryEditSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var historyStore: HistoryStore

    let entry: BabyHistoryEntry

    /// Shared baby entry form — owns the field values (incl. date/time), validators, and error strings.
    @StateObject private var form: BabyEntryForm

    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var isSaving = false
    @State private var focusedField: FocusField?

    private let labels = InputFieldLabels.self
    private let lang = HistoryListStrings.self

    init(entry: BabyHistoryEntry) {
        self.entry = entry
        let parsed = DateTimeTools.parse(entry.entryTimestamp) ?? Date()

        // Seed the shared baby form from the entry. Zero weight/length seed as empty
        // (matching the manual entry form's clear-zero behaviour) so the numeric
        // validators don't flag a legitimate "0 oz" / absent-length value.
        let babyForm = BabyEntryForm()
        babyForm.kg.value = entry.weightKg > 0 ? String(format: "%.3f", entry.weightKg) : ""
        babyForm.pounds.value = entry.weightLbs > 0 ? "\(entry.weightLbs)" : ""
        babyForm.ounces.value = entry.weightOz > 0 ? String(format: "%.1f", entry.weightOz) : ""
        babyForm.inches.value = entry.lengthInches > 0 ? String(format: "%.1f", entry.lengthInches) : ""
        babyForm.cm.value = entry.lengthCm > 0 ? String(format: "%.1f", entry.lengthCm) : ""
        babyForm.notes.value = entry.notes ?? ""
        babyForm.date.value = parsed
        babyForm.time.value = parsed
        // Seeded from a saved (valid) entry — start pristine so validation errors only
        // surface once the user actually edits a field.
        [babyForm.kg, babyForm.pounds, babyForm.ounces, babyForm.inches, babyForm.cm, babyForm.notes]
            .forEach { $0.markAsPristine() }

        _form = StateObject(wrappedValue: babyForm)
    }

    private var isMetric: Bool { historyStore.isMetric }

    /// Device-synced readings (baby scale) are note-only: the measured weight/length came from
    /// the device and stay read-only. Manually-entered readings are fully editable (MOB-1172).
    private var valuesLocked: Bool { !entry.isManual }

    // MARK: - Validation (driven by BabyEntryForm)

    /// Weight is required; length is optional. Range checks come from the form's validators.
    private var isValid: Bool {
        if isMetric {
            return !form.kg.value.isEmpty && form.kg.isValid && form.cm.isValid
        } else {
            let hasWeight = !form.pounds.value.isEmpty || !form.ounces.value.isEmpty
            return hasWeight && form.pounds.isValid && form.ounces.isValid && form.inches.isValid
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                closeButton

                if isMetric {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.kg,
                            inputType: .metric,
                            errorMessage: form.weightErrorMetric,
                            isDisabled: valuesLocked,
                            focusField: .weight,
                            allowWholeNumbers: false,
                            decimalPlaces: 3
                        ),
                        value: $form.kg.value,
                        focusedField: $focusedField
                    ) { focusedField = .inches }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.cm,
                            inputType: .metric,
                            errorMessage: form.lengthErrorCm,
                            isDisabled: valuesLocked,
                            focusField: .inches,
                            allowWholeNumbers: false
                        ),
                        value: $form.cm.value,
                        focusedField: $focusedField
                    ) { focusedField = .notes }
                } else {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.pounds,
                            inputType: .metric,
                            errorMessage: form.weightError,
                            isDisabled: valuesLocked,
                            focusField: .weight,
                            maxLength: 3,
                            allowWholeNumbers: true
                        ),
                        value: $form.pounds.value,
                        focusedField: $focusedField
                    ) { focusedField = .ounces }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.ounces,
                            inputType: .metric,
                            isDisabled: valuesLocked,
                            focusField: .ounces,
                            allowWholeNumbers: false
                        ),
                        value: $form.ounces.value,
                        focusedField: $focusedField
                    ) { focusedField = .inches }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.inches,
                            inputType: .metric,
                            errorMessage: form.lengthError,
                            isDisabled: valuesLocked,
                            focusField: .inches,
                            allowWholeNumbers: false
                        ),
                        value: $form.inches.value,
                        focusedField: $focusedField
                    ) { focusedField = .notes }
                }

                AppInputField(
                    config: TextInputConfig(
                        label: lang.notes,
                        inputType: .notes,
                        focusField: .notes
                    ),
                    value: $form.notes.value,
                    focusedField: $focusedField
                )

                datePicker

                HStack {
                    Spacer()
                    ButtonView(
                        text: CommonStrings.save,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !isValid || isSaving
                    ) { saveEntry() }
                    Spacer()
                }
            }
            .padding(.spacingMD)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    // MARK: - Private Views

    private var closeButton: some View {
        HStack {
            Spacer()
            Button { dismiss() } label: {
                Image(systemName: "xmark")
                    .fontWeight(.semibold)
                    .foregroundStyle(theme.textBody)
            }
            .buttonStyle(.plain)
        }
    }

    private var datePicker: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            Text(labels.date)
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)

            HStack(spacing: .spacingSM) {
                DateLabelView(date: form.date.value, isSelected: showDatePicker) {
                    toggleDatePicker()
                }
                TimeLabelView(time: form.time.value, isSelected: showTimePicker) {
                    toggleTimePicker()
                }
            }
            .padding(.leading, 2)

            DatePickerView(
                isPresented: $showDatePicker,
                date: $form.date.value,
                startDate: Date(timeIntervalSince1970: 946684800),
                endDate: Date()
            )
            .onChange(of: showDatePicker) { _, isPresented in
                if isPresented {
                    focusedField = nil
                    if showTimePicker { showTimePicker = false }
                }
            }

            TimePickerView(
                isPresented: $showTimePicker,
                time: $form.time.value,
                selectedDate: form.date.value,
                endTime: Date()
            )
            .onChange(of: showTimePicker) { _, isPresented in
                if isPresented {
                    focusedField = nil
                    if showDatePicker { showDatePicker = false }
                }
            }
        }
    }

    // MARK: - Actions

    private func toggleDatePicker() {
        focusedField = nil
        withAnimation { showDatePicker.toggle() }
        if showTimePicker { showTimePicker = false }
    }

    private func toggleTimePicker() {
        focusedField = nil
        withAnimation { showTimePicker.toggle() }
        if showDatePicker { showDatePicker = false }
    }

    private func saveEntry() {
        let timestamp = DateTimeTools.isoString(date: form.date.value, time: form.time.value, useUTC: true)

        let weightDecigrams: Int
        let lengthMm: Int

        if isMetric {
            let kg = Double(form.kg.value) ?? entry.weightKg
            weightDecigrams = ConversionTools.convertBabyKgToDecigrams(kg)
            let cm = Double(form.cm.value) ?? entry.lengthCm
            lengthMm = cm > 0 ? ConversionTools.convertBabyCmToMm(cm) : 0
        } else {
            let lbs = Int(form.pounds.value) ?? entry.weightLbs
            let oz = Double(form.ounces.value) ?? entry.weightOz
            weightDecigrams = ConversionTools.convertBabyLbsOzToDecigrams(lbs: lbs, oz: oz)
            let inches = Double(form.inches.value) ?? entry.lengthInches
            lengthMm = inches > 0 ? ConversionTools.convertBabyInchesToMm(inches) : 0
        }

        isSaving = true
        Task {
            await historyStore.updateBabyEntry(
                old: entry,
                note: form.notes.value,
                weightDecigrams: weightDecigrams,
                lengthMm: lengthMm,
                entryTimestamp: timestamp
            )
            isSaving = false
            dismiss()
        }
    }
}
