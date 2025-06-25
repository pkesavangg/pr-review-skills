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
        
        VStack(alignment: .center, spacing: .spacingXS){
            
            Image(
                isFromAccountSwitching
                ? AppAssets.wgLogoDark
                : (themeManager.isDarkMode ? AppAssets.wgLogoDark : AppAssets.wgLogoLight) //TODO: Need to update the logo after UX confirmation
            )
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 214, height: 25)
            
            Text(CommonStrings.byGreaterGoods)
                .fontOpenSans(.subHeading1)
                .fontWeight(.regular)
                .foregroundColor(isFromAccountSwitching ? theme.textHeading : theme.actionInverse) //TODO: Need to update the color after UX confirmation
        }
    }
}


#Preview {
    LogoView()
        .environmentObject(Theme.shared)
}
