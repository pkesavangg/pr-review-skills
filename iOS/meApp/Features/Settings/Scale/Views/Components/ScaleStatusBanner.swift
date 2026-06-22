//
//  SettingsBannerView.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

struct ScaleStatusBanner: View {
    let type: ScaleStatusBannerType
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        HStack {
            AppIconView(icon: type.iconName, size: IconSize(width: 24, height: 24))
                .foregroundColor(iconColor)
                .accessibilityHidden(true)
            
            Text(type.message)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textBody)
            
            Spacer()
            
            Button(action: type.onTap) {
                Text(type.actionTitle.uppercased())
                    .fontWeight(.bold)
                    .fontOpenSans(DevicePlatform.isMiniPhone ? .button2 : .button1)
                    .foregroundColor(theme.statusIconPrimary)
            }
        }
        .background(theme.backgroundPrimary)
        .cornerRadius(10)
    }
    
    private var iconColor: Color {
        switch type {
        case .setupIncomplete: return theme.statusError
        case .weightOnly: return theme.statusIconPrimary
        }
    }
}

// MARK: - Previews for both examples

struct SettingsBannerView_Previews: PreviewProvider {
    static var previews: some View {
        VStack(spacing: 18) {
            ScaleStatusBanner(type: .weightOnly { })
            ScaleStatusBanner(type: .setupIncomplete { })
            Spacer()
        }
        .padding()
        .background(Color(.systemGray6))
        .previewLayout(.sizeThatFits)
    }
}
