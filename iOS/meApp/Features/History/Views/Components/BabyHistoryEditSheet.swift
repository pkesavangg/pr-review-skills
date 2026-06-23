//
//  BabyHistoryEditSheet.swift
//  meApp
//

import SwiftUI

/// Bottom-sheet form for editing a baby history entry.
/// Allows editing weight, length, date, and notes. Saves via delete-old + create-new.
struct BabyHistoryEditSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var historyStore: HistoryStore

    let entry: BabyHistoryEntry

    // Imperial fields
    @State private var lbsText: String
    @State private var ozText: String
    @State private var inchesText: String
    // Metric fields
    @State private var kgText: String
    @State private var cmText: String

    @State private var notesText: String
    @State private var entryDate: Date
    @State private var entryTime: Date
    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var isSaving = false
    @State private var focusedField: FocusField?

    private let labels = InputFieldLabels.self
    private let lang = HistoryListStrings.self

    init(entry: BabyHistoryEntry) {
        self.entry = entry
        let parsed = DateTimeTools.parse(entry.entryTimestamp) ?? Date()
        _lbsText = State(initialValue: "\(entry.weightLbs)")
        _ozText = State(initialValue: String(format: "%.1f", entry.weightOz))
        _kgText = State(initialValue: entry.weightKg > 0 ? String(format: "%.3f", entry.weightKg) : "")
        _inchesText = State(initialValue: entry.lengthInches > 0 ? String(format: "%.1f", entry.lengthInches) : "")
        _cmText = State(initialValue: entry.lengthCm > 0 ? String(format: "%.1f", entry.lengthCm) : "")
        _notesText = State(initialValue: entry.notes ?? "")
        _entryDate = State(initialValue: parsed)
        _entryTime = State(initialValue: parsed)
    }

    private var isMetric: Bool { historyStore.isMetric }

    private var isValid: Bool {
        if isMetric {
            return (Double(kgText) ?? 0) > 0
        } else {
            let lbs = Int(lbsText) ?? 0
            let oz = Double(ozText) ?? 0
            return lbs > 0 || oz > 0
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
                            focusField: .weight,
                            allowWholeNumbers: false
                        ),
                        value: $kgText,
                        focusedField: $focusedField
                    ) { focusedField = .inches }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.cm,
                            inputType: .metric,
                            focusField: .inches,
                            allowWholeNumbers: false
                        ),
                        value: $cmText,
                        focusedField: $focusedField
                    ) { focusedField = .notes }
                } else {
                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.pounds,
                            inputType: .metric,
                            focusField: .weight,
                            maxLength: 3,
                            allowWholeNumbers: true
                        ),
                        value: $lbsText,
                        focusedField: $focusedField
                    ) { focusedField = .ounces }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.ounces,
                            inputType: .metric,
                            focusField: .ounces,
                            allowWholeNumbers: false
                        ),
                        value: $ozText,
                        focusedField: $focusedField
                    ) { focusedField = .inches }

                    MetricInputField(
                        config: TextInputConfig(
                            label: lang.inches,
                            inputType: .metric,
                            focusField: .inches,
                            allowWholeNumbers: false
                        ),
                        value: $inchesText,
                        focusedField: $focusedField
                    ) { focusedField = .notes }
                }

                AppInputField(
                    config: TextInputConfig(
                        label: lang.notes,
                        inputType: .notes,
                        focusField: .notes
                    ),
                    value: $notesText,
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
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(labels.date)
                .fontOpenSans(.heading4)
                .foregroundColor(theme.textHeading)

            HStack(spacing: .spacingSM) {
                DateLabelView(date: entryDate, isSelected: showDatePicker) {
                    toggleDatePicker()
                }
                TimeLabelView(time: entryTime, isSelected: showTimePicker) {
                    toggleTimePicker()
                }
            }

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
        let timestamp = DateTimeTools.isoString(date: entryDate, time: entryTime, useUTC: true)

        let weightDecigrams: Int
        let lengthMm: Int

        if isMetric {
            let kg = Double(kgText) ?? entry.weightKg
            weightDecigrams = ConversionTools.convertBabyKgToDecigrams(kg)
            let cm = Double(cmText) ?? entry.lengthCm
            lengthMm = cm > 0 ? ConversionTools.convertBabyCmToMm(cm) : 0
        } else {
            let lbs = Int(lbsText) ?? entry.weightLbs
            let oz = Double(ozText) ?? entry.weightOz
            weightDecigrams = ConversionTools.convertBabyLbsOzToDecigrams(lbs: lbs, oz: oz)
            let inches = Double(inchesText) ?? entry.lengthInches
            lengthMm = inches > 0 ? ConversionTools.convertBabyInchesToMm(inches) : 0
        }

        isSaving = true
        Task {
            await historyStore.updateBabyEntry(
                old: entry,
                note: notesText,
                weightDecigrams: weightDecigrams,
                lengthMm: lengthMm,
                entryTimestamp: timestamp
            )
            isSaving = false
            dismiss()
        }
    }
}
