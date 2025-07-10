//
//  HistoryEntryItem.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

/// Reusable view that displays a single history entry with expandable metrics
/// Supports selection, expansion, and swipe-to-delete functionality
struct HistoryEntryItem: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.weightlessSettings) private var weightlessSettings
    @Environment(\.weightUnit) private var weightUnit

    let entry: Entry
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void
    let onMetricTap: (Entry, BodyMetric) -> Void

    // MARK: - Computed Properties

    private var dateText: String {
      return DateTimeTools.getFormattedDay(entry.entryTimestamp)
    }

    private var timeText: String {
        return DateTimeTools.getFormattedTime(entry.entryTimestamp)
    }

    private var weightText: String {
      let weight = WeightValueConvertor.formatWeight(Double(entry.scaleEntry?.weight ?? 0), showSymbol: false, weightUnit: weightUnit, weightless: weightlessSettings)
      return weight
    }

    private var backgroundColor: Color {
        if isExpanded {
            return theme.actionSecondary
        }
        return .clear
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Date and time
                VStack(alignment: .leading, spacing: 2) {
                    Text(dateText)
                        .fontOpenSans(.heading5)
                        .foregroundColor( isExpanded ? theme.textInverse : theme.textHeading)

                    Text(timeText)
                        .fontOpenSans(.body3)
                        .foregroundColor( isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight value
                HStack(spacing: .spacingXS) {
                    Text(weightText)
                        .fontOpenSans(.heading3)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)

                    Text(weightUnit.rawValue)
                        .fontOpenSans(.body2)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }

                // Expansion chevron
                AppIconView(icon: isExpanded ? AppAssets.chevronUp : AppAssets.chevronDown)
                    .foregroundColor(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                    .padding(.leading, .spacingSM)
                    .animation(.easeOut, value: isExpanded)
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())

            Divider()
                .foregroundColor(theme.actionPrimary)
            // Expanded metrics section
            if isExpanded {
                VStack() {

                    // Build array of available metrics
                    let metricItems: [(value: Int, metric: BodyMetric)] = {
                        var arr: [(Int, BodyMetric)] = []
                        if let bmi = entry.scaleEntry?.bmi {
                            arr.append((bmi, .bmi))
                        }
                        if let bodyFat = entry.scaleEntry?.bodyFat, bodyFat != 0 {
                            arr.append((bodyFat, .bodyFat))
                        }
                        if let muscleMass = entry.scaleEntry?.muscleMass, muscleMass != 0 {
                            arr.append((muscleMass, .muscleMass))
                        }
                        if let water = entry.scaleEntry?.water, water != 0 {
                            arr.append((water, .water))
                        }
                        if let heartRate = entry.scaleEntryMetric?.pulse, heartRate != 0 {
                            arr.append((heartRate, .pulse))
                        }
                        if let boneMass = entry.scaleEntryMetric?.boneMass, boneMass != 0 {
                            arr.append((boneMass, .boneMass))
                        }
                        if let visceralFat = entry.scaleEntryMetric?.visceralFatLevel, visceralFat != 0 {
                            arr.append((visceralFat, .visceralFatLevel))
                        }
                        if let subcutaneousFat = entry.scaleEntryMetric?.subcutaneousFatPercent, subcutaneousFat != 0 {
                            arr.append((subcutaneousFat, .subcutaneousFatPercent))
                        }
                        if let skeletalMuscles = entry.scaleEntryMetric?.skeletalMusclePercent, skeletalMuscles != 0 {
                            arr.append((skeletalMuscles, .skeletalMusclePercent))
                        }
                        if let bmr = entry.scaleEntryMetric?.bmr, bmr != 0 {
                            arr.append((bmr, .bmr))
                        }
                        if let metabolicAge = entry.scaleEntryMetric?.metabolicAge, metabolicAge != 0 {
                            arr.append((metabolicAge, .metabolicAge))
                        }
                        return arr
                    }()

                    LazyVStack(spacing: 0) {
                        ForEach(Array(metricItems.enumerated()), id: \.0) { index, item in
                            HistoryMetricItem(
                                metric: BodyMetrics.config[item.metric]!,
                                value: item.value,
                                isAlternate: index % 2 == 1,
                                onTap: { onMetricTap(entry, item.metric) }
                            )
                        }
                    }
                }
            }
        }
        .background(backgroundColor)
        .animation(.easeOut, value: isExpanded)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
        // Swipe to delete
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }
}


// MARK: - Preview

#if DEBUG
struct HistoryEntryItem_Previews: PreviewProvider {
    static var previews: some View {
        let entry = Entry(
            id: UUID(),
            entryTimestamp: "2025-12-16T14:10:00Z",
            accountId: "123",
            operationType: OperationType.create.rawValue,
            deviceType: "scale",
            isSynced: true
        )

        entry.scaleEntry = BathScaleEntry(
            weight: 1492,
            bodyFat: 50,
            muscleMass: 569,
            water: 53
        )

        entry.scaleEntryMetric = BathScaleMetric(
            bmr: 1862,
            metabolicAge: 28,
            pulse: 80,
            skeletalMusclePercent: 527,
            subcutaneousFatPercent: 103,
            visceralFatLevel: 8,
            boneMass: 44,
            impedance: 100,
            unit: "kg"
        )

        return VStack(spacing: .spacingMD) {
            HistoryEntryItem(
                entry: entry,
                isExpanded: false,
                onTap: {},
                onDelete: {},
                onMetricTap: { _, _ in }
            )

            HistoryEntryItem(
                entry: entry,
                isExpanded: true,
                onTap: {},
                onDelete: {},
                onMetricTap: { _, _ in }
            )
        }
        .padding()
        .themeable()
        .environmentObject(Theme.shared)
        .previewLayout(.sizeThatFits)
    }
}
#endif


