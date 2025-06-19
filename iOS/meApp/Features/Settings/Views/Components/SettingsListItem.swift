//
//  SettingsListItem.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - Settings List Item
/// A reusable list item for settings screens, supporting actions and value display.
struct SettingsListItem: View {
    @Environment(\.appTheme) private var theme
    let config: SettingsItemConfig
    
    var body: some View {
        Button {
            config.onTap?()
        } label: {
            HStack {
                actionLabelText(config.title, isDestructive: config.isDestructive)
                Spacer()
                if let value = config.value {
                    valueText(value)
                }
                if config.canShowChevron {
                    AppIconView(icon: AppAssets.chevronUp)
                        .foregroundColor(theme.statusIconPrimary)
                        .rotationEffect(Angle(degrees: 90))
                }
            }
        }
    }
    
    private func actionLabelText(_ text: String, isDestructive: Bool = false) -> some View {
        Text(text)
            .fontOpenSans(.body2)
            .foregroundColor(isDestructive ? theme.textError: theme.textBody)
    }
    
    private func valueText(_ text: String) -> some View {
        Text(text)
            .fontOpenSans(.body2)
            .foregroundColor(theme.textSubheading)
    }
}

#Preview {
    List {
        Section("Preview") {
            SettingsListItem(config: SettingsItemConfig(
                title: "Default Row",
                onTap: { print("Tapped Default Row") }
            ))

            SettingsListItem(config: SettingsItemConfig(
                title: "Row with Value",
                value: "Enabled",
                onTap: { print("Tapped Value Row") }
            ))

            SettingsListItem(config: SettingsItemConfig(
                title: "Row without Chevron",
                canShowChevron: false,
                onTap: { print("Tapped No Chevron Row") }
            ))

            SettingsListItem(config: SettingsItemConfig(
                title: "Destructive Row",
                isDestructive: true,
                onTap: { print("Tapped Destructive Row") }
            ))

            SettingsListItem(config: SettingsItemConfig(
                title: "Destructive + Value + Chevron",
                value: "Danger",
                isDestructive: true,
                onTap: { print("Tapped Complex Row") }
            ))
            .settingsRowInsets() // Apply custom insets
        }
    }
    .listStyle(.insetGrouped)
}
