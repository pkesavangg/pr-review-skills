//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//
//  Y-Axis Tick Animation Fix:
//  Uses selective animation strategy: animates chart data and domain changes
//  while keeping ticks stable to prevent jump animations.
//
//  X-Axis Height Fix:
//  Properly positions goal chip and chart elements accounting for X-axis height
//  in week/month/year periods while maintaining correct positioning for total period.
//

import SwiftUI
import Charts

struct GraphView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @EnvironmentObject private var accountService: AccountService
    @Environment(\.appTheme) private var theme
    
    // Section view models
    @StateObject private var totalSectionViewModel = TotalSectionViewModel()
    @StateObject private var yearSectionViewModel = YearSectionViewModel()
    @StateObject private var monthSectionViewModel = MonthSectionViewModel()
    @StateObject private var weekSectionViewModel = WeekSectionViewModel()
    
    // Reset chart identity on period switches to avoid stale animations/state
    @State private var chartIdentity: UUID = UUID()
    
    // Check if there are any entries to display
    private var hasEntries: Bool {
        return !dashboardStore.continuousOperations.isEmpty
    }
    
    // Get the appropriate empty state message
    private var emptyStateMessage: String {
        return DashboardStrings.noEntriesMessage
    }
    
    var body: some View {
        VStack(alignment: .leading){
            // Preserve layout height: fade the label out instead of removing it to avoid jump
            Text(dashboardStore.weightLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .opacity(dashboardStore.state.graph.selectedPoint == nil ? 1 : 0)
                .padding(.leading, .spacingSM)
                .padding(.vertical, .spacingXS)
            if hasEntries {
                chartView
                    .id(chartIdentity)
            } else {
                emptyStateView
            }
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, newValue in
            // Clear crosshair and selection when time period changes
            dashboardStore.clearSelection()
            
            // Also clear local selection in all section view models
            totalSectionViewModel.clearSelection()
            yearSectionViewModel.clearSelection()
            monthSectionViewModel.clearSelection()
            weekSectionViewModel.clearSelection()
            
            // Reconfigure active section view model with fresh store state
            switch newValue {
            case .week:
                weekSectionViewModel.configure(with: dashboardStore)
            case .month:
                monthSectionViewModel.configure(with: dashboardStore)
            case .year:
                yearSectionViewModel.configure(with: dashboardStore)
            case .total:
                totalSectionViewModel.configure(with: dashboardStore)
            }
            
            // Ensure most recent entries are shown for the selected period
            let optimal = dashboardStore.graphManager.calculateOptimalScrollPosition(
                for: newValue,
                from: dashboardStore.continuousOperations,
                showingLatest: true
            )
            dashboardStore.graphManager.updateScrollPosition(to: optimal)
            
            // Recalculate and cache Y-axis based on the new visible region
            dashboardStore.updateYAxisCache()
            
            // Reset chart identity to fully rebuild the Chart without unwanted animations
            chartIdentity = UUID()
        }
        // Immediately react to active account goal updates like GoalProgressView
        .onReceive(accountService.$activeAccount) { _ in
            dashboardStore.handleSettingsChange()
        }
        // Disable implicit animations when switching sections to prevent axes animating from outside
        .animation(nil, value: dashboardStore.state.graph.selectedPeriod)
        
    }
    
    // MARK: - Chart View
    private var chartView: some View {
        return HStack(spacing: 0) {
            // Use switch case for different time periods (total, year, month, week)
            switch dashboardStore.state.graph.selectedPeriod {
            case .week:
                WeekGraphView(
                    viewModel: weekSectionViewModel,
                    dashboardStore: dashboardStore
                )
            case .month:
                MonthGraphView(
                    viewModel: monthSectionViewModel,
                    dashboardStore: dashboardStore
                )
            case .year:
                YearGraphView(
                    viewModel: yearSectionViewModel,
                    dashboardStore: dashboardStore
                )
            case .total:
                TotalGraphView(
                    viewModel: totalSectionViewModel,
                    dashboardStore: dashboardStore
                )
            }
        }
    }
    
    // MARK: - Empty State View
    private var emptyStateView: some View {
        VStack(spacing: .spacingMD) {
            Spacer()
            
            Text(emptyStateMessage)
                .fontOpenSans(.heading5)
                .foregroundColor(theme.textHeading)
                .fontWeight(.bold)
                .multilineTextAlignment(.center)
                .padding(.horizontal, .spacingLG)
            
            Spacer()
        }
        .graphViewStyle(isAtLeftBoundary: true)
        .padding(.horizontal)
        .background(theme.textInverse)
    }
}

#Preview {
    GraphView(dashboardStore: DashboardStore())
        .frame(height: 240)
        .padding()
}
