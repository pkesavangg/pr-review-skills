//
//  CustomizeSettingsView.swift
//  meApp
//
//  Created by Cursor AI on 12/07/25.
//

import SwiftUI

/// View for customizing scale settings during BtWifi setup flow.
struct CustomizeSettingsView: View {
    // MARK: - Environment
    @Environment(\.appTheme) private var theme
    @EnvironmentObject private var setupStore: BtWifiScaleSetupStore
    
    // MARK: - Constants
    private let strings = BtWifiScaleSetupStrings.CustomizeSettingsStrings.self
    private let appAssets = AppAssets.self

    var body: some View {
            VStack(alignment: .leading) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(strings.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.center)
                    
                    Text(strings.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                        .multilineTextAlignment(.center)
                }
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(alignment: .leading, spacing: 0) {
                        VStack(alignment: .leading, spacing: .spacingLG) {
                            // Settings Items
                            VStack(spacing: .spacingSM) {
                                ForEach(CustomizeSettingsItem.allCases, id: \.rawValue) { item in
                                    settingsItemView(item: item)
                                }
                            }
                            Spacer()
                        }
                    }
                }
            }
            .padding(.top, .spacingLG)
            .background(theme.backgroundSecondary)
    }
    
    /// Creates a settings item view with icon, text, and checkmark.
    private func settingsItemView(item: CustomizeSettingsItem) -> some View {
        let isSelected = setupStore.isCustomizeItemSelected(item.rawValue)
        return Button {
            setupStore.setCustomizationPage(item.customizeSettingsType)
            setupStore.addSelectedCustomizeItem(item.rawValue)
        } label: {
            VStack(alignment: .leading) {
                HStack(spacing: .spacingSM) {
                    VStack(alignment: .leading, spacing: .spacingSM) {
                        HStack(spacing: .spacingXS) {
                            AppIconView(icon: item.icon)
                                .foregroundColor(theme.actionPrimary)
                                .accessibilityHidden(true)
                            Text(item.title)
                                .fontOpenSans(.heading5)
                                .foregroundColor(theme.textHeading)
                                .multilineTextAlignment(.leading)
                        }
                        Text(item.subtitle)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                            .multilineTextAlignment(.leading)
                    }
                    Spacer()
                    let icon = isSelected ? AppAssets.filledTickCircle : AppAssets.chevronRight
                    AppIconView(icon: icon)
                        .foregroundColor(theme.actionPrimary)
                        .accessibilityHidden(true)
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingMD)
            }
            .background(theme.backgroundPrimary)
            .clipShape(RoundedRectangle(cornerRadius: .radiusSM))
        }
        .accessibilityLabel("\(item.title). \(item.subtitle)")
        .accessibilityHint(isSelected ? BtWifiScaleSetupStrings.A11y.settingsItemDoneHint : BtWifiScaleSetupStrings.A11y.settingsItemHint)
    }
}

#Preview {
    CustomizeSettingsView()
        .environmentObject(BtWifiScaleSetupStore())
        .environmentObject(Theme.shared)
}
