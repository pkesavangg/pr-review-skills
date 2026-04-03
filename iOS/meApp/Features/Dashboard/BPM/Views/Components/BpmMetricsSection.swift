//
//  BpmMetricsSection.swift
//  meApp
//
//  BP-specific metric cards section below the graph.
//  Shows the three-reading average card and the two streak cards from the BP mock.
//

import SwiftUI

struct BpmMetricsSection: View {
    @ObservedObject var store: DashboardStore
    @State private var recentReadings: [BpmReadingDisplayData] = []
    @State private var streakCards: [MetricItem] = []

    private let streakColumns = [
        GridItem(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing),
        GridItem(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing)
    ]

    private var displayReadings: [BpmReadingDisplayData] {
        if !recentReadings.isEmpty {
            return recentReadings
        }

        let summaryOps = store.dataManager.getContinuousOperations(for: store.state.graph.selectedPeriod)
            .filter { $0.systolic != nil && $0.diastolic != nil }
        let fallbackOps = Array(summaryOps.suffix(BpmConstants.readingAverageCount).reversed())
        return fallbackOps.compactMap(BpmReadingDisplayData.init(summary:))
    }

    private var threeReadingAverage: ThreeReadingAverage? {
        if !displayReadings.isEmpty {
            let count = displayReadings.count
            let avgSys = Int(round(Double(displayReadings.map(\.systolic).reduce(0, +)) / Double(count)))
            let avgDia = Int(round(Double(displayReadings.map(\.diastolic).reduce(0, +)) / Double(count)))
            let avgPulse = Int(round(Double(displayReadings.map(\.pulse).reduce(0, +)) / Double(count)))
            return ThreeReadingAverage(
                systolic: avgSys,
                diastolic: avgDia,
                pulse: avgPulse,
                count: count,
                label: ThreeReadingAverage.displayLabel(for: count),
                classification: AhaPressureClass.classify(systolic: avgSys, diastolic: avgDia)
            )
        }
        return nil
    }

    var body: some View {
        VStack(spacing: .spacingSM) {
            if let average = threeReadingAverage {
                ThreeReadingAverageCard(average: average, recentReadings: displayReadings)
            }

            if !streakCards.isEmpty {
                LazyVGrid(columns: streakColumns, spacing: DashboardConstants.UIConstants.gridSpacing) {
                    ForEach(streakCards) { streak in
                        StreakCardView(
                            value: streak.value,
                            label: streak.label,
                            icon: streak.icon,
                            isEditMode: false,
                            isRemoved: false,
                            isDropTarget: false,
                            onToggleRemoval: {},
                            onDrop: { _, _ in false },
                            onDropTargetChanged: { _ in },
                            parentView: .dashboard
                        )
                    }
                }
            }
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingSM)
        .task {
            await loadRecentReadings()
            await loadBpmStreaks()
        }
        .onReceive(store.dashboardEntryService.entrySaved) { _ in
            Task {
                await loadRecentReadings()
                await loadBpmStreaks()
            }
        }
        .onReceive(store.dashboardEntryService.entryDeleted) { _ in
            Task {
                await loadRecentReadings()
                await loadBpmStreaks()
            }
        }
    }

    @MainActor
    private func loadRecentReadings() async {
        do {
            let entries = try await store.dashboardEntryService.getAllEntries()
            let bpmEntries = entries.filter {
                $0.entryType == EntryType.bpm.rawValue && $0.operationType == OperationType.create.rawValue
            }
            let sortedEntries = bpmEntries.sorted { $0.entryTimestamp > $1.entryTimestamp }
            let readingData = sortedEntries.compactMap(BpmReadingDisplayData.init(entry:))
            recentReadings = Array(readingData.prefix(BpmConstants.readingAverageCount))
        } catch {
            recentReadings = []
        }
    }

    @MainActor
    private func loadBpmStreaks() async {
        do {
            let streak = try await store.dashboardEntryService.getStreak(entryType: .bpm)
            streakCards = [
                MetricItem(
                    value: "\(streak.current)",
                    label: DashboardStrings.currentStreak,
                    unit: nil,
                    preLabel: nil,
                    icon: AppAssets.streak
                ),
                MetricItem(
                    value: "\(streak.max)",
                    label: DashboardStrings.longestStreak,
                    unit: nil,
                    preLabel: nil,
                    icon: AppAssets.longestStreak
                )
            ]
        } catch {
            streakCards = []
        }
    }
}
