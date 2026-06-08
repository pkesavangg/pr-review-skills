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
    @ObservedObject private var productTypeStore = ProductTypeStore.shared
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @State private var isProductTypeSelectorPresented = false

    // iOS 17 fix: Prevent duplicate lifecycle calls
    @State private var hasAppeared = false
    @State private var lastTabCheck: BottomTab?
    
    // Prevent multiple simultaneous navigation
    @State private var isNavigating = false
    @State private var navigationTask: Task<Void, Never>?
    
    var body: some View {
      RoutingView(stack: $router.stack) {
          VStack(spacing: 0) {
              NavbarHeaderView<EmptyView, AnyView>(
                  title: productTypeStore.availableItems.count > 1
                      ? productTypeStore.selectedItem.historyTitle
                      : HistoryListStrings.title,
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
                          .accessibilityIdentifier(AccessibilityID.historyDownloadButton)
                      )
                  },
                  onTitleTap: productTypeStore.availableItems.count > 1 ? {
                      isProductTypeSelectorPresented = true
                  } : nil,
                  canShowBorder: true,
                  canShowTitleChevron: productTypeStore.availableItems.count > 1
              )
              .background(theme.backgroundPrimary)
              .sheet(isPresented: $isProductTypeSelectorPresented) {
                  ProductTypeSelectorSheet(
                      store: productTypeStore,
                      isPresented: $isProductTypeSelectorPresented,
                      title: ProductTypeStrings.myHistory
                  )
              }

              content
                  .background(theme.backgroundSecondary)
                  .edgesIgnoringSafeArea(.bottom)
          }
          .onAppear {
              // Register reselect handler to pop to root when history tab is tapped
              tabViewModel.registerReselectHandler(for: .history) {
                  router.navigateToRoot()
              }
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
        if store.isBabyMode {
            babyContent
        } else if store.isBloodPressureMode {
            bpContent
        } else {
            weightContent
        }
    }

    @ViewBuilder
    private var weightContent: some View {
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

                                navigationTask?.cancel()
                                navigationTask = Task {
                                    try? await Task.sleep(nanoseconds: 500_000_000)
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

    @ViewBuilder
    private var bpContent: some View {
        ScrollView(showsIndicators: !store.bpMonths.isEmpty) {
            if store.bpMonths.isEmpty {
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
                    ForEach(store.bpMonths) { month in
                        BPMonthSummaryItem(month: month)
                            .contentShape(Rectangle())
                            .onTapGesture {
                                guard !isNavigating else { return }
                                isNavigating = true
                                router.navigate(to: .bpHistoryMonthList(month: month))

                                navigationTask?.cancel()
                                navigationTask = Task {
                                    try? await Task.sleep(nanoseconds: 500_000_000)
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
    }

    @ViewBuilder
    private var babyContent: some View {
        ScrollView(showsIndicators: !store.babyWeeks.isEmpty) {
            if store.babyWeeks.isEmpty {
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
                    ForEach(store.babyWeeks) { week in
                        BabyWeekHeaderView(weekNumber: week.weekNumber)

                        ForEach(week.days) { day in
                            BabyDaySummaryItem(day: day)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    guard !isNavigating else { return }
                                    isNavigating = true
                                    router.navigate(to: .babyHistoryDayList(day: day))

                                    navigationTask?.cancel()
                                    navigationTask = Task {
                                        try? await Task.sleep(nanoseconds: 500_000_000)
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
