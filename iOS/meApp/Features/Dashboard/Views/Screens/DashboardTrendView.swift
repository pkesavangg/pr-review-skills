//
//  DashboardTrendView.swift
//  meApp
//

import SwiftUI

struct DashboardTrendView<TopContent: View, ChartFooter: View>: View {
    @ObservedObject var dashboardStore: DashboardStore
    @EnvironmentObject private var tabViewModel: BottomTabBarViewModel
    @Environment(\.appTheme) private var theme
    @State private var localSelectedPeriod: TimePeriod = DefaultGraphPeriodPreference.fallback
    @State private var segmentSwitchTask: Task<Void, Never>?

    private let topContent: () -> TopContent
    private let chartFooter: () -> ChartFooter

    /// Footer shown when the selected product has no entries yet. Two variants (MOB-1245):
    /// a pending baby selection (no profile) shows the "No babies added yet" / ADD A BABY card;
    /// every other empty state shows the "connect a device" / CONNECT DEVICE card.
    @ViewBuilder
    private func emptyStateFooter() -> some View {
        if dashboardStore.isPendingBabySelection {
            noBabyFooter()
        } else {
            noEntriesFooter()
        }
    }

    @ViewBuilder
    private func noEntriesFooter() -> some View {
        VStack(spacing: .spacingMD) {
            Text(DashboardStrings.noEntriesMessage)
                .fontOpenSans(.body2)
                .foregroundStyle(theme.textBody)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)
            ButtonView(
                text: DashboardStrings.connectDevice,
                type: .filledPrimary,
                size: .large,
                isDisabled: false
            ) {
                tabViewModel.pendingSettingsNavigation = .addEditScales
                tabViewModel.selectedTab = .settings
                tabViewModel.settingsNavigationSourceTab = .dash
            }
            .padding(.horizontal, .spacingLG)
        }
        .padding(.vertical, .spacingMD)
        .frame(maxWidth: .infinity)
    }

    @ViewBuilder
    private func noBabyFooter() -> some View {
        VStack(spacing: .spacingMD) {
            Image(AppAssets.babyHeadIcon)
                .resizable()
                .renderingMode(.template)
                .scaledToFit()
                .frame(width: 56, height: 56)
                .foregroundStyle(theme.babyScaleColor)
                .accessibilityHidden(true)
            VStack(spacing: .spacingXS) {
                Text(BabyDashboardStrings.noBabiesTitle)
                    .fontOpenSans(.heading4)
                    .foregroundStyle(theme.textHeading)
                    .multilineTextAlignment(.center)
                Text(BabyDashboardStrings.noBabiesSubtitle)
                    .fontOpenSans(.body2)
                    .foregroundStyle(theme.textBody)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // Combine only the title + subtitle into one VoiceOver element; the
            // ADD A BABY button stays a directly-activatable button of its own
            // (mirrors noEntriesFooter()).
            .accessibilityElement(children: .combine)
            .padding(.horizontal, .spacingLG)
            ButtonView(
                text: BabyDashboardStrings.addBaby,
                type: .filledPrimary,
                size: .large,
                isDisabled: false
            ) {
                tabViewModel.navigateToSettings(route: .addBaby, sourceTab: .dash)
            }
            .padding(.horizontal, .spacingLG)
        }
        .padding(.vertical, .spacingMD)
        .frame(maxWidth: .infinity)
    }

    init(
        dashboardStore: DashboardStore,
        @ViewBuilder topContent: @escaping () -> TopContent,
        @ViewBuilder chartFooter: @escaping () -> ChartFooter
    ) {
        self.dashboardStore = dashboardStore
        self.topContent = topContent
        self.chartFooter = chartFooter
    }

    var body: some View {
        ZStack {
            VStack(alignment: .leading, spacing: 0) {
                topContent()
                GraphView(dashboardStore: dashboardStore)
                chartFooter()
                SegmentedButtonView(
                    segments: TimePeriod.allCases,
                    // MA-3839: scale all period tabs (Week/Month/Year/Total) to one shared
                    // font size so they stay uniform and don't truncate under Dynamic Type.
                    selectedSegment: $localSelectedPeriod,
                    useUniformFontScaling: true
                )
                .padding(.vertical, .spacingSM)
                .padding(.horizontal, 15)
                if !dashboardStore.state.data.hasAnyEntries ||
                   (dashboardStore.isBabySelection && !dashboardStore.hasBabyEntries) {
                    emptyStateFooter()
                }
            }
            .padding(.top, .spacingMD)
            .background(theme.textInverse)
            .edgesIgnoringSafeArea(.all)
            .zIndex(1)
        }
        .onAppear {
            localSelectedPeriod = dashboardStore.state.graph.selectedPeriod
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            if localSelectedPeriod != newValue {
                localSelectedPeriod = newValue
            }
        }
        .onChange(of: localSelectedPeriod) { _, newValue in
            guard newValue != dashboardStore.state.graph.selectedPeriod else { return }
            // MA-3837: reset to the latest entry on a period switch (anchorDate = nil →
            // showingLatest) instead of anchoring to the previous view's visible midpoint,
            // so the chart and selection land on the most recent reading.
            // Dispatch outside the current animation transaction so the segment button
            // highlight animates without being blocked by chart recalculation.
            // MOB-243: debounce rapid taps — cancel any in-flight recalculation and restart,
            // so only the final selected segment triggers the expensive chart update.
            segmentSwitchTask?.cancel()
            segmentSwitchTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 120_000_000)
                guard !Task.isCancelled else { return }
                dashboardStore.chartManager.updateSelectedPeriod(newValue)
            }
        }
    }
}

extension DashboardTrendView where ChartFooter == EmptyView {
    init(
        dashboardStore: DashboardStore,
        @ViewBuilder topContent: @escaping () -> TopContent
    ) {
        self.init(dashboardStore: dashboardStore, topContent: topContent) { EmptyView() }
    }
}
