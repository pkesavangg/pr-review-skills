//
//  SkeletonGoalCardView.swift
//  meApp
//
//  Created for skeleton loading of goal progress card
//

import SwiftUI

/// Skeleton loading view for goal progress card that matches GoalProgressView structure
struct SkeletonGoalCardView: View {
    @Environment(\.appTheme) private var theme
    @State private var isAnimating = false
    
    private var skeletonColor: Color {
        theme.textSubheading.opacity(isAnimating ? 0.4 : 0.2)
    }
    
    var body: some View {
        NoteBox(alignCenter: false) {
            VStack(alignment: .leading, spacing: .spacingXS) {
                // Skeleton header (delta value and label)
                HStack(alignment: .firstTextBaseline) {
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 50, height: 24)
                    
                    RoundedRectangle(cornerRadius: 4)
                        .fill(skeletonColor)
                        .frame(width: 80, height: 14)
                }
                
                // Skeleton progress bar with labels
                VStack(spacing: .spacingXS) {
                    RoundedRectangle(cornerRadius: 8)
                        .fill(skeletonColor)
                        .frame(height: 8)
                    
                    // Start and goal weight labels
                    HStack {
                        RoundedRectangle(cornerRadius: 4)
                            .fill(skeletonColor)
                            .frame(width: 60, height: 12)
                        Spacer()
                        RoundedRectangle(cornerRadius: 4)
                            .fill(skeletonColor)
                            .frame(width: 60, height: 12)
                    }
                }
            }
            .padding(.spacingSM)
        }
        .frame(height: 120)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }
}

#Preview {
    SkeletonGoalCardView()
        .padding()
        .environmentObject(Theme.shared)
}
