//
//  BabyTrendView.swift
//  meApp
//
//  Baby-specific trend view displayed when a baby profile is selected.
//  Shows weight/height graph with baby-themed purple color, WHO percentile
//  curves, and a metric toggle (Weight / Height).
//

import SwiftUI

struct BabyTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore
    let babyProfile: BabyProfile
    @Environment(\.appTheme) private var theme
    @State private var isGrowthPercentilesSheetPresented = false
    private let viewModel = BabyTrendViewModel()

    private var babyColor: Color { theme.babyPrimary }

    private var displayState: BabyTrendDisplayState {
        viewModel.displayState(dashboardStore: dashboardStore, babyProfile: babyProfile)
    }

    var body: some View {
        DashboardTrendView(dashboardStore: dashboardStore) {
            babyInfoSection
        }
        .environment(\.babyGrowthChartCalloutDateStyle, true)
        .onAppear {
            viewModel.handleAppear(dashboardStore: dashboardStore)
        }
        .onChange(of: dashboardStore.state.graph.selectedPeriod) { _, _ in
            viewModel.handlePeriodChange(dashboardStore: dashboardStore)
        }
        .sheet(isPresented: $isGrowthPercentilesSheetPresented) {
            BabyGrowthPercentilesSheet(
                state: viewModel.growthPercentilesSheetState(
                    dashboardStore: dashboardStore,
                    babyProfile: babyProfile
                )
            )
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
                .presentationCornerRadius(.radiusXL)
                .presentationBackground(theme.backgroundSecondary)
        }
    }

    // MARK: - Baby Info Section

    private var isGraphLoading: Bool {
        !dashboardStore.state.graph.isGraphReady
    }

    @ViewBuilder
    private var babyInfoSection: some View {
        HStack(alignment: .top) {
            Button {
                isGrowthPercentilesSheetPresented = true
            } label: {
                VStack(alignment: .leading, spacing: .zero) {
                    Text(displayState.headlineLabel)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textSubheading)

                    ZStack(alignment: .leading) {
                        if isGraphLoading {
                            babyValueSkeleton
                        }

                        Group {
                            if displayState.selectedMetric == .weight {
                                babyWeightDisplay
                            } else {
                                babyHeightDisplay
                            }
                        }
                        .opacity(isGraphLoading ? 0 : 1)
                    }
                    .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
                }
            }
            .buttonStyle(.plain)

            Spacer()

            metricToggle
        }
        .padding(.horizontal, .spacingSM)
    }

    // MARK: - Baby Weight Display

    @ViewBuilder
    private var babyWeightDisplay: some View {
        let display = displayState.weightDisplay
        HStack(alignment: .lastTextBaseline, spacing: .zero) {
            Text(display.primary)
                .fontOpenSans(.heading1)
                .fontWeight(.heavy)
                .foregroundColor(babyColor)

            Text(display.primaryUnit)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, 4)

            if let secondary = display.secondary, let secondaryUnit = display.secondaryUnit {
                Text(secondary)
                    .fontOpenSans(.heading1)
                    .fontWeight(.heavy)
                    .foregroundColor(babyColor)
                    .padding(.leading, .spacingMD)

                Text(secondaryUnit)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
                    .padding(.leading, 4)
            }
        }
        .frame(height: 55)
    }

    @ViewBuilder
    private var babyHeightDisplay: some View {
        HStack(alignment: .lastTextBaseline, spacing: .zero) {
            Text(displayState.heightDisplayText)
                .fontOpenSans(.heading1)
                .fontWeight(.heavy)
                .foregroundColor(babyColor)

            Text(BabyDashboardStrings.inches)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, 8)
        }
        .frame(height: 55)
    }

    // MARK: - Skeleton

    @State private var isSkeletonAnimating = false

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isSkeletonAnimating ? 0.4 : 0.2)
    }

    private var babyValueSkeleton: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(skeletonColor)
            .frame(width: 180, height: 40)
            .frame(height: 55, alignment: .leading)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                    isSkeletonAnimating = true
                }
            }
    }

    // MARK: - Weight / Height Toggle

    private var metricToggle: some View {
        VStack(spacing: 4) {
            metricButton(for: .weight)
            metricButton(for: .height)
        }
        .padding(.top, 4)
    }

    private func metricButton(for metric: BabyMetric) -> some View {
        Button {
            guard displayState.selectedMetric != metric else { return }
            withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
                viewModel.applySelectedMetric(metric, to: dashboardStore)
            }
        } label: {
            Text(metric.rawValue.uppercased())
                .fontOpenSans(.heading5)
                .foregroundColor(displayState.selectedMetric == metric ? theme.textInverse : babyColor)
                .padding(.vertical, 6)
                .padding(.horizontal, 12)
                .frame(minWidth: 80)
                .background(
                    RoundedRectangle(cornerRadius: .radiusSM)
                        .fill(displayState.selectedMetric == metric ? babyColor : Color.clear)
                )
        }
        .buttonStyle(.plain)
    }
}

#Preview("Baby Trend Mock Size") {
    BabyTrendView(
        dashboardStore: DashboardStore(),
        babyProfile: BabyProfile(
            id: "preview-baby",
            name: "Ava"
        )
    )
    .frame(width: 402, height: 874)
}
