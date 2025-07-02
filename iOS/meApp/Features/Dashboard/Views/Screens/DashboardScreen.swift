//
//  DashboardView.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

import SwiftUI

struct DashboardScreen: View {
    @Environment(\.appTheme) private var theme
    @StateObject var scale = DashboardStore()
    let lang = DashboardStrings.self
    @State private var selectedEntry: Entry? = nil
    @State private var selectedMetric: BodyMetric? = nil

    var body: some View {
        VStack(spacing: 0) {

            NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false)
                .zIndex(100)
            
            ScrollView(showsIndicators: false) {
                WeightTrendView()
                    .frame(height: 490)
                    .padding(.top, .spacingLG)

                let allContentRemoved = scale.metricsToShow.isEmpty && (!scale.isEditMode && scale.isGoalCardRemoved) && scale.streakItemsToShow.isEmpty

                if !allContentRemoved {
                    metricGridSection()
                        .padding(.top,.spacingSM)

                    if !scale.metricsToShow.isEmpty {
                        Divider()
                            .foregroundColor(theme.statusUtility)
                            .padding(.vertical, .spacingSM)
                            .padding(.horizontal, .spacing2XL)
                    }

                    goalCardSection()

                    streakAndLossGrid()
                }

                actionButtonsSection()
                    .padding(.top, allContentRemoved ? .spacing6XL : .spacingSM)
            }
        }
        .ignoresSafeArea(.all)
        .background(theme.backgroundSecondary)
        .sheet(item: $selectedEntry) { entry in
            ScaleMetricsView(entry: entry, selectedMetric: selectedMetric ?? .bmi)
        }
    }

    // MARK: - Metric Grid Section
    private func metricGridSection() -> some View {
        let columns = scale.metricGridColumns
        let metricsToShow = scale.metricsToShow

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(Array(metricsToShow.enumerated()), id: \.offset) { index, item in
                MetricCardView(
                    value: item.preLabel != nil ? "\(item.preLabel!) \(item.value)" : item.value,
                    label: item.label,
                    metricType: scale.metricType,
                    isEditMode: scale.isEditMode,
                    isRemoved: scale.isMetricRemovedInReorderedArray(at: index),
                    onToggleRemoval: {
                        scale.toggleMetricRemovalInReorderedArray(at: index)
                    }
                )
            }
        }
        .padding(.top, .spacing3XL)
        .padding(.horizontal, .spacingSM)
    }
    
    // MARK: - Streak and Loss Grid
    private func streakAndLossGrid() -> some View {
        let columns = scale.streakColumns
        let streakItemsToShow = scale.streakItemsToShow

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(Array(streakItemsToShow.enumerated()), id: \.offset) { index, item in
                NoteBox(alignCenter: true) {
                    HStack(alignment: .center, spacing: 8) {
                        if let icon = item.icon {
                            AppIconView(icon: icon, size: IconSize(width: 40, height: 40))
                                .foregroundColor(scale.isStreakRemovedInReorderedArray(at: index) ? theme.statusIconSecondary : theme.statusStreak)
                                .padding(.trailing, 2)
                        }
                        VStack(alignment: .center, spacing: 2) {
                            Text(item.value)
                                .fontOpenSans(.heading4)
                                .fontWeight(.bold)
                                .foregroundColor(theme.textHeading)
                            Text(item.label)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.textSubheading)
                        }
                    }
                }
                .editModeOverlay(
                    isEditMode: scale.isEditMode,
                    isRemoved: scale.isStreakRemovedInReorderedArray(at: index),
                    onToggleRemoval: {
                        scale.toggleStreakRemovalInReorderedArray(at: index)
                    }
                )
            }
        }
        .padding(.bottom, .spacingSM)
        .padding(.top, (!scale.isEditMode && scale.isGoalCardRemoved) ? 0 : .spacingSM)
        .padding(.horizontal, .spacingSM)
    }
    
    // MARK: - Goal Progress Section
    private func goalCardSection() -> some View {
        if !scale.isEditMode && scale.isGoalCardRemoved {
            return AnyView(EmptyView())
        }
        
        return AnyView(
            GoalProgressCardView(
                delta: scale.goalDelta,
                startWeight: scale.goalStartWeight,
                goalWeight: scale.goalGoalWeight,
                unit: scale.goalUnit.rawValue,
                isRemoved: scale.isGoalCardRemoved
            )
            .editModeOverlay(
                isEditMode: scale.isEditMode,
                isRemoved: scale.isGoalCardRemoved,
                onToggleRemoval: {
                    scale.toggleGoalCardRemoval()
                }
            )
            .padding(.horizontal, .spacingSM)
        )
    }

    // MARK: - Action Buttons
    private func actionButtonsSection() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if scale.isEditMode {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: false, action: {
                    scale.saveChanges()
                })
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: false, action: {
                    scale.resetDashboard()
                })
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: false, action: {
                    scale.isEditMode.toggle()
                })
                ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: false, action: {
                    let entry = scale.createEntryForMetricInfo()
                    selectedEntry = entry
                    selectedMetric = .bmi
                })
            }
        }
        .padding(.bottom, .spacingLG)
    }
}
