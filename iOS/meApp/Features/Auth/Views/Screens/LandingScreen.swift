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
    @StateObject private var router = Router<AuthRoute>()
    let lang = LandingScreenStrings.self
    let commonLang = CommonStrings.self
    
  var body: some View {
      RoutingView(stack: $router.stack) {
          ZStack {
              theme.actionPrimary
                  .ignoresSafeArea()

          VStack(alignment: .center) {
              
              Spacer()
                  .frame(minHeight: .spacing6XL)
              
              Text(lang.weightGurusNow)
                  .fontOpenSans(.subHeading1)
                  .foregroundColor(theme.backgroundPrimary)
                  .padding(.top, .spacing6XL)
              
              VStack(alignment: .center){
                  Text(lang.myEveryday)
                       .fontOpenSans(.heading2)
                       .foregroundColor(theme.backgroundPrimary)
                  Text(lang.health)
                       .fontOpenSans(.heading2)
                       .foregroundColor(theme.brandMeAppPrimary)
              }
              .padding(.top, .spacingSM)
              .padding(.horizontal,.spacingLG)
              
              ButtonView(text: lang.learnMore, type: .textSecondary, size: .large, isDisabled: false, action: {})
                  .padding(.top, .spacingXS)
                  .padding(.bottom,.spacing2XL)
              
              VStack(alignment: .center, spacing: .spacingSM){
                  ButtonView(text: lang.signUp, type: .filledSecondary, size: .large, isDisabled: false, action: {router.navigate(to: .signup)})
                  ButtonView(text: commonLang.logIn, type: .outlinedSecondary, size: .large, isDisabled: false, action: {
                      router.navigate(to: .login)
                  })
              }
              .padding(.bottom, .spacing6XL)
                                          
              Spacer()
                  .frame(minHeight: .spacing6XL)

                  VersionAndCopyrightView()
              }
          }
      }
      .environmentObject(router)
    }
}
