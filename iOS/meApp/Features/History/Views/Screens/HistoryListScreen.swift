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
    
    // Prevent multiple simultaneous navigation
    @State private var isNavigating = false
    @State private var navigationTask: Task<Void, Never>?
    
    var body: some View {
      RoutingView(stack: $router.stack) {
          VStack(spacing: 0) {
              NavbarHeaderView<EmptyView, AnyView>(
                  title: HistoryListStrings.title,
                  trailingContent: {
                      AnyView(
                          Button {
                            store.handleExport()
                          } label: {
                            AppIconView(icon: AppAssets.export)
                                .foregroundColor(theme.statusIconPrimary)
                                .frame(width: 24, height: 24)
                                .opacity(store.isEmptyState ? 0.5 : 1.0)
                          }
                          .disabled(store.isEmptyState)
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
        .onDisappear {
            // Cancel any pending navigation task when view disappears
            navigationTask?.cancel()
            navigationTask = nil
        }
        .environmentObject(Theme.shared)
        .environmentObject(router)
        .environmentObject(store)
    }

    @ViewBuilder
    private var content: some View {
        ScrollView(showsIndicators: !store.isEmptyState) {
            if store.isEmptyState {
                ZStack {
                    Spacer().containerRelativeFrame([.horizontal, .vertical])
                    VStack {
                        NoEntryView {
                            tabViewModel.pendingSettingsNavigation = .addEditScales
                            tabViewModel.selectedTab = .settings
                            tabViewModel.settingsNavigationSourceTab = .history
                        }
                    }
                }
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(store.months, id: \.id) { month in
                        MonthSummaryItem(month: month)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                guard !isNavigating else { return }
                                isNavigating = true
                                store.setSelectedMonth(selectedMonth: month)
                                router.navigate(to: .historyMonthList(month: month))
                                
                                // Cancel any existing navigation task
                                navigationTask?.cancel()
                                
                                // Reset navigation flag after a short delay
                                navigationTask = Task {
                                    try? await Task.sleep(nanoseconds: 500_000_000) // 500ms
                                    await MainActor.run {
                                        isNavigating = false
                                    }
                                }
                            }
                            .background(theme.backgroundSecondary)
                    }
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

