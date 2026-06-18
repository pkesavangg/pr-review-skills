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
    @State private var isSaving = false
    @State private var focusedField: FocusField?

    init(entry: BPHistoryEntry) {
        self.entry = entry
        _systolicText = State(initialValue: "\(entry.systolic)")
        _diastolicText = State(initialValue: "\(entry.diastolic)")
        _pulseText = State(initialValue: "\(entry.pulse)")
        _notesText = State(initialValue: entry.notes ?? "")
        _entryDate = State(initialValue: DateTimeTools.parse(entry.entryTimestamp) ?? Date())
    }

    private var isValid: Bool {
        (Int(systolicText) ?? 0) > 0 &&
        (Int(diastolicText) ?? 0) > 0 &&
        (Int(pulseText) ?? 0) > 0
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: .spacingLG) {
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .fontWeight(.semibold)
                            .foregroundStyle(theme.textBody)
                    }
                    .buttonStyle(.plain)
                }

                Group {
                    labeledField(label: HistoryListStrings.systolic, text: $systolicText, keyboard: .numberPad)
                    labeledField(label: HistoryListStrings.diastolic, text: $diastolicText, keyboard: .numberPad)
                    labeledField(label: HistoryListStrings.pulse, text: $pulseText, keyboard: .numberPad)
                }

                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text("Date")
                        .fontOpenSans(.subHeading2)
                        .foregroundStyle(theme.textSubheading)
                    HStack(spacing: .spacingSM) {
                        DatePicker("", selection: $entryDate, displayedComponents: .date)
                            .datePickerStyle(.compact)
                            .labelsHidden()
                        DatePicker("", selection: $entryDate, displayedComponents: .hourAndMinute)
                            .datePickerStyle(.compact)
                            .labelsHidden()
                    }
                }

                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(HistoryListStrings.notes)
                        .fontOpenSans(.subHeading2)
                        .foregroundStyle(theme.textSubheading)
                    NotesInputField(
                        config: TextInputConfig(label: HistoryListStrings.addNotesPlaceholder, focusField: .notes),
                        value: $notesText,
                        focusedField: $focusedField
                    )
                }

                HStack {
                    Spacer()
                    ButtonView(
                        text: CommonStrings.save,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !isValid || isSaving
                    ) {
                        saveEntry()
                    }
                    Spacer()
                }
            }
            .padding(.spacingMD)
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func labeledField(label: String, text: Binding<String>, keyboard: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundStyle(theme.textSubheading)
            HStack {
                TextField("", text: text)
                    .font(.body2)
                    .foregroundStyle(theme.textBody)
                    .keyboardType(keyboard)
                if !text.wrappedValue.isEmpty {
                    Button {
                        text.wrappedValue = ""
                    } label: {
                        Image(systemName: "xmark.circle")
                            .foregroundStyle(theme.textSubheading)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.spacingXS)
            .background(theme.backgroundSecondary)
            .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
        }
    }

    private func saveEntry() {
        guard let sys = Int(systolicText), let dia = Int(diastolicText), let pul = Int(pulseText) else { return }
        isSaving = true
        let timestamp = DateTimeTools.isoString(date: entryDate, time: entryDate, useUTC: true)
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
