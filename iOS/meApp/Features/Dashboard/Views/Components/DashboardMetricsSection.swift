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
                VStack(alignment: .leading, spacing: .spacingXS){
                    Text(DashboardStrings.customizeDashboardTitle)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                    VStack{
                        Text(DashboardStrings.customizeDashboardSubtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textHeading)
                    }
                    
                }
            }

            // Show skeleton while loading body metrics, otherwise show actual metrics
            if store.shouldShowBodyMetricsSkeleton {
                skeletonMetricsGrid()
            } else if store.shouldShowBodyMetrics {
                metricsGridSection()
            }
            
            // Show divider if both body metrics and progress metrics are present
            if store.shouldShowDivider {
                dividerSection()
            }

            // Show skeleton while loading progress metrics, otherwise show actual progress metrics
            if store.shouldShowProgressMetricsSkeleton {
                skeletonProgressMetrics(hasContentAbove: store.skeletonProgressMetricsHasContentAbove)
                    .padding(.horizontal, .spacingSM)
            } else if store.shouldShowGoalStreakSection {
                goalStreakSection()
            }
            
        }
        .onAppear {
            if parentView == .R4ScaleSetup {
                // Force edit mode in Scale Setup context
                if !store.state.ui.isEditMode {
                    store.state.ui.isEditMode = true
                }
                // In R4 setup, ensure all 12 metrics are visible and active regardless of current dashboard type or API state
                if store.metricsManager.state.metrics.count < 12 || store.effectiveDashboardType == .dashboard4 {
                    store.metricsManager.setupInitialMetrics(forceShowAll: true)
                }
                store.metricsManager.resetActiveMetricsCountToShowAll()
                store.syncRemovalStateFromMetricsManager()
                store.objectWillChange.send()
            }
        }
        .onChange(of: parentView) { _, newValue in
            if newValue == .R4ScaleSetup {
                if !store.state.ui.isEditMode {
                    store.state.ui.isEditMode = true
                }
            }
        }
        .onChange(of: store.state.ui.isEditMode) { _, newValue in
            if parentView == .R4ScaleSetup && newValue == false {
                // Keep edit mode on while customizing from Scale Setup
                store.state.ui.isEditMode = true
            }
        }
    }
    
    private func metricsGridSection() -> some View {
        Group {
            MetricGridUIKitView(parentView: parentView, store: store, onMetricLongPress: { label in
                store.state.ui.selectedMetricLabel = label
                openMetricInfoWithoutSelection = MetricInfoWrapper(metricLabel: label)
            })
            .frame(minHeight: DevicePlatform.isTablet ? 74 : 100)
            .padding(.top, .spacingSM)
            .id(store.state.ui.gridLayoutId)
            .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
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
                .padding(.top, store.state.ui.isGoalCardRemoved ? 0 : .spacingXS)
                .id(store.state.ui.gridLayoutId)
                .animation(.easeInOut(duration: 0.3), value: store.state.ui.gridLayoutId)
        }
    }
    
    // MARK: - Skeleton Views
    
    private func skeletonMetricsGrid() -> some View {
        let columnCount = store.metricsManager.getMetricGridColumnCount(for: store.effectiveDashboardType)
        let skeletonCount = store.effectiveDashboardType == .dashboard12 ? 12 : 4
        
        return LazyVGrid(
            columns: Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing), count: columnCount),
            spacing: DashboardConstants.UI.gridSpacing
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
        VStack{
            // Skeleton goal card
            SkeletonGoalCardView()
            
            // Skeleton streak cards grid
            let streakColumnCount = DashboardConstants.UI.streakGridColumns
            LazyVGrid(
                columns: Array(repeating: GridItem(.flexible(), spacing: DashboardConstants.UI.gridSpacing/2), count: streakColumnCount),
                spacing: DashboardConstants.UI.gridSpacing/2
            ) {
                ForEach(0..<6, id: \.self) { _ in
                    SkeletonStreakCardView(parentView: parentView)
                }
            }
        }
        .padding(.top, .spacingSM)
        .padding(.horizontal, .spacingXS)
    }
}

#Preview("DashboardContentSection") {
    DashboardMetricsSection(store: DashboardStore(), parentView: .dashboard, openMetricInfoWithoutSelection: .constant(nil))
}


