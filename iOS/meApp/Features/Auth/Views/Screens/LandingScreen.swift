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

              LogoView()
                  .padding(.bottom, 55)
              
              VStack(alignment: .center, spacing: .spacingSM){                 
                  ButtonView(text: commonLang.logIn, type: .filledSecondary, size: .large, isDisabled: false, action: {router.navigate(to: .login)})
                  ButtonView(text: lang.signUp, type: .outlinedSecondary, size: .large, isDisabled: false, action: {router.navigate(to: .signup)})
              }
              .padding(.bottom, .spacing6XL)
                                          
              Spacer()
                  .frame(minHeight: .spacing6XL)

                  VersionView()
              }
          }
      }
      .environmentObject(router)
    }
}

