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
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(emailStepLang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    
                    Text(emailStepLang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }
                
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
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                        if signupStore.isNextEnabled {
                            signupStore.moveToNextStep()
                        }
                    }
                }
                .padding(.top, .spacingLG)
                Spacer()
            }
            .dismissKeyboardOnDrag() // Dismiss keyboard when dragging
        }
    }
}


#Preview(body: {
    EmailStepView(signupStore: SignupStore())
})
