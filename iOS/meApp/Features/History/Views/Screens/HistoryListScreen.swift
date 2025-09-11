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
    @StateObject private var router = Router<HistoryRoute>()
    @StateObject private var store = HistoryStore()
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    
    // iOS 17 fix: Prevent duplicate lifecycle calls
    @State private var hasAppeared = false
    @State private var lastTabCheck: BottomTab? = nil
    
    var body: some View {
      RoutingView(stack: $router.stack) {
          VStack(spacing: 0) {
              NavbarHeaderView<EmptyView, AnyView>(
                  title: HistoryListStrings.title,
                  trailingContent: {
                      AnyView(
                          Group {
                              if !store.isEmptyState {
                                  Button {
                                      store.handleExport()
                                  } label: {
                                      AppIconView(icon: AppAssets.export)
                                          .foregroundColor(theme.statusIconPrimary)
                                          .frame(width: 24, height: 24)
                                  }
                              } else {
                                  EmptyView()
                              }
                          }
                      )
                  },
                  canShowBorder: true
              )
              .background(theme.backgroundPrimary)

              content
                  .background(theme.backgroundSecondary)
                  .edgesIgnoringSafeArea(.bottom)
          }
          .background(theme.backgroundSecondary)
          .onChange(of: tabViewModel.selectedTab) {
              guard tabViewModel.selectedTab != lastTabCheck else { return }
              lastTabCheck = tabViewModel.selectedTab
              
              if tabViewModel.selectedTab == .history {
                  Task {
                      try? await Task.sleep(nanoseconds: 50_000_000) // 50ms
                      if tabViewModel.selectedTab == .history {
                          store.loadMonths()
                      }
                  }
              }
          }
        }
        .environmentObject(Theme.shared)
        .environmentObject(router)
        .environmentObject(store)
    }

    @ViewBuilder
    private var content: some View {
       if store.isEmptyState {
            NoEntryView(
              onButtonTap: {
                  tabViewModel.pendingSettingsNavigation = .addEditScales
                  tabViewModel.selectedTab = .settings
              }
            )
        } else {
          ScrollView {
            LazyVStack(spacing: 0) {

                ForEach(store.months, id: \.id) { month in
                    MonthSummaryItem(month: month)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            store.setSelectedMonth(selectedMonth: month)
                            router.navigate(to: .historyMonthList(month: month))
                        }
                        .background(theme.backgroundSecondary)
                }
            }
          }
          .refreshable {
              Task {
                  await store.refreshAllEntries()
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

