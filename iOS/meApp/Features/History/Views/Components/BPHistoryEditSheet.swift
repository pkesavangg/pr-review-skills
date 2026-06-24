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

    private let labels = InputFieldLabels.self
    private let lang = HistoryListStrings.self

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

    private var isValid: Bool {
        (Int(systolicText) ?? 0) > 0 &&
        (Int(diastolicText) ?? 0) > 0 &&
        (Int(pulseText) ?? 0) > 0
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                closeButton

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.systolic,
                        inputType: .metric,
                        focusField: .systolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $systolicText,
                    focusedField: $focusedField
                ) { focusedField = .diastolic }

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.diastolic,
                        inputType: .metric,
                        focusField: .diastolic,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $diastolicText,
                    focusedField: $focusedField
                ) { focusedField = .pulse }

                MetricInputField(
                    config: TextInputConfig(
                        label: lang.pulse,
                        inputType: .metric,
                        focusField: .pulse,
                        maxLength: 3,
                        allowWholeNumbers: true
                    ),
                    value: $pulseText,
                    focusedField: $focusedField
                ) { focusedField = .notes }

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
