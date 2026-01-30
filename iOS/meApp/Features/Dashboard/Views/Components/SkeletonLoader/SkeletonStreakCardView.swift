//
//  SkeletonStreakCardView.swift
//  meApp
//
//  Created for skeleton loading of streak cards
//

import SwiftUI

/// Skeleton loading view for streak cards that matches StreakCardView structure
struct SkeletonStreakCardView: View {
    @Environment(\.appTheme) private var theme
    let parentView: DashboardMetricsParentView
    
    var body: some View {
        NoteBox(alignCenter: true) {
            HStack(alignment: .center, spacing: .spacingSM) {
                // Skeleton icon
                Circle()
                    .fill(theme.backgroundSecondary)
                    .frame(width: 40, height: 40)
                    .shimmer()
                
                // Skeleton content (value and label)
                VStack(alignment: .center, spacing: .spacingXS/2) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(theme.backgroundSecondary)
                        .frame(width: 30, height: 20)
                        .shimmer()
                    
                    RoundedRectangle(cornerRadius: 4)
                        .fill(theme.backgroundSecondary)
                        .frame(width: 60, height: 12)
                        .shimmer()
                }
            }
            .padding(.vertical, parentView == .R4ScaleSetup ? 10 : 0)
        }
        .frame(height: 95)
    }
}

#Preview {
    VStack(spacing: 16) {
        SkeletonStreakCardView(parentView: .dashboard)
        SkeletonStreakCardView(parentView: .R4ScaleSetup)
    }
    .padding()
    .environmentObject(Theme.shared)
}
