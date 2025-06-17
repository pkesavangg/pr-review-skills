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
    @StateObject private var store = LoginStore()
    @FocusState private var focusedField: FocusField?
    let labels = InputFieldLabels.self
    let commonLang = CommonStrings.self
    let lang = LoginScreenStrings.self
    
    // Bridge FocusState to Binding for AppInputField
    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }
    
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
                            errorMessage: store.isEmailValid || store.email.isEmpty ? nil : "Invalid email",
                            focusField: .email,
                            showsClearButton: false
                        ),
                        value: $store.email,
                        focusedField: focusBinding
                    ) {
                        focusedField = .email
                    }
                    // password Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.password,
                            placeholder: lang.passwordPlaceholder,
                            inputType: store.showPassword ? .text : .password,
                            submitLabel: .done,
                            errorMessage: store.isPasswordValid || store.password.isEmpty ? nil : "Password must be 6-50 characters"
                        ),
                        value: $store.password,
                        focusedField: focusBinding,
                    ) {
                        focusedField = nil
                    }
                }
                .padding(.vertical, .spacingMD)
                
                // Login Button
                ButtonView(text: commonLang.logIn, type: .primary, size: .regular, isDisabled: !store.isFormValid || store.isFormSubmitting, action: {
                    focusedField = nil
                    Task { await store.logIn() }
                })
                .padding(.bottom, .spacingSM)
                
                // Forgot Password
                ButtonView(text: lang.forgotPassword, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                    store.showPasswordResetPrompt()
                })
                
                Spacer()
                
                VStack(spacing: .spacingXS/2){
                    Text(lang.byLoggingIn)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.actionSecondary)
                    HStack(spacing: .spacingMD/2){
                        ButtonView(text: lang.termsOfService, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                            store.openTerms()
                        })
                        Text(lang.and)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.actionSecondary)
                        ButtonView(text: lang.privacyPolicy, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                            store.openPrivacy()
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
                    onTrailingTap: {
                        store.openHelp()
                    }
                )
            }
        }
        .navigationBarBackButtonHidden(true)
        // MARK: In-App Browser Presentation
        .inAppBrowser(
            url: store.browserURL ?? URL(string: "https://greatergoods.com")!,
            isPresented: Binding(
                get: { store.showPrivacyBrowser || store.showTermsBrowser || store.showHelpBrowser },
                set: { newValue in
                    if (!newValue) {
                        store.showPrivacyBrowser = false
                        store.showTermsBrowser = false
                        store.showHelpBrowser = false
                        store.browserURL = nil
                    }
                }
            )
        )
        .presentLoader(loaderData: Binding(
            get: { store.isLoading ? LoaderModel(text: "Loggin in...") : nil },
            set: { _ in }
        ))
    }
}

#Preview {
    LoginScreen()
}
