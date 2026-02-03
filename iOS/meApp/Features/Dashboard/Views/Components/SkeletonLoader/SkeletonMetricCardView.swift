//
//  SkeletonMetricCardView.swift
//  meApp
//
//  Created for skeleton loading of metric cards
//

import SwiftUI

/// Skeleton loading view for metric cards that matches the structure of MetricCardView
struct SkeletonMetricCardView: View {
    @Environment(\.appTheme) private var theme
    let dashboardType: DashboardType
    
    private var verticalPadding: CGFloat {
        dashboardType == .dashboard12 
            ? MetricCardView.twelveCardVerticalPadding 
            : MetricCardView.fourCardVerticalPadding
    }
    
    var body: some View {
        VStack(spacing: .spacingXS/2) {
            // Skeleton value/icon placeholder
            RoundedRectangle(cornerRadius: 4)
                .fill(theme.backgroundSecondary)
                .frame(width: 30, height: 25)
                .shimmer()
            
            // Skeleton label placeholder
            RoundedRectangle(cornerRadius: 4)
                .fill(theme.backgroundSecondary)
                .frame(width: 65, height: 15)
                .shimmer()
        }
        .frame(maxWidth: .infinity, minHeight: MetricCardView.defaultCardMinHeight)
        .padding(.vertical, verticalPadding)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
    }
}

#Preview {
    VStack(spacing: 16) {
        SkeletonMetricCardView(dashboardType: .dashboard12)
        SkeletonMetricCardView(dashboardType: .dashboard4)
    }
    .padding()
    .environmentObject(Theme.shared)
}
