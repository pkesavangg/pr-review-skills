//
//  GraphView.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import SwiftUI

struct WeightTrendView: View {
    @ObservedObject var dashboardStore: DashboardStore
    @Environment(\.appTheme) private var theme

    var body: some View {
        DashboardTrendView(dashboardStore: dashboardStore) {
            weightInfoSection(dashboardStore: dashboardStore)
        }
    }

    private var isGraphLoading: Bool {
        !dashboardStore.state.graph.isGraphReady
    }

    @ViewBuilder
    func weightInfoSection(
        dashboardStore: DashboardStore
    ) -> some View {
        VStack(alignment: .leading, spacing: .zero) {
            Text(dashboardStore.displayManager.weightDisplayLabel)
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textSubheading)
                .padding(.leading, .spacingSM)

            ZStack(alignment: .leading) {
                if isGraphLoading {
                    weightValueSkeleton
                }

                WeightDisplayView(
                    weightText: {
                        if let displayWeight = dashboardStore.displayManager.displayWeight {
                            if abs(displayWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                                return "000.0"
                            }
                            return dashboardStore.displayManager.formatWeightDisplayText(displayWeight)
                        }
                        let averageWeight = dashboardStore.displayManager.getCurrentAverageWeight()
                        if abs(averageWeight) < AppConstants.Precision.doubleEqualityEpsilon {
                            return "000.0"
                        }
                        return dashboardStore.displayManager.formatWeightDisplayText(averageWeight)
                    }(),
                    unitText: dashboardStore.displayManager.displayUnitText
                )
                .opacity(isGraphLoading ? 0 : 1)
            }
            .animation(.easeInOut(duration: 0.3), value: dashboardStore.state.graph.isGraphReady)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: - Skeleton

    @State private var isSkeletonAnimating = false

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isSkeletonAnimating ? 0.4 : 0.2)
    }

    private var weightValueSkeleton: some View {
        RoundedRectangle(cornerRadius: 6)
            .fill(skeletonColor)
            .frame(width: 160, height: 40)
            .padding(.leading, 14)
            .frame(height: 55, alignment: .leading)
            .onAppear {
                withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                    isSkeletonAnimating = true
                }
            }
    }
}

#Preview {
    WeightTrendView(dashboardStore: DashboardStore())
}
