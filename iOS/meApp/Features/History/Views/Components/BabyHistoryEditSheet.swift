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
    @State private var isSaving = false

    private let notesLimit = 280

    init(entry: BabyHistoryEntry) {
        self.entry = entry
        _lbsText = State(initialValue: "\(entry.weightLbs)")
        _ozText = State(initialValue: String(format: "%.1f", entry.weightOz))
        _kgText = State(initialValue: entry.weightKg > 0 ? String(format: "%.3f", entry.weightKg) : "")
        _inchesText = State(initialValue: entry.lengthInches > 0 ? String(format: "%.1f", entry.lengthInches) : "")
        _cmText = State(initialValue: entry.lengthCm > 0 ? String(format: "%.1f", entry.lengthCm) : "")
        _notesText = State(initialValue: entry.notes ?? "")
        _entryDate = State(initialValue: DateTimeTools.parse(entry.entryTimestamp) ?? Date())
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
                HStack {
                    Spacer()
                    Button { dismiss() } label: {
                        Image(systemName: "xmark")
                            .fontWeight(.semibold)
                            .foregroundStyle(theme.textBody)
                    }
                    .buttonStyle(.plain)
                }

                if isMetric {
                    labeledField(label: HistoryListStrings.kg, text: $kgText, keyboard: .decimalPad)
                    labeledField(label: HistoryListStrings.cm, text: $cmText, keyboard: .decimalPad)
                } else {
                    labeledField(label: HistoryListStrings.pounds, text: $lbsText, keyboard: .numberPad)
                    labeledField(label: HistoryListStrings.ounces, text: $ozText, keyboard: .decimalPad)
                    labeledField(label: HistoryListStrings.inches, text: $inchesText, keyboard: .decimalPad)
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
                    ZStack(alignment: .bottomTrailing) {
                        TextField("Add notes…", text: $notesText, axis: .vertical)
                            .font(.body2)
                            .foregroundStyle(theme.textBody)
                            .lineLimit(4...)
                            .padding(.spacingXS)
                            .padding(.bottom, .spacingLG)
                            .onChange(of: notesText) { _, newValue in
                                if newValue.count > notesLimit {
                                    notesText = String(newValue.prefix(notesLimit))
                                }
                            }
                        Text("\(notesText.count)/\(notesLimit)")
                            .fontOpenSans(.body3)
                            .foregroundStyle(notesText.count >= notesLimit ? theme.textError : theme.textSubheading)
                            .padding(.spacingXS)
                    }
                    .background(theme.backgroundSecondary)
                    .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
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
        let timestamp = DateTimeTools.isoString(date: entryDate, time: entryDate, useUTC: true)

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
