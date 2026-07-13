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
                  // Per Me.Health 2.0: always show the product-specific history title
                  // (Weight History / Blood Pressure / baby name), tinted by product
                  // (weight → blue, BP → green, baby → purple). The chevron/selector
                  // only appears when more than one product is available to switch between.
                  title: productTypeStore.selectedItem.historyTitle,
                  titleColor: theme.productAccentColor(for: productTypeStore.selectedItem.entryType),
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
              // Seed the store's on-screen flag so off-screen saves skip the full
              // months reload (MOB-1433 §5c).
              store.isHistoryScreenActive = tabViewModel.selectedTab == .history
          }
          .background(theme.backgroundSecondary)
          .screenAccessibilityRoot(AccessibilityID.historyScreenRoot)
          .onChange(of: tabViewModel.selectedTab) {
              guard tabViewModel.selectedTab != lastTabCheck else { return }
              lastTabCheck = tabViewModel.selectedTab
              // Track on-screen state so off-screen saves skip the full months reload (§5c).
              store.isHistoryScreenActive = tabViewModel.selectedTab == .history

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
                emptyStateContainer { weightEmptyState }
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(store.months, id: \.id) { month in
                        MonthSummaryItem(month: month)
                            .accessibilityIdentifier(AccessibilityID.historyMonthRow)
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
            await store.refreshAllEntries()
        }
    }

    @ViewBuilder
    private var bpContent: some View {
        ScrollView(showsIndicators: !store.bpMonths.isEmpty) {
            if store.bpMonths.isEmpty {
                emptyStateContainer { bpEmptyState }
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
        .refreshable {
            await store.refreshAllEntries()
        }
    }

    @ViewBuilder
    private var babyContent: some View {
        ScrollView(showsIndicators: !store.babyWeeks.isEmpty) {
            if store.babyWeeks.isEmpty {
                emptyStateContainer { babyEmptyState }
            } else {
                LazyVStack(spacing: 0) {
                    ForEach(store.babyWeeks) { week in
                        BabyWeekHeaderView(
                            weekNumber: week.weekNumber,
                            showBirthdayBalloon: week.containsBirthday
                        )

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
        .refreshable {
            await store.refreshAllEntries()
        }
    }

    // MARK: - Empty States (MOB-1220) -----------------------------------
    // History empty states per product & state. Once a device is paired the primary
    // CTA flips from ADD DEVICE to LOG MANUALLY. Illustrations are tinted per product
    // (weight → blue, BP → green, baby → purple).

    /// Centers an empty-state view within the scroll viewport.
    @ViewBuilder
    private func emptyStateContainer<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        ZStack {
            Spacer().containerRelativeFrame([.horizontal, .vertical])
            VStack { content() }
        }
    }

    @ViewBuilder
    private var weightEmptyState: some View {
        if store.hasPairedDeviceForCurrentProduct {
            NoEntryView(
                title: ProductTypeStrings.EmptyState.weightNoEntriesTitle,
                description: ProductTypeStrings.EmptyState.weightNoEntriesDescription,
                buttonTitle: ProductTypeStrings.EmptyState.logManually,
                iconAsset: AppAssets.history,
                iconTint: theme.weightScaleColor
            ) { navigateToManualEntry() }
        } else {
            NoEntryView(
                title: ProductTypeStrings.EmptyState.weightNoDeviceTitle,
                description: ProductTypeStrings.EmptyState.weightNoDeviceDescription,
                buttonTitle: ProductTypeStrings.EmptyState.addDevice,
                iconAsset: AppAssets.weightScaleIcon,
                iconTint: theme.weightScaleColor
            ) { navigateToAddDevice() }
        }
    }

    @ViewBuilder
    private var bpEmptyState: some View {
        if store.hasPairedDeviceForCurrentProduct {
            NoEntryView(
                title: ProductTypeStrings.EmptyState.bpNoEntriesTitle,
                description: ProductTypeStrings.EmptyState.bpNoEntriesDescription,
                buttonTitle: ProductTypeStrings.EmptyState.logManually,
                iconAsset: AppAssets.history,
                iconTint: theme.bpmColor
            ) { navigateToManualEntry() }
        } else {
            NoEntryView(
                title: ProductTypeStrings.EmptyState.bpNoDeviceTitle,
                description: ProductTypeStrings.EmptyState.bpNoDeviceDescription,
                buttonTitle: ProductTypeStrings.EmptyState.addDevice,
                iconAsset: AppAssets.bpmIcon,
                iconTint: theme.bpmColor
            ) { navigateToAddDevice() }
        }
    }

    @ViewBuilder
    private var babyEmptyState: some View {
        if productTypeStore.selectedItem.isPendingBaby {
            // State 1 — no baby profile yet.
            NoEntryView(
                title: ProductTypeStrings.BabyEmptyState.title,
                description: ProductTypeStrings.BabyEmptyState.historyDescription,
                buttonTitle: ProductTypeStrings.BabyEmptyState.addABaby,
                iconAsset: AppAssets.babyHeadIcon,
                iconTint: theme.babyScaleColor
            ) { tabViewModel.navigateToSettings(route: .myKids, sourceTab: .history) }
        } else if store.hasPairedDeviceForCurrentProduct {
            // State 3 — profile + baby scale paired, no measurement yet.
            NoEntryView(
                title: ProductTypeStrings.EmptyState.babyNoEntriesTitle,
                description: ProductTypeStrings.EmptyState.babyNoEntriesDescription,
                buttonTitle: ProductTypeStrings.EmptyState.logManually,
                iconAsset: AppAssets.history,
                iconTint: theme.babyScaleColor
            ) { navigateToManualEntry() }
        } else {
            // State 2 — profile exists, no baby scale paired. ADD DEVICE + LOG MANUALLY.
            NoEntryView(
                title: ProductTypeStrings.EmptyState.babyNoDeviceTitle,
                description: ProductTypeStrings.EmptyState.babyNoDeviceDescription,
                buttonTitle: ProductTypeStrings.EmptyState.addDevice,
                iconAsset: AppAssets.babyAppIcon,
                iconTint: theme.babyScaleColor,
                secondaryButtonTitle: ProductTypeStrings.EmptyState.logManually,
                onSecondaryButtonTap: { navigateToManualEntry() },
                onButtonTap: { navigateToAddDevice() }
            )
        }
    }

    /// Deep-links to the Settings pairing flow (ADD DEVICE).
    private func navigateToAddDevice() {
        tabViewModel.navigateToSettings(route: .addEditScales, sourceTab: .history)
    }

    /// Switches to the Manual Entry tab (LOG MANUALLY).
    private func navigateToManualEntry() {
        tabViewModel.selectTab(.entry)
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
