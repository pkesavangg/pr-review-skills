//
//  SkeletonBpmMetricsSection.swift
//  meApp
//
//  Skeleton loader for BPM metrics: three-reading average card + two streak cards.
//

import SwiftUI

struct SkeletonBpmMetricsSection: View {
    @Environment(\.appTheme) private var theme
    @State private var isAnimating = false

    private let streakColumns = [
        GridItem(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing),
        GridItem(.flexible(), spacing: DashboardConstants.UIConstants.gridSpacing)
    ]

    private var skeletonColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.28 : 0.16)
    }

    var body: some View {
        VStack(spacing: .spacingMD) {
            threeReadingAverageSkeleton
            streakCardsSkeleton
        }
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }

    private var threeReadingAverageSkeleton: some View {
        VStack(spacing: .zero) {
            HStack(alignment: .center, spacing: 12) {
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 64, height: 32)

                    RoundedRectangle(cornerRadius: 2)
                        .fill(skeletonColor)
                        .frame(width: 2, height: 28)

                    RoundedRectangle(cornerRadius: 6)
                        .fill(skeletonColor)
                        .frame(width: 64, height: 32)
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                RoundedRectangle(cornerRadius: 6)
                    .fill(skeletonColor)
                    .frame(width: 48, height: 32)
                    .frame(width: 72, alignment: .trailing)
            }

            RoundedRectangle(cornerRadius: 4)
                .fill(skeletonColor)
                .frame(width: 120, height: 12)
                .padding(.top, .spacingSM)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .frame(height: 128)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .shimmer()
    }

    private var streakCardsSkeleton: some View {
        LazyVGrid(columns: streakColumns, spacing: DashboardConstants.UIConstants.gridSpacing) {
            ForEach(0..<2, id: \.self) { _ in
                SkeletonStreakCardView(parentView: .dashboard)
            }
        }
    }
}

#Preview {
    SkeletonBpmMetricsSection()
        .padding()
        .background(Color(.systemGroupedBackground))
}
