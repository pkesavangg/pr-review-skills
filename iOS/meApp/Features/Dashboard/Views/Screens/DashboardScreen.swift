//
//  DashboardView.swift
//  meApp
//
//  Created by Lakshmi Priya on 30/06/25.
//

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
    @State private var isEditingDashboard = false
    @State private var selectedEntry: Entry? = nil
    @State private var selectedMetric: BodyMetric? = nil

    var body: some View {
        VStack {
            ScrollView(showsIndicators: false) {
                NavbarHeaderView<EmptyView, EmptyView>(canShowBorder: false)

                WeightTrendView()
                    .frame(height: 490)
                    .padding(.top, .spacingLG)

                metricGridSection()

                Divider()
                    .foregroundColor(theme.statusUtility)
                    .padding(.vertical, .spacingSM)
                    .padding(.horizontal, .spacing2XL)

                goalCardSection()

                streakAndLossGrid()
                    .padding(.vertical, .spacingSM)
                    .padding(.horizontal, .spacingSM)

                actionButtonsSection()
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
            ForEach(metricsToShow.indices, id: \.self) { idx in
                let item = metricsToShow[idx]
                MetricCardView(
                    value: item.preLabel != nil ? "\(item.preLabel!) \(item.value)" : item.value,
                    label: item.label,
                    unit: item.unit,
                    preLabel: item.preLabel,
                    metricType: scale.metricType
                )
            }
        }
        .padding(.top, .spacing3XL)
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Goal Progress Section
    private func goalCardSection() -> some View {
        GoalProgressCardView(
            delta: scale.goalDelta,
            startWeight: scale.goalStartWeight,
            goalWeight: scale.goalGoalWeight,
            unit: scale.goalUnit.rawValue
        )
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Streak and Loss Grid
    private func streakAndLossGrid() -> some View {
        let columns = scale.streakColumns

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(scale.streakItems.indices, id: \.self) { idx in
                let item = scale.streakItems[idx]
                NoteBox(alignCenter: true) {
                    HStack(alignment: .center, spacing: 8) {
                        if let icon = item.icon {
                            AppIconView(icon: icon, size: IconSize(width: 40, height: 40))
                                .foregroundColor(theme.statusStreak)
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
            }
        }
    }

    // MARK: - Action Buttons
    private func actionButtonsSection() -> some View {
        VStack(alignment: .center, spacing: .spacingSM) {
            if isEditingDashboard {
                ButtonView(text: lang.saveChanges, type: .filledPrimary, size: .large, isDisabled: false, action: {
                    isEditingDashboard = false
                })
                ButtonView(text: lang.resetDashboard, type: .textPrimary, size: .large, isDisabled: false, action: {
                    isEditingDashboard = false
                })
            } else {
                ButtonView(text: lang.editDashboard, type: .outlinedPrimary, size: .large, isDisabled: false, action: {
                    isEditingDashboard = true
                })
                ButtonView(text: lang.updateGoal, type: .textPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: lang.metricInfo, type: .textPrimary, size: .large, isDisabled: false, action: {
                    let entry = scale.createEntryForMetricInfo()
                    selectedEntry = entry
                    selectedMetric = .bmi
                })
            }
        }
        .padding(.top, .spacingSM)
        .padding(.bottom, .spacingLG)
    }
}
