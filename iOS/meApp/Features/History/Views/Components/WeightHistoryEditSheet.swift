//
//  WeightHistoryEditSheet.swift
//  meApp
//

import SwiftUI

/// Bottom-sheet form for editing an existing weight-scale history entry.
/// Saves via delete-old + create-new (no server-side PATCH endpoint exists yet).
///
/// Per MOB-1172 the editable surface depends on how the entry was recorded:
/// manually-entered readings are fully editable (weight + core body metrics + note);
/// device-synced readings are note-only, with all measured values shown read-only.
struct WeightHistoryEditSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var historyStore: HistoryStore

    let entry: EntrySnapshot

    /// Adult weight unit (kg vs lb), captured from the store at presentation time.
    private let isMetric: Bool

    /// Called just before the sheet dismisses after a successful save. The `Bool` reports
    /// whether the entry's calendar date changed — the parent uses it to pop back to the
    /// History list (the edited entry may have moved to a different month) instead of leaving
    /// the user on the now-stale month detail (MOB-1172).
    private let onSaved: ((_ dateChanged: Bool) -> Void)?

    /// The entry's original calendar date at presentation time, used to detect a date change.
    private let originalDate: Date

    /// Reactive form driving the editable fields. Reusing `ManualEntryForm` (the same form the
    /// create screen uses) gives the edit sheet identical field-level validation and error
    /// messages: weight is required / >0 / <= max, and each body metric is clamped to 0–99.9.
    @StateObject private var form: ManualEntryForm

    @State private var entryDate: Date
    @State private var entryTime: Date
    @State private var showMetrics: Bool
    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var isSaving = false
    @State private var focusedField: FocusField?

    private let labels = InputFieldLabels.self
    private let lang = HistoryListStrings.self
    private let manualLang = ManualEntryStrings.self
    private let commonLang = CommonStrings.self

    /// Weight unit the form validates against, derived from the adult weight preference.
    private var weightUnit: WeightUnit { isMetric ? .kg : .lb }

    init(entry: EntrySnapshot, isMetric: Bool, onSaved: ((_ dateChanged: Bool) -> Void)? = nil) {
        self.entry = entry
        self.isMetric = isMetric
        self.onSaved = onSaved
        let parsed = DateTimeTools.parse(entry.entryTimestamp) ?? Date()
        self.originalDate = parsed
        let scale = entry.scaleEntry
        let storedWeight = scale?.weight ?? 0
        let displayWeight = ConversionTools.convertStoredToDisplay(storedWeight, isMetric: isMetric)

        let form = ManualEntryForm()
        form.weight.value = storedWeight > 0 ? String(format: "%.1f", displayWeight) : ""
        form.bmi.value = Self.tenthsToText(scale?.bmi)
        form.bodyFat.value = Self.tenthsToText(scale?.bodyFat)
        form.muscleMass.value = Self.tenthsToText(scale?.muscleMass)
        form.bodyWater.value = Self.tenthsToText(scale?.water)
        form.notes.value = entry.note ?? ""
        _form = StateObject(wrappedValue: form)

        _entryDate = State(initialValue: parsed)
        _entryTime = State(initialValue: parsed)
        // Expand the metrics accordion when the entry carries any core body metric.
        let hasMetrics = (scale?.bmi ?? 0) > 0 || (scale?.bodyFat ?? 0) > 0
            || (scale?.muscleMass ?? 0) > 0 || (scale?.water ?? 0) > 0
        _showMetrics = State(initialValue: hasMetrics)
    }

    // MARK: - Edit permissions

    /// Device-synced readings are note-only: measured values came from the scale and stay
    /// read-only. Manually-entered readings are fully editable (MOB-1172).
    private var valuesLocked: Bool { !EntrySource.isManualEntry(entry.scaleEntry?.source) }

    /// Save is gated on the reactive form: weight present and in range, and every filled
    /// body metric within its allowed bounds.
    private var isValid: Bool { form.isValid }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                closeButton

                MetricInputField(
                    config: TextInputConfig(
                        label: labels.weight,
                        inputType: .metric,
                        errorMessage: form.getError(for: form.weight, weightUnit: weightUnit),
                        isDisabled: valuesLocked,
                        focusField: .weight,
                        maxLength: 4,
                        maxValue: 999.9,
                        trailingLabel: labels.weightUnitSuffix(isMetric)
                    ),
                    value: $form.weight.value,
                    focusedField: $focusedField
                ) { focusedField = nil }

                metricsAccordion

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

    // MARK: - Subviews

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

    private var metricsAccordion: some View {
        VStack(spacing: .spacingSM) {
            HStack {
                VStack(alignment: .leading, spacing: 0) {
                    Text(manualLang.bodyMetrics)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    Text("(\(commonLang.optional))")
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textSubheading)
                }
                Spacer()
                AppIconView(icon: showMetrics ? AppAssets.chevronUp : AppAssets.chevronDown,
                            size: IconSize(width: 32, height: 32))
                    .foregroundColor(theme.actionPrimary)
            }
            .contentShape(Rectangle())
            .onTapGesture { withAnimation { showMetrics.toggle() } }
            .accessibilityAddTraits(.isButton)
            .accessibilityLabel(manualLang.accBodyMetricsHeader)

            if showMetrics {
                metricField(label: labels.bmi, control: form.bmi, focus: .bmi, next: .bodyFat)
                metricField(label: labels.bodyFat, control: form.bodyFat, focus: .bodyFat, next: .muscleMass)
                metricField(label: labels.muscleMass, control: form.muscleMass, focus: .muscleMass, next: .bodyWater)
                metricField(label: labels.bodyWater, control: form.bodyWater, focus: .bodyWater, next: .notes)
            }
        }
    }

    private func metricField(label: String, control: FormControl<String>, focus: FocusField, next: FocusField) -> some View {
        MetricInputField(
            config: TextInputConfig(
                label: label,
                inputType: .metric,
                errorMessage: form.getError(for: control, weightUnit: weightUnit),
                isDisabled: valuesLocked,
                focusField: focus,
                maxLength: 3,
                maxValue: 99.9
            ),
            value: Binding(get: { control.value }, set: { control.value = $0 }),
            focusedField: $focusedField
        ) { focusedField = next }
    }

    private var datePicker: some View {
        VStack(alignment: .leading, spacing: .spacingSM) {
            Text(labels.date)
                .fontOpenSans(.subHeading1)
                .foregroundColor(theme.textSubheading)

            HStack(spacing: .spacingSM) {
                DateLabelView(date: entryDate, isSelected: showDatePicker) {
                    toggleDatePicker()
                }
                TimeLabelView(time: entryTime, isSelected: showTimePicker) {
                    toggleTimePicker()
                }
            }
            .padding(.leading, 2)

            DatePickerView(
                isPresented: $showDatePicker,
                date: $entryDate,
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
                time: $entryTime,
                selectedDate: entryDate,
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
        guard form.isValid, let weightDisplay = Double(form.weight.value), weightDisplay > 0 else { return }
        isSaving = true
        let storedWeight = ConversionTools.convertDisplayToStored(weightDisplay, isMetric: isMetric)
        let timestamp = DateTimeTools.isoString(date: entryDate, time: entryTime, useUTC: true)
        let dateChanged = !Calendar.current.isDate(entryDate, inSameDayAs: originalDate)
        Task {
            await historyStore.updateWGEntry(
                old: entry,
                weight: storedWeight,
                bmi: Self.textToTenths(form.bmi.value),
                bodyFat: Self.textToTenths(form.bodyFat.value),
                muscleMass: Self.textToTenths(form.muscleMass.value),
                water: Self.textToTenths(form.bodyWater.value),
                note: form.notes.value,
                entryTimestamp: timestamp
            )
            isSaving = false
            onSaved?(dateChanged)
            dismiss()
        }
    }

    // MARK: - Conversion helpers

    /// Stored tenths (value × 10) → display text; empty for nil/zero.
    private static func tenthsToText(_ stored: Int?) -> String {
        guard let stored, stored > 0 else { return "" }
        return String(format: "%.1f", Double(stored) / 10.0)
    }

    /// Display text → stored tenths, mirroring the manual-entry save path.
    private static func textToTenths(_ text: String) -> Int? {
        guard let value = Double(text) else { return nil }
        // Round rather than floor: `value * 10` on a Double can land just under the
        // intended tenth (e.g. 12.3 → 122.9999…), so `floor` would truncate to the wrong tenth.
        return Int((value * 10).rounded())
    }
}
