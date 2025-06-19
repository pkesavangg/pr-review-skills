//
//  HistoryListScreen.swift
//  meApp
//
//  Created by Barath Chittibabu on 17/06/25.
//

import SwiftUI

/// Top-level screen that shows the monthly history summaries.
/// Uses `HistoryStore` for state and `MonthSummaryItem` for each row.
struct HistoryListScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject private var store = HistoryStore()
    @State private var selectedMonth: HistoryMonth?

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView<EmptyView, EmptyView>(
                title: HistoryListStrings.title,
                canShowBorder: true
            )
            .background(theme.backgroundPrimary)

            content
                .background(theme.backgroundSecondary)
                .edgesIgnoringSafeArea(.bottom)
        }
        .background(theme.backgroundSecondary)
        .onAppear {
            store.loadMonths()
        }
        .sheet(item: $selectedMonth) { month in
            HistoryMonthListScreen(month: month)
        }
        .environmentObject(Theme.shared)
    }

    @ViewBuilder
    private var content: some View {
       if store.isEmptyState {
            NoEntryView(
              onButtonTap: {
                // TODO: Navigate to Add Scale Screen
              }
            )
        } else {
          ScrollView {
            LazyVStack(spacing: 0) {
                ForEach(store.months, id: \.id) { month in
                    ZStack {
                        MonthSummaryItem(month: month)
                            .contentShape(Rectangle())
                    }
                    .onTapGesture {
                        store.selectMonth(month)
                        selectedMonth = month
                    }
                    .background(theme.backgroundSecondary)
                }
            }
          }
        }
    }
}

// MARK: - Preview -------------------------------------------------------

#if DEBUG
struct HistoryListScreen_Previews: PreviewProvider {
    static var previews: some View {
        HistoryListScreen()
            .themeable()
            .environmentObject(Theme.shared)
    }
}
#endif

