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
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

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
        parentView == .r4DeviceSetup ? 74 : 70
    }

    /// Reduces NoteBox padding at larger Dynamic Type sizes so longer streak values fit.
    /// Small phones keep more padding since their baseline icon/text is already compact.
    private var cardPadding: CGFloat {
        let isCompactDevice = DevicePlatform.isSmallPhone || DevicePlatform.isMiniPhone
        if dynamicTypeSize.isAccessibilitySize { return isCompactDevice ? .spacingXS : 4 }
        if dynamicTypeSize >= .xxLarge { return isCompactDevice ? .spacingXSM : .spacingXS } // 12 vs 8
        return .spacingSM                                                                     // 16pt
    }

    var body: some View {
        NoteBox(alignCenter: true, padding: cardPadding) {
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
        var baseSize: CGFloat = {
            if DevicePlatform.isMiniPhone { return 24 }
            if DevicePlatform.isSmallPhone { return 32 }
            return 40
        }()

        // At XXL and larger, shrink the streak icons so the value/label text has room to grow.
        // Reduction is gentler on small/mini phones where the icon already starts at 32pt (or 24pt).
        if isStreakItem {
            let isCompactDevice = DevicePlatform.isSmallPhone || DevicePlatform.isMiniPhone
            if dynamicTypeSize.isAccessibilitySize {
                let reduction: CGFloat = isCompactDevice ? 8 : 16
                baseSize = max(baseSize - reduction, 20)
            } else if dynamicTypeSize >= .xxLarge {
                let reduction: CGFloat = isCompactDevice ? 4 : 8
                baseSize = max(baseSize - reduction, 24)
            }
        }

        // "Current streak" normally gets an extra 5pt width, but at XXL+ that extra width
        // squeezes the label into a truncated "Current strea…" — drop the bonus at large sizes
        // so it matches the "Longest streak" icon and both labels render fully.
        let isCompressed = dynamicTypeSize >= .xxLarge
        let currentWidthBonus: CGFloat = isCompressed ? 0 : 5

        switch label {
        case DashboardStrings.currentStreak:
            return IconSize(width: baseSize + currentWidthBonus, height: baseSize + 5)

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

    /// VoiceOver reading for the streak value — always spells out "day"/"days" in full so the
    /// 1000+ "d" abbreviation (visual-only, for fixed card width) is never announced as "d".
    private var streakAccessibilityValue: String {
        let displayValue = value == DashboardStrings.placeholder ? "0" : value
        let streakCount = Int(displayValue) ?? 0
        return displayValue + (streakCount == 1 ? " day" : " days")
    }
    
    /// Formats non-streak value, converting placeholder to "0"
    private var formattedNonStreakValue: String {
        // If value is placeholder, show "0" instead of "--"
        return value == DashboardStrings.placeholder ? "0" : value
    }
    
    /// Horizontal gap between the icon and the value/label — tightens with Dynamic Type.
    /// Small phones keep a larger gap since the baseline spacing is already tight.
    private var contentSpacing: CGFloat {
        let isCompactDevice = DevicePlatform.isSmallPhone || DevicePlatform.isMiniPhone
        if dynamicTypeSize.isAccessibilitySize { return isCompactDevice ? 4 : 2 }
        if dynamicTypeSize >= .xxLarge { return isCompactDevice ? 6 : 4 }
        return 8
    }

    private func content() -> some View {
        HStack(alignment: .center, spacing: contentSpacing) {
            if let icon = icon {
                AppIconView(
                    icon: icon,
                    size: iconSize
                )
                .foregroundColor(isRemoved ? theme.statusIconSecondary : theme.statusStreak)
                .padding(.trailing, 2)
            }
            if parentView == .r4DeviceSetup && isStreakItem {
                Text(label)
                    .fontOpenSans(.subHeading2)
                    .foregroundColor(theme.textSubheading)
            } else {
                VStack(alignment: isStreakItem ? .leading : .center, spacing: 2) {
                    Text(isStreakItem ? formattedStreakValue : formattedNonStreakValue)
                        .fontOpenSans(.heading4)
                        .fontWeight(.bold)
                        .foregroundColor(theme.textHeading)
                        .accessibilityLabel(isStreakItem ? streakAccessibilityValue : formattedNonStreakValue)
                    Text(label)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textSubheading)
                }
            }
        }
        .padding(.vertical, (parentView == .r4DeviceSetup && isStreakItem) ? 10 : 0)
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
        
        // Abbreviated: "1000 d" (1000+ collapses "days" → "d" to keep card width fixed)
        StreakCardView(
            value: "1000",
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

        // R4DeviceSetup view with streak items (icon + label only)
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
            parentView: .r4DeviceSetup
        )
        
        // R4DeviceSetup view with non-streak items (icon + value + label)
        StreakCardView(
            value: "2.5",
            label: "lb/week",
            icon: nil,
            isEditMode: true,
            isRemoved: false,
            isDropTarget: true,
            onToggleRemoval: {},
            onDrop: { _, _ in true },
            onDropTargetChanged: { _ in },
            parentView: .r4DeviceSetup
        )
    }
    .padding()
}
