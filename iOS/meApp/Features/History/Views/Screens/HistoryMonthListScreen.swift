//
//  HistoryMonthListScreen.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

/// Screen that displays a list of history entries for a specific month
/// Uses HistoryEntryItem components and integrates with HistoryStore
struct HistoryMonthListScreen: View {
    @Environment(\.appTheme) private var theme
    @Environment(\.dismiss) private var dismiss
    @StateObject private var historyStore = HistoryStore()
    @State private var selectedEntry: Entry?
    @State private var selectedMetric: BodyMetric?

    let month: HistoryMonth

    // Computed Properties

    private var title: String {
        let firstEntry = historyStore.entries.first
        return DateTimeTools.getMonthDayYearShort(firstEntry?.entryTimestamp ?? "")
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
          NavbarHeaderView<Image, Image>(
                title: title,
                leadingContent: { Image(AppAssets.xmark) },
                onLeadingTap: { dismiss() }
            )
            .background(theme.backgroundPrimary)

             content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .onAppear {
            historyStore.selectMonth(month)
        }
        .sheet(item: $selectedEntry) { entry in
            ScaleMetricsView(entry: entry, selectedMetric: selectedMetric ?? .bmi)
        }
    }

    @ViewBuilder
    private var content: some View {
          if historyStore.isEmptyState {
              NoEntryView(
                onButtonTap: {

                }
              )

          } else {
              ScrollView {
                  LazyVStack(spacing: 0) {
                      ForEach(historyStore.entries.sorted {
                          DateTimeTools.getTimestamp($0.entryTimestamp) > DateTimeTools.getTimestamp($1.entryTimestamp)
                      }, id: \.id) { entry in
                          HistoryEntryItem(
                              entry: entry,
                              isExpanded: historyStore.expandedEntries.contains(entry.id.uuidString),
                              onTap: {
                                  historyStore.toggleEntry(entry)
                              },
                              onDelete: {
                                  historyStore.deleteEntry(entry)
                              },
                              onMetricTap: { entry, metric in
                                  selectedEntry = entry
                                  selectedMetric = metric
                              }
                          )
                      }
                  }
              }
              .refreshable {
                  await historyStore.refreshSelectedMonth()
              }
          }
    }
}

// MARK: - Preview

#if DEBUG
struct HistoryMonthListScreen_Previews: PreviewProvider {
    static var previews: some View {
        let month = HistoryMonth(
            id: "2025-12",
            weight: 148.6,
            entryTimestamp: "2025-12",
            count: 5,
            weights: "",
            change: "",
            bodyFat: nil,
            muscleMass: nil,
            water: nil,
            bmi: nil,
            date: nil,
            time: nil,
            month: "12",
            year: "2025",
            min: nil,
            max: nil
        )

        HistoryMonthListScreen(month: month)
            .themeable()
            .environmentObject(Theme.shared)
    }
}
#endif


