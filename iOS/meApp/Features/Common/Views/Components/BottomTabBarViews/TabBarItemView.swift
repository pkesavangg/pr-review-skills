//
//  TabBarItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//


import SwiftUI

// MARK: - TabBarItemView
/// A view representing an item in the bottom tab bar.
/// This view displays an icon and a label for each tab, with a badge for the settings tab if applicable.
struct TabBarItemView: View {
    @Environment(\.appTheme) private var theme
    let tab: BottomTab
    let isSelected: Bool
    let showSettingsBadge: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            VStack {
                Image(isSelected ? tab.filledIcon : tab.icon)
                // TODO: After UX provide the transparent icons we can use the below code
                // .renderingMode(.template)
                // .foregroundColor(theme.actionSecondary)
            }
            .frame(width: 40, height: 40)
            .overlay {
                if tab == .settings && showSettingsBadge {
                    Circle()
                        .fill(theme.statusError)
                        .frame(width: 9, height: 9)
                        .offset(x: 12, y: 7)
                }
            }
            Text(tab.label)
                .fontOpenSans(.body5)
                .foregroundColor(theme.textSubheading)
        }
    }
}

// MARK: - Preview
#Preview(body: {
    HStack {
        TabBarItemView(tab: .dash, isSelected: true, showSettingsBadge: false)
            .environmentObject(Theme.shared)
        TabBarItemView(tab: .entry, isSelected: true, showSettingsBadge: false)
            .environmentObject(Theme.shared)
        TabBarItemView(tab: .history, isSelected: true, showSettingsBadge: false)
            .environmentObject(Theme.shared)
        TabBarItemView(tab: .settings, isSelected: true, showSettingsBadge: false)
            .environmentObject(Theme.shared)
    }

})
