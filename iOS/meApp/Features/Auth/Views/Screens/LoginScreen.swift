//
//  LoginScreen.swift
//  meApp
//
//  Created by Lakshmi Priya on 16/06/25.
//

import SwiftUI

struct LoginScreen: View {
    @EnvironmentObject var router: Router<AuthRoute>
    @Environment(\.dismiss) var dismiss
    @Environment(\.appTheme) var theme
    @StateObject private var store = LoginStore()
    @FocusState private var focusedField: FocusField?
    @State private var keyboardHeight: CGFloat = 0
    
    /// Optional e-mail address passed from previous screen to pre-populate the form
    var prefilledEmail: String? = nil
    var isFromAccountSwitching: Bool = false
    
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
        VStack {
            NavbarHeaderView(
                title: isFromAccountSwitching ? commonLang.logIn.capitalized : "",
                leadingContent: { Image(AppAssets.xmark) },
                trailingContent: {
                    Button {
                        store.openHelp()
                    } label: {
                        Image(AppAssets.helpCircle)
                    }
                },
                onLeadingTap: {
                    if isFromAccountSwitching {
                        store.handleExit()
                    } else {
                        router.navigateBack()
                    }
                },
                onTrailingTap: {  },
                canShowPresentationIndicator: isFromAccountSwitching,
                shouldShowBackground: false
            )
            
            GeometryReader { geometry in
                VStack {
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .center) {
                            VStack(spacing: 0) {
                                Text(lang.welcomeBack)
                                    .fontOpenSans(.heading4)
                                    .foregroundColor(theme.textHeading)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.bottom, .spacingLG)
                                VStack {
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
                                            Task {
                                                hideKeyboard()
                                                await store.logIn()
                                            }
                                        }
                                    }
                                }
                                .padding(.bottom, .spacingSM)
                                
                                VStack(spacing: .spacingSM) {
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
                                                Task {
                                                    hideKeyboard()
                                                    await store.logIn()
                                                }
                                            }
                                        }
                                    )
                                    
                                    ButtonView(
                                        text: lang.forgotPassword,
                                        type: .textPrimary,
                                        size: .small,
                                        isDisabled: false,
                                        action: { store.showPasswordResetPrompt() }
                                    )
                                }
                            }
                            .padding(.top, .spacingLG)
                            
                            
                            // Only show spacer when keyboard is not visible
                            if keyboardHeight == 0 {
                                Spacer()
                                    .frame(minHeight: geometry.size.height * 0.15)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .frame(minHeight: keyboardHeight > 0 ? 0 : geometry.size.height - 100)
                    }
                    .scrollDismissesKeyboard(.interactively)
                    
                    // Footer - only visible when keyboard is not shown
                    if keyboardHeight == 0 {
                        VStack(spacing: .spacingXS / 2) {
                            Text(lang.byLoggingIn)
                                .fontOpenSans(.subHeading2)
                                .foregroundColor(theme.actionSecondary)
                            HStack {
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
                        .padding(.bottom, .spacingSM)
                    }
                }
            }
            .padding(.horizontal, .spacingSM)
        }
        .background(theme.backgroundSecondary.ignoresSafeArea())
        .navigationBarBackButtonHidden(true)
        .inAppBrowser(
            url: store.presentingBrowserURL,
            isPresented: store.isBrowserPresented
        )
        .presentLoader(loaderData: store.loaderData)
        .presentAlert(alertData: $store.alertData)
        .keyboardObserver(keyboardHeight: $keyboardHeight)
        .onAppear {
            store.isFromAccountSwitching = isFromAccountSwitching
            if isFromAccountSwitching {
                store.dismissAction = dismiss
            } else {
                store.onLoginSuccess = { router.navigateBack() }
            }
            
            // Prefill email if provided
            store.prefillEmailIfNeeded(prefilledEmail)
        }
    }
}

#Preview {
    LoginScreen()
}
