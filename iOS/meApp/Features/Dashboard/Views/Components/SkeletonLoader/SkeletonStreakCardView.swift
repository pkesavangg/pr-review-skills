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
    @State private var isAnimating = false
    let parentView: DashboardMetricsParentView
    
    private var skeletonColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.4 : 0.2)
    }
    private var cardMinHeight: CGFloat {
        parentView == .r4DeviceSetup ? 74 : 70
    }
    
    var body: some View {
        NoteBox(alignCenter: true) {
            HStack(alignment: .center, spacing: .spacingXS) {
                // Skeleton icon
                Circle()
                    .fill(skeletonColor)
                    .frame(width: 40, height: 40)
                
                // Skeleton content (value and label)
                VStack(alignment: .center, spacing: .spacingXS) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 30, height: 20)
                    
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 60, height: 12)
                }
            }
            .padding(.vertical, 10)
        }
        .frame(height: cardMinHeight)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        SkeletonStreakCardView(parentView: .dashboard)
        SkeletonStreakCardView(parentView: .r4DeviceSetup)
    }
    .padding()
    .environmentObject(Theme.shared)
}
