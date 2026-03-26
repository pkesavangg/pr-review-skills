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
    private let entryService = EntryService.shared

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
        }
        .padding(.horizontal, .spacingSM)
        .padding(.vertical, .spacingSM)
        .task {
            await loadRecentReadings()
        }
        .onReceive(entryService.entrySaved) { _ in
            Task { await loadRecentReadings() }
        }
        .onReceive(entryService.entryDeleted) { _ in
            Task { await loadRecentReadings() }
        }
    }

    @MainActor
    private func loadRecentReadings() async {
        do {
            let entries = try await entryService.getAllEntries()
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
}

struct BpmReadingDisplayData: Identifiable {
    let id: UUID
    let systolic: Int
    let diastolic: Int
    let pulse: Int
    let timestamp: String
    let classification: AhaPressureClass

    init?(entry: Entry) {
        guard let systolic = entry.scaleEntry?.systolic,
              let diastolic = entry.scaleEntry?.diastolic else {
            return nil
        }

        self.id = entry.id
        self.systolic = systolic
        self.diastolic = diastolic
        self.pulse = entry.scaleEntryMetric?.pulse ?? 0
        self.timestamp = entry.entryTimestamp
        self.classification = AhaPressureClass.classify(systolic: systolic, diastolic: diastolic)
    }

    init?(summary: BathScaleWeightSummary) {
        guard let systolic = summary.systolic,
              let diastolic = summary.diastolic else {
            return nil
        }

        self.id = summary.id
        self.systolic = Int(round(systolic))
        self.diastolic = Int(round(diastolic))
        self.pulse = Int(round(summary.pulse ?? 0))
        self.timestamp = summary.entryTimestamp
        self.classification = AhaPressureClass.classify(
            systolic: Int(round(systolic)),
            diastolic: Int(round(diastolic))
        )
    }

    var formattedDate: String {
        guard let date = DateTimeTools.parse(timestamp) else { return timestamp }
        return DateTimeTools.formatter("MMM d, yyyy").string(from: date)
    }
}
