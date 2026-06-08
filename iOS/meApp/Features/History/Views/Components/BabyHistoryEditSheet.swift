//
//  BabyHistoryEditSheet.swift
//  meApp
//

import SwiftUI

/// Bottom-sheet form for editing notes on a baby history entry.
/// Full value editing (weight/length) reconstructs and re-saves via delete-old + create-new.
struct BabyHistoryEditSheet: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject var historyStore: HistoryStore

    let entry: BabyHistoryEntry

    @State private var notesText: String
    @State private var isSaving = false

    init(entry: BabyHistoryEntry) {
        self.entry = entry
        _notesText = State(initialValue: entry.notes ?? "")
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: .spacingLG) {
                    // Read-only summary of measurement values
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(HistoryListStrings.measurement)
                            .fontOpenSans(.subHeading2)
                            .foregroundStyle(theme.textSubheading)
                        HStack(spacing: .spacingLG) {
                            measurementLabel(title: HistoryListStrings.weight, value: entry.weightDisplay)
                            measurementLabel(title: HistoryListStrings.length, value: entry.lengthDisplay)
                            measurementLabel(title: HistoryListStrings.percentile, value: entry.percentile > 0 ? "\(entry.percentile)\(HistoryListStrings.th)" : "--")
                        }
                    }
                    .padding(.spacingSM)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.backgroundSecondary)
                    .cornerRadius(.radiusSM)

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(HistoryListStrings.date)
                            .fontOpenSans(.subHeading2)
                            .foregroundStyle(theme.textSubheading)
                        Text(DateTimeTools.getArrivalRelativeTime(fromISOString: entry.entryTimestamp)
                             ?? DateTimeTools.getFormattedDay(entry.entryTimestamp))
                            .fontOpenSans(.body2)
                            .foregroundStyle(theme.textBody)
                    }

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(HistoryListStrings.notes.uppercased())
                            .fontOpenSans(.subHeading2)
                            .foregroundStyle(theme.textSubheading)
                        TextField(HistoryListStrings.addNotesPlaceholder, text: $notesText, axis: .vertical)
                            .font(.body2)
                            .foregroundStyle(theme.textBody)
                            .lineLimit(4...)
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
                        isDisabled: isSaving
                    ) {
                        saveEntry()
                    }
                }
                .padding(.spacingMD)
            }
            .navigationTitle(HistoryListStrings.editReading)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(CommonStrings.cancel) { dismiss() }
                        .foregroundStyle(theme.actionPrimary)
                }
            }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }

    @ViewBuilder
    private func measurementLabel(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .fontOpenSans(.heading5)
                .foregroundStyle(theme.textHeading)
            Text(title)
                .fontOpenSans(.body3)
                .foregroundStyle(theme.textSubheading)
        }
    }

    private func saveEntry() {
        isSaving = true
        Task {
            await historyStore.updateBabyEntry(old: entry, note: notesText)
            isSaving = false
            dismiss()
        }
    }
}
