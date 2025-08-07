//
//  HeartRateSettingRow.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct StatusRowView: View {
    let iconName: String
    let label: String
    let statusText: String
    let font: CustomTextStyle?
    let foregroundColor: Color?
    let iconColor: Color?
    
    @Environment(\.appTheme) private var theme
    
    init(
        iconName: String,
        label: String,
        statusText: String,
        font: CustomTextStyle? = nil,
        foregroundColor: Color? = nil,
        iconColor: Color? = nil
    ) {
        self.iconName = iconName
        self.label = label
        self.statusText = statusText
        self.font = font
        self.foregroundColor = foregroundColor
        self.iconColor = iconColor
    }
    
    var body: some View {
        HStack(alignment: .center, spacing: .spacingXS) {
            AppIconView(icon: iconName, size: IconSize(width: 20, height: 20))
                .foregroundColor(iconColor ?? theme.actionPrimary)
            
            Text(label)
                .fontOpenSans(font ?? .body3)
                .foregroundColor(foregroundColor ?? theme.textHeading)
            
            Text(statusText)
                .fontOpenSans(font ?? .body3)
                .foregroundColor(foregroundColor ?? theme.textHeading)
        }
    }
}
