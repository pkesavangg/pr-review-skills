//
//  BabyHistoryDayListScreen.swift
//  meApp
//

import SwiftUI

/// Screen that displays individual baby entries for a specific day.
struct BabyHistoryDayListScreen: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var historyStore: HistoryStore
    @EnvironmentObject var router: Router<HistoryRoute>

    let day: BabyHistoryDay
    @State private var openItemID: UUID?
    @State private var entryToEdit: BabyHistoryEntry?

    private var title: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        guard let date = formatter.date(from: day.id) else { return day.id }
        formatter.dateFormat = "M/d/yy"
        return formatter.string(from: date)
    }

    // MARK: - Body

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<AppIconView, AnyView>(
                title: title,
                titleLeadingContent: day.isBirthday ? AnyView(BirthdayBalloonBadge()) : nil,
                leadingContent: { AppIconView(icon: AppAssets.chevronLeft) },
                onLeadingTap: { router.navigateBack() },
                leadingAccessibilityID: AccessibilityID.babyHistoryDayBackButton
            )
            .background(theme.backgroundPrimary)

            content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .screenAccessibilityRoot(AccessibilityID.babyHistoryDayListScreenRoot)
        .navigationBarBackButtonHidden(true)
        .onAppear {
            historyStore.selectBabyDay(day)
        }
        .onChange(of: historyStore.babyEntries) { _, entries in
            if entries.isEmpty { router.navigateBack() }
        }
        .onDisappear {
            historyStore.expandedBabyEntries.removeAll()
            historyStore.resetSelectedBabyDay()
        }
        .sheet(item: $entryToEdit) { entry in
            BabyHistoryEditSheet(entry: entry)
                .environmentObject(historyStore)
        }
    }

    @ViewBuilder
    private var content: some View {
        ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(historyStore.babyEntries) { entry in
                    BabyHistoryEntryItem(
                        entry: entry,
                        isExpanded: historyStore.expandedBabyEntries.contains(entry.id.uuidString),
                        onTap: {
                            toggleEntry(entry)
                        },
                        onDelete: {
                            historyStore.showDeleteBabyEntryAlert(entry: entry)
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

    private func toggleEntry(_ entry: BabyHistoryEntry) {
        let id = entry.id.uuidString
        withAnimation(.easeInOut(duration: 0.25)) {
            if historyStore.expandedBabyEntries.contains(id) {
                historyStore.expandedBabyEntries.remove(id)
            } else {
                historyStore.expandedBabyEntries.insert(id)
            }
        }
    }
}
