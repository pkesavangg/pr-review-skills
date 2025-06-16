//
//  LoginScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct LoginScreen: View {
    @EnvironmentObject var router: Router<AuthRoute>
    @Environment(\.appTheme) var theme
    @State var email: String = ""
    @State var password: String = ""
    @State var focusedField: FocusField?
    let labels = InputFieldLabels.self
    let commonLang = CommonStrings.self
    let lang = LoginScreenStrings.self
    
    var body: some View {
        ZStack {
            theme.backgroundSecondary
                .ignoresSafeArea()
            VStack (alignment: .center) {
                
                VStack (alignment: .leading) {
                    Text(lang.welcomeBack)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .padding(.top, .spacingLG)
                    
                    // email Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.email,
                            inputType: .email,
                            errorMessage: nil,
                            focusField: .email,
                            showsClearButton: false
                        ),
                        value: $email,
                        focusedField: $focusedField
                    ) {
                        focusedField = .email
                    }
                    
                    // password Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.password,
                            placeholder: lang.passwordPlaceholder,
                            inputType: .password,
                            submitLabel: .done,
                            errorMessage: nil
                        ),
                        value: $password,
                        focusedField: $focusedField
                    ) {
                        focusedField = .password
                    }
                }
                .padding(.vertical, .spacingMD)
                
                ButtonView(text: commonLang.logIn, type: .primary, size: .regular, isDisabled: true, action: {
                    // TODO: Add button action
                })
                    .padding(.bottom, .spacingSM)
                
                ButtonView(text: lang.forgotPassword, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                    // TODO: Add button action
                })
                
                Spacer()
                
                VStack(spacing: .spacingXS/2){
                    Text(lang.byLoggingIn)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.actionSecondary)
                    
                    HStack(spacing: .spacingMD/2){
                        ButtonView(text: lang.termsOfService, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                            // TODO: Add button action
                        })
                        
                        Text(lang.and)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.actionSecondary)
                        
                        ButtonView(text: lang.privacyPolicy, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                            // TODO: Add button action
                        })
                    }
                    
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .toolbar {
            ToolbarItem(placement: .principal) {
                NavbarHeaderView(
                    title: "",
                    leadingContent: { Image(AppAssets.xmark) },
                    trailingContent: { Image(AppAssets.helpCircle) },
                    onLeadingTap: {
                        router.navigateBack()
                    },
                    onTrailingTap: {}
                )
            }
        }
        .navigationBarBackButtonHidden(true)
    }
}

#Preview {
    LoginScreen()
}
