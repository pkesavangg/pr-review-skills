//
//  HistoryEntryItem.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import Combine
import SwiftUI

/// Reusable view that displays a single history entry with expandable metrics
/// Supports selection, expansion, and swipe-to-delete functionality
struct HistoryEntryItem: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.weightlessSettings) private var weightlessSettings
    @Environment(\.weightUnit) private var weightUnit

    let entry: EntrySnapshot
    let isExpanded: Bool
    let onTap: () -> Void
    let onDelete: () -> Void
    let onMetricTap: (EntrySnapshot, BodyMetric) -> Void
    /// Called when the user taps the "+"/pencil icon in the expanded notes section (MOB-1172).
    var onEditNotes: () -> Void = {}
    var openItemID: Binding<UUID?>? // Optional binding for swipeable open tracking

    // MARK: - Computed Properties

    private var hasNotes: Bool { !(entry.note ?? "").isEmpty }

    private var combinedAccessibilityLabel: String {
        let day = DateTimeTools.getFormattedDay(entry.entryTimestamp)
        let time = DateTimeTools.getFormattedTime(entry.entryTimestamp)
        let weightVal = WeightValueConvertor.formatWeight(
            Double(entry.scaleEntry?.weight ?? 0),
            showSymbol: false,
            weightUnit: weightUnit,
            weightless: weightlessSettings
        )
        let unitLabel = WeightValueConvertor.unitForDisplay(unit: weightUnit)
        return "\(day), \(time), \(weightVal) \(unitLabel)"
    }

    // Hide "bpm" on pulse for weight-scale entries so heart rate renders as a bare number.
    private func displayMetric(for metric: BodyMetric, config: MetricData) -> MetricData {
        guard metric == .pulse, entry.entryType == EntryType.scale.rawValue else {
            return config
        }
        return MetricData(
            unit: "",
            label: config.label,
            expandedLabel: config.expandedLabel,
            bodyCompositionRelated: config.bodyCompositionRelated,
            icon: config.icon,
            min: config.min,
            max: config.max,
            isWholeNumber: config.isWholeNumber,
            preLabel: config.preLabel
        )
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(DateTimeTools.getFormattedDay(entry.entryTimestamp))
                        .fontOpenSans(.heading5)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                    Text(DateTimeTools.getFormattedTimeLowercased(entry.entryTimestamp))
                        .fontOpenSans(.body3)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Weight value
                HStack(spacing: .spacingXS) {
                    Text(WeightValueConvertor.formatWeight(
                        Double(entry.scaleEntry?.weight ?? 0),
                        showSymbol: false,
                        weightUnit: weightUnit,
                        weightless: weightlessSettings
                    ))
                        .fontOpenSans(.heading3)
                        .foregroundStyle(isExpanded ? theme.textInverse : theme.brandWgPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .allowsTightening(true)

                    Text(WeightValueConvertor.unitForDisplay(unit: weightUnit))
                        .fontOpenSans(.body2)
                        .foregroundStyle(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .layoutPriority(1)
                .fixedSize(horizontal: true, vertical: false)

                // Expansion chevron — always shown: every entry expands to reveal its
                // metrics (if any) and the note add/edit affordance (MOB-1172).
                AppIconView(icon: AppAssets.chevronDown)
                    .foregroundStyle(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                    .rotationEffect(.degrees(isExpanded ? 180 : 0))
                    .padding(.leading, .spacingSM)
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())
            // Expanded row is a dark highlight: its values use inverse (light) text, so the
            // background must be the dark actionPrimary. actionSecondary is the same light
            // token as textInverse, which made the values invisible.
            .background(isExpanded ? theme.actionPrimary : Color.clear)
            .accessibilityIdentifier(AccessibilityID.historyEntryRow)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(combinedAccessibilityLabel)
            .accessibilityAddTraits(.isButton)
            .accessibilityHint(isExpanded ? HistoryListStrings.accEntryCollapseHint : HistoryListStrings.accEntryExpandHint)
            // Swipeable delete action
            .swipeableActions(
                buttons: [
                    SwipeButton(
                        tint: theme.textError,
                        action: { onDelete() },
                        label: {
                            AnyView(
                                Text(CommonStrings.delete.uppercased())
                                    .fontOpenSans(.button1)
                                    .fontWeight(.bold)
                                    .foregroundStyle(theme.textInverse)
                                    .accessibilityLabel(HistoryListStrings.accDeleteEntryLabel)
                                    .accessibilityIdentifier(AccessibilityID.historyDeleteButton)
                            )
                        }
                    )
                ],
                itemID: entry.id,
                openItemID: openItemID
            )

            Divider()
                .foregroundColor(theme.actionPrimary)

            // Expanded section: note add/edit affordance + body metrics
            if isExpanded {
                VStack(spacing: 0) {
                    notesSection

                    ForEach(Array(entry.metricItems.enumerated()), id: \.0) { index, item in
                        if let metricConfig = BodyMetrics.config[item.metric] {
                            HistoryMetricItem(
                                metric: displayMetric(for: item.metric, config: metricConfig),
                                metricType: item.metric,
                                value: item.value,
                                index: index,
                                size: entry.metricItems.count
                            ) { onMetricTap(entry, item.metric) }
                            .id("\(entry.id.uuidString)-metric-\(index)")
                        }
                    }
                }
                .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: isExpanded)
        .contentShape(Rectangle())
        .onTapGesture {
            onTap()
        }
    }

    // MARK: - Notes Section

    /// Expanded-state note row: shows the saved note (or a placeholder) with a "+" to add
    /// or a boxed pencil to edit. Tapping opens the edit overlay (MOB-1172).
    private var notesSection: some View {
        HStack(alignment: .center, spacing: .spacingXS) {
            if hasNotes {
                Text(entry.note ?? "")
                    .fontOpenSans(.body3)
                    .foregroundStyle(theme.textBody)
            } else {
                Text(HistoryListStrings.noNotesPlaceholder)
                    .fontOpenSans(.body3)
                    .foregroundStyle(theme.textSubheading)
            }
            Spacer()
            Button(action: onEditNotes) {
                // "+" to add when no note exists, boxed pencil to edit once it does.
                Image(systemName: hasNotes ? "square.and.pencil" : "plus")
                    .font(.system(size: 18))
                    .foregroundStyle(theme.actionPrimary)
                    // Guarantee at least a 44×44pt tap target (Apple HIG) — the glyph stays
                    // visually 18pt but the whole square is tappable.
                    .frame(width: 44, height: 44)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(hasNotes ? HistoryListStrings.accEditNoteLabel : HistoryListStrings.accAddNoteLabel)
            .appAccessibility(id: AccessibilityID.historyEditNoteButton)
        }
        .padding(.spacingSM)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(theme.backgroundSecondary)
    }
}

// MARK: - Preview

#if DEBUG
struct HistoryEntryItem_Previews: PreviewProvider {
    static var previews: some View {
        let entry = EntrySnapshot(
            id: UUID(),
            accountId: "123",
            entryTimestamp: "2025-12-16T14:10:00Z",
            serverTimestamp: nil,
            serverEntryId: nil,
            opTimestamp: nil,
            operationType: OperationType.create.rawValue,
            entryType: EntryType.scale.rawValue,
            isSynced: true,
            note: nil,
            attempts: 0,
            isFailedToSync: false,
            scaleEntry: BathScaleEntrySnapshot(
                weight: 1492,
                bodyFat: 50,
                muscleMass: 569,
                water: 53,
                bmi: nil,
                source: nil,
                systolic: nil,
                diastolic: nil,
                meanArterial: nil
            ),
            scaleEntryMetric: BathScaleMetricSnapshot(
                bmr: 1862,
                metabolicAge: 28,
                proteinPercent: nil,
                pulse: 80,
                skeletalMusclePercent: 527,
                subcutaneousFatPercent: 103,
                visceralFatLevel: 8,
                boneMass: 44,
                impedance: 100,
                unit: "kg"
            ),
            bpmEntry: nil,
            babyEntry: nil
        )

        @State var openItemID: UUID?
        return VStack(spacing: .spacingMD) {
            HistoryEntryItem(
                entry: entry,
                isExpanded: false,
                onTap: {},
                onDelete: {},
                onMetricTap: { _, _ in },
                openItemID: .constant(nil)
            )

            HistoryEntryItem(
                entry: entry,
                isExpanded: true,
                onTap: {},
                onDelete: {},
                onMetricTap: { _, _ in },
                openItemID: .constant(nil)
            )
        }
        .padding()
        .themeable()
        .environmentObject(Theme.shared)
        .previewLayout(.sizeThatFits)
    }
}
#endif
