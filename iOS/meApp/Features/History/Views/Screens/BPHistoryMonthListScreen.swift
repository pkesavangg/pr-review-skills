//
//  BPHistoryMonthListScreen.swift
//  meApp
//

import SwiftUI

/// Screen that displays individual blood pressure entries for a specific month.
struct BPHistoryMonthListScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var historyStore: HistoryStore
    @EnvironmentObject var router: Router<HistoryRoute>

    let month: BPHistoryMonth
    @State private var openItemID: UUID?
    @State private var entryToEdit: BPHistoryEntry?

    private var title: String {
        guard let firstEntry = historyStore.bpEntries.first else { return month.id }
        return DateTimeTools.getMonthDayYearShort(firstEntry.entryTimestamp)
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AppIconView, AnyView>(
                title: title,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() }
            )
            .background(theme.backgroundPrimary)

            content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .navigationBarBackButtonHidden(true)
        .onAppear {
            historyStore.selectBPMonth(month)
        }
        .onChange(of: historyStore.bpEntries) { _, entries in
            if entries.isEmpty { router.navigateBack() }
        }
        .onDisappear {
            historyStore.expandedBPEntries.removeAll()
            historyStore.resetSelectedBPMonth()
        }
        .sheet(item: $entryToEdit) { entry in
            BPHistoryEditSheet(entry: entry)
                .environmentObject(historyStore)
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(historyStore.bpEntries) { entry in
                    BPHistoryEntryItem(
                        entry: entry,
                        isExpanded: historyStore.expandedBPEntries.contains(entry.id.uuidString),
                        onTap: {
                            toggleEntry(entry)
                        },
                        onDelete: {
                            historyStore.showDeleteBPEntryAlert(entry: entry)
                        },
                        onEditNotes: {
                            entryToEdit = entry
                        },
                        openItemID: $openItemID
                    )
                    .id(entry.id)
                }
            }
        }
        // A DragGesture here (even simultaneous) can block the ScrollView's own pan
        // on iOS 18+, so close any open swipe row via the scroll phase instead.
        .onScrollPhaseChange { _, newPhase in
            if newPhase != .idle, openItemID != nil {
                withAnimation { openItemID = nil }
            }
        }
    }

    // MARK: - Private

    private func toggleEntry(_ entry: BPHistoryEntry) {
        let id = entry.id.uuidString
        withAnimation(.easeInOut(duration: 0.25)) {
            if historyStore.expandedBPEntries.contains(id) {
                historyStore.expandedBPEntries.remove(id)
            } else {
                historyStore.expandedBPEntries.insert(id)
            }
        }
    }
}
