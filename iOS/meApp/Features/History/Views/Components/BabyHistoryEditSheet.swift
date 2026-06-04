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
                        Text("MEASUREMENT")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        HStack(spacing: .spacingLG) {
                            measurementLabel(title: HistoryListStrings.weight, value: entry.weightDisplay)
                            measurementLabel(title: HistoryListStrings.length, value: entry.lengthDisplay)
                            measurementLabel(title: HistoryListStrings.percentile, value: entry.percentile > 0 ? "\(entry.percentile)th" : "--")
                        }
                    }
                    .padding(.spacingSM)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(theme.backgroundSecondary)
                    .cornerRadius(.radiusSM)

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text("DATE")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        Text(DateTimeTools.getArrivalRelativeTime(fromISOString: entry.entryTimestamp)
                             ?? DateTimeTools.getFormattedDay(entry.entryTimestamp))
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text("NOTES")
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.textSubheading)
                        TextEditor(text: $notesText)
                            .font(.body2)
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
                        isDisabled: isSaving
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
    private func measurementLabel(title: String, value: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.textHeading)
            Text(title)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textSubheading)
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
