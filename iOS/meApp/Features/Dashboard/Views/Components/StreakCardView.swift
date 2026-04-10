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
    let parentView: DashboardMetricsParentView
    
    @Environment(\.appTheme) private var theme
    
    private var borderColor: Color {
        isDropTarget ? theme.actionSecondary : Color.clear
    }
    
    private var borderWidth: CGFloat {
        isDropTarget ? 2 : 0
    }
    
    /// Returns true if this is a streak item (current streak or longest streak)
    private var isStreakItem: Bool {
        label == DashboardStrings.currentStreak || label == DashboardStrings.longestStreak
    }
    
    /// Returns the minimum height based on parentView
    private var cardMinHeight: CGFloat {
        parentView == .R4ScaleSetup ? 74 : 70
    }
    
    var body: some View {
        NoteBox(alignCenter: true) {
            content()
        }
        .frame(minHeight: cardMinHeight)
        .overlay(
            RoundedRectangle(cornerRadius: .radiusSM)
                .strokeBorder(style: StrokeStyle(lineWidth: 2, dash: [6]))
                .foregroundColor(isDropTarget ? theme.actionSecondary : Color.clear)
        )

    }
    
    /// Computes the icon size based on the label
    private var iconSize: IconSize {
        let baseSize: CGFloat = {
            if DevicePlatform.isMiniPhone { return 24 }
            if DevicePlatform.isSmallPhone { return 32 }
            return 40
        }()

        switch label {
        case DashboardStrings.currentStreak:
            return IconSize(width: baseSize + 5, height: baseSize + 5)

        case DashboardStrings.longestStreak:
            return IconSize(width: baseSize, height: baseSize + 5)

        default:
            return IconSize(width: baseSize, height: baseSize)
        }
    }
    
    /// Formats streak value with proper day/days pluralization.
    private var formattedStreakValue: String {
        // If value is placeholder, show "0 days" instead of "-- days"
        let displayValue = value == DashboardStrings.placeholder ? "0" : value
        let streakCount = Int(displayValue) ?? 0
        return displayValue + DashboardStrings.daySuffix(forStreak: streakCount)
    }
    
    /// Formats non-streak value, converting placeholder to "0"
    private var formattedNonStreakValue: String {
        // If value is placeholder, show "0" instead of "--"
        return value == DashboardStrings.placeholder ? "0" : value
    }
    
    private func content() -> some View {
        HStack(alignment: .center, spacing: 8) {
            if let icon = icon {
                AppIconView(
                    icon: icon,
                    size: iconSize
                )
                .foregroundColor(isRemoved ? theme.statusIconSecondary : theme.statusStreak)
                .padding(.trailing, 2)
            }
            if parentView == .R4ScaleSetup && isStreakItem {
                Text(label)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            } else {
                VStack(alignment: isStreakItem ? .leading : .center, spacing: 2) {
                    Text(isStreakItem ? formattedStreakValue : formattedNonStreakValue)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.1)
                    Text(label)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                        .lineLimit(1)
                        .minimumScaleFactor(0.1)
                }
            }
        }
        .padding(.vertical, (parentView == .R4ScaleSetup && isStreakItem) ? 10 : 0)
    }
}

#Preview {
    VStack(spacing: 16) {
        // Singular: "1 day"
        StreakCardView(
            value: "1",
            label: DashboardStrings.currentStreak,
            icon: AppAssets.streak,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: false,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            parentView: .dashboard
        )
        
        // Plural: "5 days"
        StreakCardView(
            value: "5",
            label: DashboardStrings.longestStreak,
            icon: AppAssets.streak,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: false,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            parentView: .dashboard
        )
        
        // R4ScaleSetup view with streak items (icon + label only)
        StreakCardView(
            value: "3",
            label: DashboardStrings.currentStreak,
            icon: AppAssets.streak,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: false,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            parentView: .R4ScaleSetup
        )
        
        // R4ScaleSetup view with non-streak items (icon + value + label)
        StreakCardView(
            value: "2.5",
            label: "lbs/week",
            icon: nil,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: true,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            parentView: .R4ScaleSetup
        )
    }
    .padding()
}
