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
                
                LogoView()
                    .frame(width: 214, height: 25)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(lang.accLogoLabel)

                HStack(alignment: .center) {
                    Text(lang.loading.lowercased())
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.backgroundPrimary)

                    LoadingDotsView(color: theme.backgroundPrimary)
                        .offset(y: 1)
                        .accessibilityHidden(true)

                }
                .accessibilityLabel(lang.accLoadingLabel)
                .padding(.top, .spacing3XL)

                Spacer()

                VersionView()
            }
        }
    }

}

#Preview {
    LoadingScreen()
        .environmentObject(Theme.shared)
}
