//
//  LandingScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct LandingScreen: View {
    @Environment(\.appTheme) var theme
    @EnvironmentObject var themeManager: Theme
    @Environment(\.colorScheme) private var colorScheme
    @EnvironmentObject var router: Router<AuthRoute>
    let lang = LandingScreenStrings.self
    
  var body: some View {
      ZStack {
          theme.actionPrimary
              .ignoresSafeArea()

          VStack(alignment: .center) {
              Text(lang.weightGurusNow)
                  .fontOpenSans(.subHeading1)
                  .foregroundColor(theme.backgroundPrimary)
                  .padding(.top, 171)
              
              VStack(alignment: .center){
                  Text(lang.myEveryday)
                       .fontOpenSans(.heading2)
                       .foregroundColor(theme.backgroundPrimary)
                  Text(lang.health)
                       .fontOpenSans(.heading2)
                       .foregroundColor(theme.brandMeAppPrimary)
              }
              .padding(.top,16)
              .padding(.horizontal,31)
              
              ButtonView(text: lang.learnMore, type: .linkWhiteDefault, size: .regular, isDisabled: false, action: {})
              .padding(.top,10)
              .padding(.horizontal, 118)
              .padding(.bottom,50)
              
              VStack(alignment: .center, spacing: 16){
                  ButtonView(text: lang.signUp, type: .secondary, size: .regular, isDisabled: false, action: {router.navigate(to: .signup)})
                  ButtonView(text: lang.logIn, type: .secondaryInverse, size: .regular, isDisabled: false, action: {
                      // TODO: Replace with navigation to the login view
                  })
              }
              .padding(.bottom, 150)
              .padding(.horizontal,105)
                                          
              Spacer()

              VersionAndCopyrightView()
          }
      }
    }
}
