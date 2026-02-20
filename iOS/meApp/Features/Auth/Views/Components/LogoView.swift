//
//  LogoView.swift
//  meApp
//
//  Created by Lakshmi Priya on 20/06/25.
//

import SwiftUI

struct LogoView: View {
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    var isFromAccountSwitching = false
    
    var body: some View {
        
        VStack(alignment: .center, spacing: .spacingXS) {
            
            Image(
                isFromAccountSwitching
                ? (themeManager.isDarkMode ? AppAssets.wgLogoLight : AppAssets.wgLogoDark)
                : (themeManager.isDarkMode ? AppAssets.wgLogoDark : AppAssets.wgLogoLight)
            )
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 214, height: 25)
            
            Text(CommonStrings.byGreaterGoods.lowercased())
                .fontOpenSans(.subHeading1)
                .fontWeight(.regular)
                .foregroundColor(isFromAccountSwitching ? theme.actionSecondary : theme.actionInverse)
        }
    }
}

#Preview {
    LogoView()
        .environmentObject(Theme.shared)
}
