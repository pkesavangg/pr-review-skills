//
//  StreakCardView.swift
//  meApp
//
//  Created by Lakshmi Priya on 01/07/25.
//

import SwiftUI

struct StreakCardView: View {
    let value: String
    let label: String
    let icon: String?
    let isEditMode: Bool
    let isRemoved: Bool
    let isDropTarget: Bool
    let onToggleRemoval: () -> Void
    let onDrop: (String, String) -> Bool
    let onDropTargetChanged: (Bool) -> Void
    
    @Environment(\.appTheme) private var theme
    
    private var borderColor: Color {
        isDropTarget ? theme.actionSecondary : Color.clear
    }
    
    private var borderWidth: CGFloat {
        isDropTarget ? 2 : 0
    }
    
    var body: some View {
        NoteBox(alignCenter: true) {
            content()
        }
        .overlay(
            RoundedRectangle(cornerRadius: .radiusSM)
                .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [6]))
                .foregroundColor(isDropTarget ? theme.actionSecondary : Color.clear)
        )

    }
    
    private func content() -> some View {
        HStack(alignment: .center, spacing: 8) {
            if let icon = icon {
                AppIconView(
                    icon: icon,
                    size: DevicePlatform.isSmallPhone ? IconSize(width: 32, height: 32) : IconSize(width: 40, height: 40)
                )
                .foregroundColor(isRemoved ? theme.statusIconSecondary : theme.statusStreak)
                .padding(.trailing, 2)
            }
            VStack(alignment: .center, spacing: 2) {
                Text(value)
                    .fontOpenSans(.heading4)
                    .fontWeight(.bold)
                    .foregroundColor(theme.textHeading)
                Text(label)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            }
        }
    }
}

#Preview {
    VStack(spacing: 16) {
        StreakCardView(
            value: "1 day",
            label: "Current Streak",
            icon: AppAssets.streak,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: false,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in }
        )
        
        StreakCardView(
            value: "10 day",
            label: "Longest Streak",
            icon: AppAssets.longestStreak,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: true,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in }
        )
    }
    .padding()
}
