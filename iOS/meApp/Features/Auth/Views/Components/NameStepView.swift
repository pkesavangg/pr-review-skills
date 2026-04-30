//
//  NameStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

// MARK: NameStepView
/// This view is responsible for the name step of the signup process.
struct NameStepView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var signupStore: SignupStore
    @State var focusedField: FocusField?
    let nameStepLang = SignupStrings.NameStep.self
    let labels = InputFieldLabels.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(nameStepLang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    
                    Text(nameStepLang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }
                
                VStack(spacing: 4) {
                    // First Name Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.firstName,
                            inputType: .text,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.firstName),
                            focusField: .firstName
                        ),
                        value: $signupStore.signupForm.firstName.value,
                        focusedField: $focusedField,
                        onCommit: {
                            signupStore.touchAndValidate(field: .firstName)
                            focusedField = .lastName
                        },
                        onEditingChanged: { isEditing in
                            signupStore.handleEditingChanged(isEditing, field: .firstName)
                        }
                    )
                    
                    // Last Name Input Field
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.lastName,
                            inputType: .text,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.lastName),
                            focusField: .lastName
                        ),
                        value: $signupStore.signupForm.lastName.value,
                        focusedField: $focusedField,
                        onCommit: {
                            signupStore.touchAndValidate(field: .lastName)
                            focusedField = nil
                            if signupStore.isNextEnabled {
                                signupStore.moveToNextStep()
                            }
                        },
                        onEditingChanged: { isEditing in
                            signupStore.handleEditingChanged(isEditing, field: .lastName)
                        }
                    )
                }
                .padding(.top, .spacingLG)
                Spacer()
            }
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively) // Dismiss keyboard when dragging
        .onTapGesture {
            hideKeyboard()
        }
    }
}

#Preview {
    NameStepView(signupStore: SignupStore())
        .padding()
} 
