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
    }

    // MARK: - Metric Grid Section
    private func metricGridSection() -> some View {
        let metrics: [(value: String, label: String, unit: String?, preLabel: String?)] = [
            ("24.5", "bmi", nil, nil),
            ("18.3", "body fat %", "%", nil),
            ("41.6", "muscle %", "%", nil),
            ("59.1", "water %", "%", nil),
            ("80", "heart bpm", "bpm", nil),
            ("4.4", "bone %", "%", nil),
            ("8", "visceral fat", nil, "Lv."),
            ("10.3", "sub fat %", "%", nil),
            ("18.6", "protein %", "%", nil),
            ("52.7", "skel muscle", "%", nil),
            ("1862", "bmr kcal", "kcal", nil),
            ("28", "met age", "yrs", nil)
        ]

        let columns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 3)

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(Array(metrics.enumerated()), id: \.offset) { pair in
                let item = pair.element
                MetricCardView(
                    value: item.preLabel != nil ? "\(item.preLabel!) \(item.value)" : item.value,
                    label: item.label,
                    unit: item.unit,
                    preLabel: item.preLabel
                )
            }
        }
        .padding(.top, .spacing3XL)
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Goal Progress Section
    private func goalCardSection() -> some View {
        GoalProgressCardView(
            delta: -13.2,
            startWeight: 154.3,
            goalWeight: 132.3,
            unit: "lbs"
        )
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Streak and Loss Grid
    private func streakAndLossGrid() -> some View {
        let items: [(icon: String?, value: String, label: String, iconColor: Color?)] = [
            (AppAssets.streak, "1 day", "current streak", theme.statusStreak),
            (AppAssets.longestStreak, "10 day", "longest streak", theme.statusStreak),
            (nil, "-1", "lbs/week", nil),
            (nil, "-10", "lbs/month", nil),
            (nil, "-20", "lbs/year", nil),
            (nil, "-30", "lbs/total", nil)
        ]

        let columns = Array(repeating: GridItem(.flexible(), spacing: 16), count: 2)

        return LazyVGrid(columns: columns, spacing: 16) {
            ForEach(Array(items.enumerated()), id: \.offset) { pair in
                let item = pair.element
                NoteBox(alignCenter: true) {
                    HStack(alignment: .center, spacing: 8) {
                        if let icon = item.icon, let color = item.iconColor {
                            AppIconView(icon: icon, size: IconSize(width: 40, height: 40))
                                .foregroundColor(color)
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
            ButtonView(text: "Edit dashboard", type: .outlinedPrimary, size: .large, isDisabled: false, action: {})
            ButtonView(text: "update goal", type: .textPrimary, size: .large, isDisabled: false, action: {})
            ButtonView(text: "Metric info", type: .textPrimary, size: .large, isDisabled: false, action: {})
        }
        .padding(.top, .spacingSM)
        .padding(.bottom, .spacingLG)
    }
}


// MARK: - Dashboard Store
/// A store to manage scale settings and actions, including details for a selected scale.

class DashboardStore: ObservableObject {
}
