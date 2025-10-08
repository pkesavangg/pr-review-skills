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
    @EnvironmentObject var historyStore: HistoryStore
    @EnvironmentObject var router: Router<HistoryRoute>
    @State private var selectedEntry: Entry?
    @State private var selectedMetric: BodyMetric?
    @State private var showDeleteAlert = false
    @State private var entryToDelete: Entry? = nil
    @State private var openItemID: UUID? = nil
    @State private var isOnboardingComplete: Bool = false
    
    let month: HistoryMonth
    
    // Computed Properties
    
    private var title: String {
        let firstEntry = historyStore.entries.first
        return DateTimeTools.getMonthDayYearShort(firstEntry?.entryTimestamp ?? "")
    }
    
    // MARK: - Private Methods
    
    /// Toggle expand/collapse for an entry row.
    private func toggleEntry(_ entry: Entry) {
        let id = entry.id.uuidString
        
        // iOS 17 fix: Immediate toggle for better responsiveness on repeated navigation
        if historyStore.expandedEntries.contains(id) {
            historyStore.expandedEntries.remove(id)
        } else {
            historyStore.expandedEntries.insert(id)
        }
    }
    
    // MARK: - Body
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AnyView, AnyView>(
                title: title,
                leadingContent: { AnyView(Image(AppAssets.chevronLeft)) },
                onLeadingTap: { router.navigateBack() }
            )
            .background(theme.backgroundPrimary)
            
            content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .navigationBarBackButtonHidden(true)
        .onAppear(perform: {
            if !isOnboardingComplete {
                Task {
                    await self.historyStore.loadEntries(for: month)
                    isOnboardingComplete = true
                }
            }
        })
        .onChange(of: historyStore.entries) { _, entries in
            if entries.isEmpty {
                dismiss()
            }
        }
        .onDisappear(perform: {
            historyStore.expandedEntries.removeAll() // Clear expanded state when leaving
            historyStore.resetSelectedMonth()
        })
        .refreshable {
            // Only allow refresh when no swipe actions are open
            guard openItemID == nil else { return }
            await historyStore.refreshAllEntries()
        }
        .sheet(item: $selectedEntry) { entry in
            ScaleMetricsView(entry: entry, selectedMetric: selectedMetric ?? .bmi)
        }
    }
    
    @ViewBuilder
    private var content: some View {
        
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(historyStore.entries, id: \.id) { entry in
                    HistoryEntryItem(
                        entry: entry,
                        isExpanded: historyStore.expandedEntries.contains(entry.id.uuidString),
                        onTap: {
                            toggleEntry(entry)
                        },
                        onDelete: {
                            historyStore.showDeleteEntryAlert(entry: entry)
                        },
                        onMetricTap: { entry, metric in
                            selectedEntry = entry
                            selectedMetric = metric
                        },
                        openItemID: $openItemID
                    )
                    .id(entry.id) // Use entry ID for stable identity
                    // Removed dynamic id to preserve view identity and avoid full view rebuild
                }
            }
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 10)
                .onChanged { _ in
                    if openItemID != nil {
                        withAnimation {
                            openItemID = nil
                        }
                    }
                }
        )
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
