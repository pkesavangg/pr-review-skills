//
// ToggleListItem.swift
// meApp
//
// Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ToggleListItem: View {
    @Environment(\.appTheme) var theme
    @Binding var isOn: Bool
    let text: String?
    let icon: String?
    let showReorderHandle: Bool
    let isDisabled: Bool
    let showDivider: Bool
    
    init(
        isOn: Binding<Bool>,
        text: String? = nil,
        icon: String? = nil,
        showReorderHandle: Bool = false,
        isDisabled: Bool = false,
        showDivider: Bool = true
    ) {
        self._isOn = isOn
        self.text = text
        self.icon = icon
        self.showReorderHandle = showReorderHandle
        self.isDisabled = isDisabled
        self.showDivider = showDivider
    }

    private var imageForegroundColor: Color {
        isDisabled
            ? theme.actionTertiaryDisabled
            : (isOn ? theme.actionPrimary : theme.statusIconSecondary)
    }

    private var textForegroundColor: Color {
        isOn
            ? theme.textBody
            : theme.statusIconSecondary
    }
    
    var body: some View {
        HStack(spacing: .spacingXS) {
            if let icon = icon {
                AppIconView(icon: icon, size: IconSize(width: 28, height: 28))
                    .foregroundColor(imageForegroundColor)
                    .padding(.leading, .spacingSM)
            }
            if let text = text {
                Text(text)
                    .fontOpenSans(.body2)
                    .foregroundColor(textForegroundColor)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, icon == nil ? .spacingXS : .spacingSM)
            }

            CustomToggleView(isOn: $isOn)
                .frame(width: 51, height: 31)
                .padding(.trailing, .spacingSM)
            
            if showDivider {
                HStack {
                    Divider()
                        .foregroundColor(theme.statusUtility)
                        .padding(.trailing, .spacingSM)
                }
            }
        }
        .frame(height: 48)
    }
}

struct ToggleListItem_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 20) {
            ToggleListItem(
                isOn: .constant(true),
                text: "Wi-Fi"
            )
            ToggleListItem(
                isOn: .constant(false),
                text: "Bluetooth",
                showDivider: false
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
