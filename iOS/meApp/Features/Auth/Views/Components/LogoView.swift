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
    
    var body: some View {
        
        VStack(alignment: .center, spacing: .spacingXS){
            
            Image(colorScheme == .dark ? AppAssets.wgLogoDark : AppAssets.wgLogoLight)
                .resizable()
                .aspectRatio(contentMode: .fit)
                .frame(width: 214, height: 25)
            
            Text(CommonStrings.byGreaterGoods)
                .fontOpenSans(.subHeading1)
                .fontWeight(.regular)
                .foregroundColor(theme.actionInverse)
        }
    }
}


#Preview {
    LogoView()
}
