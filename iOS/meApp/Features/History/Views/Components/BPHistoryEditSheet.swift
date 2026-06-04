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
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: .spacingLG) {
                    Group {
                        labeledField(label: HistoryListStrings.mmhg.uppercased(), text: $systolicText, keyboard: .numberPad)
                        labeledField(label: "DIASTOLIC (mmhg)", text: $diastolicText, keyboard: .numberPad)
                        labeledField(label: HistoryListStrings.pulse.uppercased(), text: $pulseText, keyboard: .numberPad)
                    }

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text("DATE")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        DatePicker("", selection: $entryDate, displayedComponents: [.date, .hourAndMinute])
                            .datePickerStyle(.compact)
                            .labelsHidden()
                    }

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text("NOTES")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        TextEditor(text: $notesText)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .frame(minHeight: 80)
                            .padding(.spacingXS)
                            .overlay(
                                RoundedRectangle(cornerRadius: .radiusSM)
                                    .stroke(theme.glow, lineWidth: 1)
                            )
                    }

                    ButtonView(
                        text: CommonStrings.save,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: !isValid || isSaving
                    ) {
                        saveEntry()
                    }
                }
                .padding(.spacingMD)
            }
            .navigationTitle("Edit Reading")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(CommonStrings.cancel) { dismiss() }
                        .foregroundColor(theme.actionPrimary)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func labeledField(label: String, text: Binding<String>, keyboard: UIKeyboardType) -> some View {
        VStack(alignment: .leading, spacing: .spacingXS) {
            Text(label)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
            TextField("", text: text)
                .fontOpenSans(.body2)
                .foregroundColor(theme.textBody)
                .keyboardType(keyboard)
                .padding(.spacingXS)
                .overlay(
                    RoundedRectangle(cornerRadius: .radiusSM)
                        .stroke(theme.glow, lineWidth: 1)
                )
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
