//
//  LoadingScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 13/06/25.
//

import SwiftUI

struct LoadingScreen: View {
    @Environment(\.appTheme) var theme
    @EnvironmentObject var themeManager: Theme
    @Environment(\.colorScheme) private var colorScheme
    let lang = LoadingScreenStrings.self
    var body: some View {
        ZStack {
            theme.actionPrimary
                .ignoresSafeArea()

            VStack(alignment: .center) {
                Spacer()
                
                logoView
                    .frame(width: 234, height: 78)

                HStack(alignment: .center, spacing: .spacingXS) {
                    Text(lang.loading)
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.backgroundPrimary)
                    
                    LoadingDotsView(color: theme.brandMeAppPrimary)
                        .offset(y: .spacingXS)
                    
                }
                .padding(.top, .spacingXL)

                Spacer()

                VersionAndCopyrightView()
            }
        }
    }

    private var logoView: some View {
        Image(colorScheme == .dark ? AppAssets.meLogoDark : AppAssets.meLogoLight)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .frame(width: 234, height: 78)
            .shadow(color: theme.actionPrimary.opacity(0.1), radius: 10, x: 0, y: 5)
            .scaleEffect(1)
            .opacity(1)
            .animation(.spring(response: 0.5, dampingFraction: 0.7), value: colorScheme)
    }
}

#Preview {
    LoadingScreen()
        .environmentObject(Theme.shared)
}
