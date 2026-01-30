//
//  SkeletonGoalCardView.swift
//  meApp
//
//  Created for skeleton loading of goal progress card
//

import SwiftUI

/// Skeleton loading view for goal progress card that matches GoalProgressCardView structure
struct SkeletonGoalCardView: View {
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Skeleton header (title and goal type)
            HStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(theme.backgroundSecondary)
                    .frame(width: 100, height: 16)
                    .shimmer()
                Spacer()
                RoundedRectangle(cornerRadius: 4)
                    .fill(theme.backgroundSecondary)
                    .frame(width: 60, height: 14)
                    .shimmer()
            }
            
            // Skeleton progress bar
            RoundedRectangle(cornerRadius: 8)
                .fill(theme.backgroundSecondary)
                .frame(height: 8)
                .shimmer()
            
            // Skeleton weight info
            HStack {
                RoundedRectangle(cornerRadius: 4)
                    .fill(theme.backgroundSecondary)
                    .frame(width: 80, height: 24)
                    .shimmer()
                Spacer()
                HStack(spacing: 8) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(theme.backgroundSecondary)
                        .frame(width: 70, height: 14)
                        .shimmer()
                    RoundedRectangle(cornerRadius: 4)
                        .fill(theme.backgroundSecondary)
                        .frame(width: 70, height: 14)
                        .shimmer()
                }
            }
        }
        .padding()
        .frame(height: 120)
        .background(theme.backgroundPrimary)
        .cornerRadius(12)
    }
}

#Preview {
    SkeletonGoalCardView()
        .padding()
        .environmentObject(Theme.shared)
}
