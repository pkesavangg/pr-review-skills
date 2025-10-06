//
//  HistoryEntryItem.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI
import Combine

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
    var openItemID: Binding<UUID?>? = nil // Optional binding for swipeable open tracking
    
    // iOS 17 fix: Stable animation state
    @State private var animationPhase: UUID = UUID()
    @State private var isAnimating = false
    
    // MARK: - Computed Properties
    
    // MARK: - Body
    
    var body: some View {
        VStack(spacing: 0) {
            // Main entry row
            HStack {
                // Date and time
                VStack(alignment: .leading, spacing: 2) {
                    Text(DateTimeTools.getFormattedDay(entry.entryTimestamp))
                        .fontOpenSans(.heading5)
                        .foregroundColor( isExpanded ? theme.textInverse : theme.textHeading)
                    
                    Text(DateTimeTools.getFormattedTime(entry.entryTimestamp))
                        .fontOpenSans(.body3)
                        .foregroundColor( isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                
                // Weight value
                HStack(spacing: .spacingXS) {
                    Text(WeightValueConvertor.formatWeight(Double(entry.scaleEntry?.weight ?? 0), showSymbol: false, weightUnit: weightUnit, weightless: weightlessSettings))
                        .fontOpenSans(.heading3)
                        .foregroundColor(isExpanded ? theme.textInverse : theme.textHeading)
                    
                    Text(WeightValueConvertor.unitForDisplay(
                        value: ConversionTools.convertStoredToDisplay(Int(entry.scaleEntry?.weight ?? 0), isMetric: weightUnit == .kg),
                        unit: weightUnit
                    ))
                        .fontOpenSans(.body2)
                        .foregroundColor(isExpanded ? theme.actionInverseSecondary : theme.textSubheading)
                }
                
                // Expansion chevron (only if metrics exist)
                if !entry.metricItems.isEmpty {
                    AppIconView(icon: isExpanded ? AppAssets.chevronUp : AppAssets.chevronDown)
                        .foregroundColor(isExpanded ? theme.actionInverse : theme.statusIconPrimary)
                        .padding(.leading, .spacingSM)
                }
            }
            .padding(.vertical, .spacingSM)
            .padding(.horizontal, .spacingSM)
            .contentShape(Rectangle())
            .background(isExpanded ? theme.actionSecondary : Color.clear)
            // Swipeable delete action
            .swipeableActions(
                buttons: [
                    SwipeButton(
                        tint: theme.textError,
                        action: { onDelete() },
                        label: {
                            AnyView(
                                Text(CommonStrings.delete)
                                    .fontOpenSans(.button1)
                                    .fontWeight(.bold)
                                    .foregroundColor(theme.textInverse)
                            )
                        }
                    )
                ],
                itemID: entry.id,
                openItemID: openItemID
            )
            
            Divider()
                .foregroundColor(theme.actionPrimary)
            
            //            // iOS 17 fix: Stable expanded metrics section with proper animation
            if isExpanded, !entry.metricItems.isEmpty {
                VStack(spacing: 0) {
                    // iOS 17 fix: Use regular VStack instead of LazyVStack to prevent layout churn
                    ForEach(Array(entry.metricItems.enumerated()), id: \.0) { index, item in
                        HistoryMetricItem(
                            metric: BodyMetrics.config[item.metric]!,
                            value: item.value,
                            isAlternate: index % 2 == 1,
                            onTap: { onMetricTap(entry, item.metric) }
                        )
                        .id("\(entry.id.uuidString)-metric-\(index)") // iOS 17 fix: Stable metric IDs
                    }
                }
                .transition(.asymmetric(
                    insertion: .opacity.combined(with: .move(edge: .top)),
                    removal: .opacity.combined(with: .move(edge: .top))
                ))
            }
        }
        // TODO: iOS 17 fix: Remove conflicting animations need to be handled carefully later
        .animation(.easeOut(duration: 0.25), value: animationPhase)
        .contentShape(Rectangle())
        .onTapGesture {
            guard !entry.metricItems.isEmpty else { return }
            onTap()
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
        
        @State var openItemID: UUID? = nil
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



