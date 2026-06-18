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
    let rowHeight = CGFloat(48)
    var body: some View {
        Button {
            if config.toggleBinding == nil {
                config.onTap?()
            }
        } label: {
            VStack {
                Spacer()
                HStack(spacing: 12) {
                    // Conditional dot indicator
                    if config.showDot {
                        Circle()
                            .fill(config.dotColor ?? theme.textError)
                            .frame(width: 9, height: 9)
                            .accessibilityHidden(true)
                    } else if let leadingIcon = config.leadingIcon {
                        leadingIcon
                            .accessibilityHidden(true)
                    }
                    
                    actionLabelText(config.title, isDestructive: config.isDestructive)
                    Spacer()
                    
                    // Toggle or value display
                    if let toggleBinding = config.toggleBinding {
                        CustomToggleView(isOn: toggleBinding)
                            .onChange(of: toggleBinding.wrappedValue) { _, _ in
                                config.onTap?()
                            }
                    } else if let value = config.value {
                        valueText(value)
                    }
                    
                    // Chevron based on type
                    chevronView()
                }
                Spacer()
            }
            .frame(height: rowHeight)
        }
        // Force a full row refresh when the dot visibility changes.
        .id(config.showDot)
        .disabled(config.isDisabled || (config.toggleBinding != nil && config.onTap == nil))
        .opacity(config.isDisabled ? 0.5 : 1.0)
    }
    
    @ViewBuilder
    private func chevronView() -> some View {
        switch config.chevronType {
        case .right:
            AppIconView(icon: AppAssets.chevronRight, size: IconSize(width: 22, height: 22))
                .foregroundColor(theme.statusIconPrimary)
                .accessibilityHidden(true)
        case .upDown:
            AppIconView(icon: AppAssets.chevronUpDown, size: IconSize(width: 22, height: 22))
                .foregroundColor(theme.statusIconSecondary)
                .accessibilityHidden(true)
        case .loading:
            ProgressView()
                .progressViewStyle(.circular)
                .frame(width: 22, height: 22)
                .tint(theme.statusIconSecondary)
                .accessibilityHidden(true)
        case .none:
            EmptyView()
        }
    }
    
    private func actionLabelText(_ text: String, isDestructive: Bool = false) -> some View {
        Text(text)
            .fontOpenSans(.body2)
            .foregroundColor(isDestructive ? theme.textError : theme.textBody)
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
                title: "Default Row"
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Value",
                value: "Enabled"
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Up/Down Chevron",
                chevronType: .upDown
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row without Chevron",
                chevronType: .none
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Row with Dot",
                showDot: true
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Toggle Row",
// swiftlint:disable:next multiline_arguments
                chevronType: .none, toggleBinding: $toggleState
            ) { })
            
            ActionListItemView(config: ActionListItemConfig(
                title: "Destructive Row",
// swiftlint:disable:next multiline_arguments
                chevronType: .none, isDestructive: true
            ) { })
            .listRowInsets()
        }
    }
    .listStyle(.insetGrouped)
}
