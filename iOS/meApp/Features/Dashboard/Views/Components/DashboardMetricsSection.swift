//
//  DashboardContentSection.swift
//  meApp
//
//  Extracted composite section from DashboardScreen
//

import SwiftUI

struct DashboardMetricsSection: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var store: DashboardStore
    let parentView: DashboardMetricsParentView
    @Binding var openMetricInfoWithoutSelection: MetricInfoWrapper?
    
    var body: some View {
        VStack(spacing: 0) {
            if parentView == .R4ScaleSetup {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(DashboardStrings.customizeDashboardTitle)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    VStack {
                        Text(DashboardStrings.customizeDashboardSubtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textHeading)
                    }
                    
                }
            }

            // Show skeleton while loading body metrics, otherwise show actual metrics
            if store.shouldShowBodyMetricsSkeleton {
                skeletonMetricsGrid()
            } else if store.shouldShowBodyMetrics || !store.metricsToShow.isEmpty {
                metricsGridSection()
            }
            
            // Show divider if both body metrics and progress metrics are present
            if store.shouldShowDivider {
                dividerSection()
            }

            // Show skeleton while loading progress metrics, otherwise show actual progress metrics
            if store.shouldShowProgressMetricsSkeleton {
                skeletonProgressMetrics(hasContentAbove: store.skeletonProgressMetricsHasContentAbove)
            } else if store.shouldShowGoalStreakSection {
                goalStreakSection()
            }
            
        }
        .onAppear {
            if parentView == .R4ScaleSetup {
                // Force edit mode in Scale Setup context
                if !store.ui.isEditMode {
                    store.ui.isEditMode = true
                }
                store.gridEditingManager.syncRemovalStateFromMetricsManager()
                store.objectWillChange.send()
            }
        }
        .onChange(of: store.metricsManager.state.metrics) { _, _ in
            if parentView == .R4ScaleSetup {
                store.gridEditingManager.debouncedSyncRemovalState()
            }
        }
        .onChange(of: store.metricsManager.state.activeMetricsCount) { _, _ in
            if parentView == .R4ScaleSetup {
                store.gridEditingManager.debouncedSyncRemovalState()
            }
        }
        .onChange(of: parentView) { _, newValue in
            if newValue == .R4ScaleSetup {
                if !store.ui.isEditMode {
                    store.ui.isEditMode = true
                }
            }
        }
        .onChange(of: store.ui.isEditMode) { _, newValue in
            if parentView == .R4ScaleSetup && newValue == false {
                // Keep edit mode on while customizing from Scale Setup
                store.ui.isEditMode = true
            }
        }
    }
    
    private func metricsGridSection() -> some View {
        Group {
            MetricGridUIKitView(parentView: parentView, store: store) { _ in
                // Long press on any metric should directly open edit dashboard mode
                if !store.state.ui.isEditMode {
                    store.gridEditingManager.toggleEditMode()
                }
            }
            .frame(minHeight: DevicePlatform.isTablet ? 74 : 100)
            .padding(.top, .spacingSM)
            .id(store.ui.gridLayoutId)
            .animation(.easeInOut(duration: 0.3), value: store.ui.gridLayoutId)
        }
    }
    
    private func dividerSection() -> some View {
        Group {
            Divider()
                .foregroundColor(theme.statusUtilityPrimary)
                .padding(.horizontal, .spacingLG)
                .padding(.top, .spacingSM)
        }
    }
    
    private func goalStreakSection() -> some View {
        Group {
            GoalStreakGridUIKitView(parentView: parentView, store: store)
                .frame(minHeight: store.shouldShowGoalCardOrStreaks ? 100 : 200)
                .padding(.top, store.ui.isGoalCardRemoved ? 0 : .spacingXS)
                .id(store.ui.gridLayoutId)
                .animation(.easeInOut(duration: 0.3), value: store.ui.gridLayoutId)
        }
    }
    
    // MARK: - Skeleton Views
    
    private func skeletonMetricsGrid() -> some View {
        let columnCount = store.metricsManager.getMetricGridColumnCount(for: store.effectiveDashboardType)
        let skeletonCount = store.effectiveDashboardType == .dashboard12 ? 12 : 4
        
        return LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing), count: columnCount),
            spacing: DashboardConstants.UIConstants.gridSpacing
        ) {
            ForEach(0..<skeletonCount, id: \.self) { _ in
                SkeletonMetricCardView(dashboardType: store.effectiveDashboardType)
            }
        }
        .frame(minHeight: DevicePlatform.isTablet ? 74 : 100)
        .padding(.top, .spacingLG)
        .padding(.horizontal, .spacingSM)
    }
    
    private func skeletonProgressMetrics(hasContentAbove: Bool) -> some View {
        let columns = DevicePlatform.isTablet ? 4 : 2
        let topInset: CGFloat = store.ui.isGoalCardRemoved ? .spacingLG : .spacingSM
        let extraTopPadding: CGFloat = store.ui.isGoalCardRemoved ? 0 : .spacingXS

        return VStack(spacing: .spacingLG) {
            SkeletonGoalCardView()
            LazyVGrid(
                columns: Array(repeating: .init(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing), count: columns),
                spacing: .spacingLG
            ) {
                ForEach(0..<6, id: \.self) { _ in
                    SkeletonStreakCardView(parentView: parentView)
                }
            }
        }
        .padding(.top, topInset + extraTopPadding)
        .padding(.horizontal, .spacingSM)
        .padding(.bottom, .spacingLG)
    }
}

#Preview("DashboardContentSection") {
    DashboardMetricsSection(store: DashboardStore(), parentView: .dashboard, openMetricInfoWithoutSelection: .constant(nil))
}
