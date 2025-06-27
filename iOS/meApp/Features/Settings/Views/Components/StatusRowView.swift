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
    let foregroundColor: Color?
    
    @Environment(\.appTheme) private var theme
    
    init(
        iconName: String,
        label: String,
        statusText: String,
        foregroundColor: Color? = nil
    ) {
        self.iconName = iconName
        self.label = label
        self.statusText = statusText
        self.foregroundColor = foregroundColor
    }
    
    var body: some View {
        HStack(alignment: .center, spacing: .spacingXS) {
            AppIconView(icon: iconName, size: IconSize(width: 20, height: 20))
                .foregroundColor(foregroundColor ?? theme.actionPrimary)
            
            Text(label)
                .fontOpenSans(.body3)
                .foregroundColor(foregroundColor ?? theme.textHeading)
            
            Text(statusText)
                .fontOpenSans(.body3)
                .foregroundColor(foregroundColor ?? theme.textHeading)
        }
    }
}
