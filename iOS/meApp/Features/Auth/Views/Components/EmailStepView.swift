//
//  EmailStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/06/25.
//


import SwiftUI

// MARK: - Email Step View
/// View for the email step in the signup process.
/// This view allows the user to enter their email address.
struct EmailStepView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var signupStore: SignupStore
    @State var focusedField: FocusField?
    var emailStepLang = SignupStrings.EmailStep.self
    var labels = InputFieldLabels.self
    
    var body: some View {
        SignupStepWrapper(title: emailStepLang.title, subtitle: emailStepLang.subtitle) {
            VStack(spacing: 4) {
                // Email Input Field
                AppInputField(
                    config: TextInputConfig(
                        label: labels.email,
                        inputType: .email,
                        errorMessage: signupStore.getError(for: signupStore.signupForm.email),
                        focusField: .email
                    ),
                    value: $signupStore.signupForm.email.value,
                    focusedField: $focusedField,
                    onCommit: {
                        focusedField = nil
                        if signupStore.isNextEnabled {
                            signupStore.moveToNextStep()
                        }
                    },
                    onEditingChanged: { isEditing in
                        signupStore.handleEditingChanged(isEditing, field: .email)
                    }
                )
                .onChange(of: focusedField) { oldValue, newValue in
                    if oldValue == .email && newValue != .email {
                        signupStore.touchAndValidate(field: .email)
                    }
                }
            }
            .padding(.top, .spacingLG)
            Spacer()
        }
        .scrollDismissesKeyboard(.interactively) // Dismiss keyboard when dragging
        .onTapGesture {
            hideKeyboard()
        }
    }
}


#Preview(body: {
    EmailStepView(signupStore: SignupStore())
})
