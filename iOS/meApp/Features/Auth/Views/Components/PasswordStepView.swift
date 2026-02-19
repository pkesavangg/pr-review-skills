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
                    focusedField: $focusedField,
                    onCommit: {
                        signupStore.touchAndValidate(field: .password)
                        focusedField = .confirmPassword // Move focus to confirm password field
                    },
                    onEditingChanged: { isEditing in
                        signupStore.handleEditingChanged(isEditing, field: .password)
                    }
                )
                
                AppInputField(
                    config: TextInputConfig(
                        label: labels.confirmPassword,
                        inputType: .password,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.confirmPassword),
                        focusField: .confirmPassword
                    ),
                    value: $signupStore.signupForm.confirmPassword.value,
                    focusedField: $focusedField,
                    onCommit: {
                        signupStore.touchAndValidate(field: .confirmPassword)
                        focusedField = .zipCode // Move focus to zip code field
                    },
                    onEditingChanged: { isEditing in
                        signupStore.handleEditingChanged(isEditing, field: .confirmPassword)
                    }
                )
                
                AppInputField(
                    config: TextInputConfig(
                        label: labels.zipCode,
                        inputType: .text,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.zipcode),
                        focusField: .zipCode
                    ),
                    value: $signupStore.signupForm.zipcode.value,
                    focusedField: $focusedField,
                    onCommit: {
                        signupStore.touchAndValidate(field: .zipCode)
                        focusedField = nil // Clear focus
                        
                        if signupStore.isNextEnabled {
                            Task {
                                await signupStore.createUser()
                            }
                        }
                    },
                    onEditingChanged: { isEditing in
                        signupStore.handleEditingChanged(isEditing, field: .zipCode)
                    }
                )
            }
            .padding(.top, .spacingLG)
            Spacer()
            
            // Legal text and links
            VStack(spacing: .spacingXS) {
                HStack(spacing: 4) {
                    Spacer()
                    Text(passwordStepLang.termsAndPrivacyText.lowercased())
                        .fontOpenSans(.subHeading2)
                        .foregroundColor(theme.textBody)
                    Spacer()
                }
                LegalLinksRow(
                    termsLabel: passwordStepLang.termsOfService,
                    andLabel: passwordStepLang.andText,
                    privacyLabel: passwordStepLang.privacyPolicy,
                    termsURL: legalUrls.termsOfService,
                    privacyURL: legalUrls.privacyPolicy
                )
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively) // Dismiss keyboard when dragging
        .onTapGesture {
            hideKeyboard()
        }
    }
}

#Preview {
    PasswordStepView(signupStore: SignupStore())
}
