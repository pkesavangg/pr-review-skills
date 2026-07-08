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
            theme.backgroundSecondary
                .ignoresSafeArea()

            VStack(alignment: .center) {
                Spacer()

                MeHealthLogoCard()

                HStack(alignment: .center) {
                    Text(lang.loading.lowercased())
                        .fontOpenSans(.subHeading1)
                        .foregroundColor(theme.textBody)

                    LoadingDotsView(color: theme.textBody)
                        .offset(y: 1)
                        .accessibilityHidden(true)

                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(lang.accLoadingLabel)
                .padding(.top, .spacing4XL)

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
