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
    let legalStrings = LegalStrings.self
    private var focusBinding: Binding<FocusField?> {
        Binding(
            get: { focusedField },
            set: { focusedField = $0 }
        )
    }

    var body: some View {
        ZStack {
            theme.backgroundSecondary.ignoresSafeArea()
            VStack(alignment: .center) {

                NavbarHeaderView(
                    title: "",
                    leadingContent: { Image(AppAssets.xmark) },
                    trailingContent: { Image(AppAssets.helpCircle) },
                    onLeadingTap: { router.navigateBack() },
                    onTrailingTap: { store.openHelp() }
                )
                .padding(.bottom, .spacingLG)

                VStack(alignment: .leading) {
                    Text(lang.welcomeBack)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)

                    // Email Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.email,
                            inputType: .email,
                            errorMessage: store.emailError,
                            focusField: .email
                        ),
                        value: $store.loginForm.email.value,
                        focusedField: focusBinding
                    ) {
                        store.setEmailTouched()
                        focusedField = .password
                    }
                    // Password Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.password,
                            placeholder: lang.passwordPlaceholder,
                            inputType: store.showPassword ? .text : .password,
                            submitLabel: .done,
                            errorMessage: store.passwordError
                        ),
                        value: $store.loginForm.password.value,
                        focusedField: focusBinding
                    ) {
                        store.setPasswordTouched()
                        focusedField = nil
                        if store.isFormValid {
                            Task { await store.logIn() }
                        }
                    }
                }
                .padding(.vertical, .spacingMD)

                ButtonView(
                    text: commonLang.logIn,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: !store.isFormValid || store.isFormSubmitting,
                    action: {
                        focusedField = nil
                        store.loginForm.email.markAsDirty()
                        store.loginForm.password.markAsDirty()
                        if store.isFormValid {
                            Task { await store.logIn() }
                        }
                    }
                )
                .padding(.bottom, .spacingSM)

                ButtonView(
                    text: lang.forgotPassword,
                    type: .textPrimary,
                    size: .small,
                    isDisabled: false,
                    action: { store.showPasswordResetPrompt() }
                )

                Spacer()

                VStack(spacing: .spacingXS/2) {
                    Text(lang.byLoggingIn)
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.actionSecondary)
                    HStack() {
                        ButtonView(
                            text: legalStrings.termsOfService,
                            type: .textPrimary,
                            size: .small,
                            isDisabled: false,
                            action: { store.openTerms() }
                        )
                        Text(legalStrings.andText)
                            .fontOpenSans(.subHeading2)
                            .foregroundColor(theme.actionSecondary)
                        ButtonView(
                            text: legalStrings.privacyPolicy,
                            type: .textPrimary,
                            size: .small,
                            isDisabled: false,
                            action: { store.openPrivacy() }
                        )
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .navigationBarBackButtonHidden(true)
        .inAppBrowser(
            url: store.presentingBrowserURL,
            isPresented: store.isBrowserPresented
        )
        .presentLoader(loaderData: store.loaderData)
        .presentAlert(alertData: $store.alertData)
        .onAppear {
            store.onLoginSuccess = { router.navigateBack() }
        }
    }
}

#Preview {
    LoginScreen()
}
