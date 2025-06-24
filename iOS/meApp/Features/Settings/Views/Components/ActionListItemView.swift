//
//  ActionListItemView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//

import SwiftUI

// MARK: - Action List Item
/// A reusable list item for settings screens, troubleshooting views, and other views supporting actions and value display.
struct ActionListItemView: View {
    @Environment(\.appTheme) private var theme
    let config: ActionListItemConfig
    
    var body: some View {
        Button {
            if config.toggleBinding == nil {
                config.onTap?()
            }
        } label: {
            HStack(spacing: 12) {
                // Conditional dot indicator
                if config.showDot {
                    Circle()
                        .fill(config.dotColor ?? theme.textError)
                        .frame(width: 9, height: 9)
                }
                
                actionLabelText(config.title, isDestructive: config.isDestructive)
                Spacer()
                
                // Toggle or value display
                if let toggleBinding = config.toggleBinding {
                    CustomToggleView(isOn: toggleBinding)
                        .onChange(of: toggleBinding.wrappedValue) { _, newValue in
                            config.onTap?()
                        }
                } else if let value = config.value {
                    valueText(value)
                }
                
                // Chevron based on type
                chevronView()
            }
        }
        .disabled(config.toggleBinding != nil && config.onTap == nil)
    }
    
    @ViewBuilder
    private func chevronView() -> some View {
        switch config.chevronType {
        case .right:
            AppIconView(icon: AppAssets.chevronRight, size: IconSize(width: 22, height: 22))
                .foregroundColor(theme.statusIconPrimary)
        case .upDown:
            AppIconView(icon: AppAssets.chevronUpDown, size: IconSize(width: 22, height: 22))
                .foregroundColor(theme.statusIconSecondary)
        case .none:
            EmptyView()
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
    @Previewable
    @State var toggleState = true
    List {
        Section("Preview") {
            ActionListItemView(config: ActionListItemConfig(
                title: "Default Row",
                onTap: { print("Tapped Default Row") }
            ))
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Value",
                value: "Enabled",
                onTap: { print("Tapped Value Row") }
            ))
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Up/Down Chevron",
                chevronType: .upDown,
                onTap: { print("Tapped Up/Down Row") }
            ))
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row without Chevron",
                chevronType: .none,
                onTap: { print("Tapped No Chevron Row") }
            ))
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Dot",
                showDot: true,
                onTap: { print("Tapped Dot Row") }
            ))
            
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Toggle Row",
                chevronType: .none, toggleBinding: $toggleState,
                onTap: { print("Toggle tapped") }
            ))
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Destructive Row",
                chevronType: .none, isDestructive: true,
                onTap: { print("Tapped Destructive Row") }
            ))
        }
    }
    .listStyle(.insetGrouped)
}
