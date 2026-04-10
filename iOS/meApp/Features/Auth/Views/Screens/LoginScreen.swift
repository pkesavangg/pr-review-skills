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
    @State private var focusedField: FocusField?
    @State private var keyboardHeight: CGFloat = 0
    
    /// Optional e-mail address passed from previous screen to pre-populate the form
    var prefilledEmail: String?
    var isFromAccountSwitching: Bool = false
    
    let labels = InputFieldLabels.self
    let commonLang = CommonStrings.self
    let lang = LoginScreenStrings.self
    let legalStrings = LegalStrings.self
    
    var body: some View {
        VStack {
            NavbarHeaderView(
                title: isFromAccountSwitching ? commonLang.logIn.capitalized : "",
                leadingContent: {
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    Button {
                        store.openHelp()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 24, height: 24))
                            .foregroundColor(theme.statusIconPrimary)
                    }
                },
                onLeadingTap: {
                    store.handleExit(router: isFromAccountSwitching ? nil : router)
                },
                onTrailingTap: { },
                canShowBorder: isFromAccountSwitching,
                canShowPresentationIndicator: isFromAccountSwitching,
                shouldShowBackground: false
            )
            
            GeometryReader { geometry in
                VStack {
                    ScrollView(.vertical, showsIndicators: false) {
                        VStack(alignment: .center, spacing: 0) {
                            
                            // Only show spacer when keyboard is not visible
                            if keyboardHeight == 0 {
                                Spacer()
                                    .frame(minHeight: geometry.size.height * 0.15)
                            }
                            
                            VStack(spacing: 0) {
                                Text(lang.welcomeBack)
                                    .fontOpenSans(.heading4)
                                    .foregroundColor(theme.textHeading)
                                    .frame(maxWidth: .infinity, alignment: .center)
                                    .padding(.bottom, .spacingXL)
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
                                        focusedField: $focusedField,
                                        accessibilityIdentifier: AccessibilityID.loginEmailField
                                    ) {
                                        store.setEmailTouched()
                                        focusedField = .password
                                    }
                                    .onChange(of: focusedField) { oldValue, newValue in
                                        // Mark email as touched when focus moves away from it
                                        if oldValue == .email && newValue != .email {
                                            store.setEmailTouched()
                                        }
                                    }
                                    
                                    // Password Input Field
                                    AppInputField(
                                        config: TextInputConfig(
                                            label: labels.password,
                                            placeholder: lang.passwordPlaceholder,
                                            inputType: store.showPassword ? .text : .password,
                                            submitLabel: .done,
                                            errorMessage: store.passwordError,
                                            focusField: .password
                                        ),
                                        value: $store.loginForm.password.value,
                                        focusedField: $focusedField,
                                        accessibilityIdentifier: AccessibilityID.loginPasswordField
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
                                    .onChange(of: focusedField) { oldValue, newValue in
                                        // Mark password as touched when focus moves away from it
                                        if oldValue == .password && newValue != .password {
                                            store.setPasswordTouched()
                                        }
                                    }
                                }
                                .padding(.bottom, .spacingSM)
                                
                                VStack(spacing: .spacingMD) {
                                    ButtonView(
                                        text: commonLang.logIn,
                                        type: .filledPrimary,
                                        size: .large,
                                        isDisabled: !store.isFormValid || store.isFormSubmitting
                                    ) {
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
                                    .accessibilityIdentifier(AccessibilityID.loginSubmitButton)

                                    ButtonView(
                                        text: lang.forgotPassword,
                                        type: .textPrimary,
                                        size: .small,
                                        isDisabled: false
                                    ) {
                                            focusedField = nil
                                            store.showPasswordResetPrompt()
                                        }
                                    .accessibilityIdentifier(AccessibilityID.loginForgotPasswordButton)
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
                            LegalLinksRow(
                                termsLabel: legalStrings.termsOfService,
                                andLabel: legalStrings.andText,
                                privacyLabel: legalStrings.privacyPolicy,
                                termsURL: AppConstants.LegalURLs.termsOfService,
                                privacyURL: AppConstants.LegalURLs.privacyPolicy
                            )
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
        .keyboardObserver(keyboardHeight: $keyboardHeight)
        .onTapGesture {
            focusedField = nil
            hideKeyboard()
        }
        .onAppear {
            store.isFromAccountSwitching = isFromAccountSwitching
            if isFromAccountSwitching {
                store.dismissAction = dismiss
                // Set up exit handler to dismiss the sheet
                store.onAccountSwitchingExit = {
                    dismiss()
                }
            } else {
                store.onLoginSuccess = { router.navigateBack() }
            }
            
            // Set up callback to clear focus when password reset alert is dismissed
            store.onPasswordResetAlertDismissed = {
                focusedField = nil
                hideKeyboard()
            }
            
            // Prefill email if provided
            store.prefillEmailIfNeeded(prefilledEmail)
        }
        .onChange(of: store.isPasswordResetAlertVisible) { oldValue, newValue in
            // When alert is dismissed (goes from true to false), ensure focus is cleared
            if oldValue && !newValue {
                focusedField = nil
                hideKeyboard()
            }
        }
        .accessibilityIdentifier(AccessibilityID.loginScreenRoot)
    }
}

#Preview {
    LoginScreen()
}
