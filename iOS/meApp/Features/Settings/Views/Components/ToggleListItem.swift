//
//  ToggleListItem.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct ToggleListItem: View {
    @Environment(\.appTheme) var theme
    @Binding var isOn: Bool
    let text: String?
    let icon: String?
    let showReorderHandle: Bool
    let isDisabled: Bool
    
    init(
        isOn: Binding<Bool>,
        text: String? = nil,
        icon: String? = nil,
        showReorderHandle: Bool = false,
        isDisabled: Bool = false
    ) {
        self._isOn = isOn
        self.text = text
        self.icon = icon
        self.showReorderHandle = showReorderHandle
        self.isDisabled = isDisabled
    }
    
    private var imageForegroundColor: Color {
        if isDisabled {
            return theme.actionTertiaryDisabled
        } else if !isOn {
            return theme.statusIconSecondary
        } else {
            return theme.actionPrimary
        }
    }
    private var textForegroundColor: Color {
        if !isOn {
            return theme.statusIconSecondary
        } else {
            return theme.textBody
        }
    }
    private var opacity: Double {
        isDisabled ? 0.5 : 1.0
    }
    private var shouldShowReorderHandle: Bool {
        showReorderHandle && isOn
    }
    
    var body: some View {
        HStack(spacing: .spacingXS) {
            if let icon = icon {
                AppIconView(icon: icon, size: IconSize(width: 28, height: 28))
                    .foregroundColor(imageForegroundColor)
                    .opacity(opacity)
                    .padding(.leading, .spacingSM)
            }
            if let text = text {
                Text(text)
                    .fontOpenSans(.body2)
                    .foregroundColor(textForegroundColor)
                    .opacity(opacity)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, icon == nil ? .spacingXS : .spacingSM)
            }
            
            CustomToggleView(isOn: $isOn)
                .if(!shouldShowReorderHandle) { view in
                    view.padding(.trailing, .spacingSM)
                }
            
            
            if shouldShowReorderHandle {
                Divider()
                    .foregroundColor(theme.statusUtility)
                
                Image(systemName: "line.3.horizontal")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 22, height: 22)
                    .foregroundColor(isDisabled ? theme.actionTertiaryDisabled : theme.statusIconSecondary)
                    .fontWeight(.semibold)
                    .padding(.vertical, 11)
                    .padding(.horizontal, 13.5)
                    .opacity(opacity)
            }
        }
        .frame(height: 48)
    }
}

struct ToggleListItem_Previews: PreviewProvider {
    struct ReorderCaseDemo: View {
        @State var isOn = false
        
        var body: some View {
            VStack(spacing: 8) {
                Text("Reorder Handle only shows when toggle is ON")
                    .font(.caption)
                    .foregroundColor(.gray)
                ToggleListItem(
                    isOn: $isOn,
                    text: "Reorder Example",
                    showReorderHandle: true,
                    isDisabled: false
                )
                .padding(.bottom, 2)
                Text("Toggle is \(isOn ? "ON (handle shows)" : "OFF (handle hidden)")")
                    .font(.caption2)
            }
            .padding()
        }
    }
    
    static var previews: some View {
        VStack(spacing: 20) {
            // Only text
            ToggleListItem(
                isOn: .constant(true),
                text: "Wi-Fi"
            )
            
            // Text + image
            ToggleListItem(
                isOn: .constant(false),
                text: "Bluetooth",
            )
            
            // Only image
            ToggleListItem(
                isOn: .constant(true),
            )
            
            // With reorder handle (will only show when toggle is ON)
            ReorderCaseDemo()
            
            // Disabled
            ToggleListItem(
                isOn: .constant(false),
                text: "Location",
                isDisabled: true
            )
            
            // Disabled with reorder handle (won't show since toggle is OFF)
            ToggleListItem(
                isOn: .constant(false),
                text: "Airplane Mode",
                showReorderHandle: true,
                isDisabled: true
            )
            
            // Custom icon example (star)
            ToggleListItem(
                isOn: .constant(true),
                text: "Premium",
            )
        }
        .padding()
        .previewLayout(.sizeThatFits)
    }
}
