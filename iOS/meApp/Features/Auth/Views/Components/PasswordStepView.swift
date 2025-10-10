//
//  PasswordStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 13/06/25.
//

import SwiftUI

// MARK: - PasswordStepView
/// A view for the password step in the signup process.
/// This view allows users to enter their password, confirm it, and provide a zip code.
struct PasswordStepView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var signupStore: SignupStore
    @State var focusedField: FocusField?
    @State private var showTerms = false
    @State private var showPrivacy = false
    let passwordStepLang = SignupStrings.PasswordStep.self
    let labels = InputFieldLabels.self
    let legalUrls = AppConstants.LegalURLs.self
    
    var body: some View {
        SignupStepWrapper(title: passwordStepLang.title, subtitle: passwordStepLang.subtitle) {
            VStack(spacing: 4) {
                // Password Input Field
                AppInputField(
                    config: TextInputConfig(
                        label: labels.password,
                        inputType: .password,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.password),
                        focusField: .password
                    ),
                    value: $signupStore.signupForm.password.value,
                    focusedField: $focusedField
                ) {
                    focusedField = .confirmPassword // Move focus to confirm password field
                }
                
                AppInputField(
                    config: TextInputConfig(
                        label: labels.confirmPassword,
                        inputType: .password,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.confirmPassword),
                        focusField: .confirmPassword
                    ),
                    value: $signupStore.signupForm.confirmPassword.value,
                    focusedField: $focusedField
                ) {
                    focusedField = .zipCode // Move focus to zip code field
                }
                
                AppInputField(
                    config: TextInputConfig(
                        label: labels.zipCode,
                        inputType: .text,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.zipcode),
                        focusField: .zipCode
                    ),
                    value: $signupStore.signupForm.zipcode.value,
                    focusedField: $focusedField
                ) {
                    focusedField = nil // Clear focus
                    
                    if signupStore.isNextEnabled {
                        Task {
                            await signupStore.createUser()
                        }
                    }
                }
            }
            .padding(.top, .spacingLG)
            Spacer()
            
            // Legal text and links
            VStack(spacing: .spacingXS) {
                HStack(spacing: 4) {
                    Spacer()
                    Text(passwordStepLang.termsAndPrivacyText.lowercased())
                        .fontOpenSans(.link2)
                        .foregroundColor(theme.textBody)
                    Spacer()
                }
                HStack {
                    Button {
                        showTerms = true
                    } label: {
                        Text(passwordStepLang.termsOfService)
                            .fontOpenSans(.link2)
                            .foregroundColor(theme.actionPrimary)
                    }
                    .inAppBrowser(url: legalUrls.termsOfService, isPresented: $showTerms)
                    
                    Text(passwordStepLang.andText)
                        .fontOpenSans(.link2)
                        .foregroundColor(theme.textBody)
                    
                    Button {
                        showPrivacy = true
                    } label: {
                        Text(passwordStepLang.privacyPolicy)
                            .fontOpenSans(.link2)
                            .foregroundColor(theme.actionPrimary)
                    }
                    .inAppBrowser(url: legalUrls.privacyPolicy, isPresented: $showPrivacy)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively) // Dismiss keyboard when dragging
    }
}

#Preview {
    PasswordStepView(signupStore: SignupStore())
}
