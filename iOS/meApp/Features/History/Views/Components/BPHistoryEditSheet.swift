//
//  BPHistoryEditSheet.swift
//  meApp
//

import SwiftUI

/// Bottom-sheet form for editing an existing blood pressure history entry.
/// Saves via delete-old + create-new (no server-side PATCH endpoint exists yet).
struct BPHistoryEditSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var historyStore: HistoryStore

    let entry: BPHistoryEntry

    @State private var systolicText: String
    @State private var diastolicText: String
    @State private var pulseText: String
    @State private var notesText: String
    @State private var entryDate: Date
    @State private var entryTime: Date
    @State private var showDatePicker = false
    @State private var showTimePicker = false
    @State private var isSaving = false
    @State private var focusedField: FocusField?

    // Dirty flags — errors and warnings only appear after the user edits a field
    @State private var systolicDirty = false
    @State private var diastolicDirty = false
    @State private var pulseDirty = false

    private let labels = InputFieldLabels.self
    private let lang = HistoryListStrings.self
    private let entryLang = ManualEntryStrings.self

    init(entry: BPHistoryEntry) {
        self.entry = entry
        let parsed = DateTimeTools.parse(entry.entryTimestamp) ?? Date()
        _systolicText = State(initialValue: "\(entry.systolic)")
        _diastolicText = State(initialValue: "\(entry.diastolic)")
        _pulseText = State(initialValue: "\(entry.pulse)")
        _notesText = State(initialValue: entry.notes ?? "")
        _entryDate = State(initialValue: parsed)
        _entryTime = State(initialValue: parsed)
    }

    // MARK: - Validation

    private var systolicError: String? {
        guard systolicDirty else { return nil }
        let val = Int(systolicText) ?? 0
        if val == 0 { return entryLang.required }
        if val > 500 { return entryLang.maxLimit }
        return nil
    }

    private var diastolicError: String? {
        guard diastolicDirty else { return nil }
        let val = Int(diastolicText) ?? 0
        if val == 0 { return entryLang.required }
        if val > 500 { return entryLang.maxLimit }
        return nil
    }

    private var pulseError: String? {
        guard pulseDirty else { return nil }
        let val = Int(pulseText) ?? 0
        if val == 0 { return entryLang.required }
        if val > 500 { return entryLang.maxLimit }
        return nil
    }

    private var systolicWarning: String? {
        guard systolicDirty, systolicError == nil else { return nil }
        guard let sys = Int(systolicText) else { return nil }
        let dia = Int(diastolicText)
        if sys >= 60, sys <= 250, let dia, dia > sys { return entryLang.systolicReversed }
        if sys < 60 || sys > 250 { return entryLang.typicalRange(60, 250) }
        return nil
    }

    private var diastolicWarning: String? {
        guard diastolicDirty, diastolicError == nil else { return nil }
        guard let dia = Int(diastolicText) else { return nil }
        let sys = Int(systolicText)
        if dia >= 30, dia <= 150 {
            if let sys, sys < dia { return entryLang.diastolicReversed }
            if systolicText.isEmpty { return entryLang.diastolicReversed }
        }
        if dia < 30 || dia > 150 { return entryLang.typicalRange(30, 150) }
        return nil
    }

    private var pulseWarning: String? {
        guard pulseDirty, pulseError == nil else { return nil }
        guard let pul = Int(pulseText) else { return nil }
        if pul < 20 || pul > 200 { return entryLang.typicalRange(20, 200) }
        return nil
    }

    private var isValid: Bool {
        let sys = Int(systolicText) ?? 0
        let dia = Int(diastolicText) ?? 0
        let pul = Int(pulseText) ?? 0
        return sys > 0 && sys <= 500 && dia > 0 && dia <= 500 && pul > 0 && pul <= 500
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                closeButton

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.systolic,
                        inputType: .metric,
                        errorMessage: systolicError ?? systolicWarning,
                        focusField: .systolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $systolicText,
                    focusedField: $focusedField
                ) { focusedField = .diastolic }
                .onChange(of: systolicText) { _, _ in systolicDirty = true }

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.diastolic,
                        inputType: .metric,
                        errorMessage: diastolicError ?? diastolicWarning,
                        focusField: .diastolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $diastolicText,
                    focusedField: $focusedField
                ) { focusedField = .pulse }
                .onChange(of: diastolicText) { _, _ in diastolicDirty = true }

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.pulse,
                        inputType: .metric,
                        errorMessage: pulseError ?? pulseWarning,
                        focusField: .pulse,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $pulseText,
                    focusedField: $focusedField
                ) { focusedField = .notes }
                .onChange(of: pulseText) { _, _ in pulseDirty = true }

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
        guard let sys = Int(systolicText), let dia = Int(diastolicText), let pul = Int(pulseText) else { return }
        isSaving = true
        let timestamp = DateTimeTools.isoString(date: entryDate, time: entryTime, useUTC: true)
        Task {
            await historyStore.updateBPEntry(
                old: entry,
                systolic: sys,
                diastolic: dia,
                pulse: pul,
                note: notesText,
                entryTimestamp: timestamp
            )
            isSaving = false
            dismiss()
        }
    }
}
