//
//  NameStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

struct NameStepView: View {
    @Environment(\.appTheme) private var theme
    @ObservedObject var signupStore: SignupStore
    @State var focusedField: FocusField?
    var nameStepLang = SignupStrings.NameStep.self
    var labels = InputFieldLabels.self
    
    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading, spacing: .spacingMD) {
                Text(nameStepLang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                
                Text(nameStepLang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
                
                VStack(spacing: 24) {
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.firstName,
                            inputType: .text,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.firstName),
                            focusField: .firstName
                        ),
                        value: $signupStore.signupForm.firstName.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = .lastName
                    }
                    
                    AppInputField(
                        config: TextInputConfig(
                            label: labels.lastName,
                            inputType: .text,
                            errorMessage: signupStore.getError(for: signupStore.signupForm.lastName),
                            focusField: .lastName
                        ),
                        value: $signupStore.signupForm.lastName.value,
                        focusedField: $focusedField
                    ) {
                        focusedField = nil
                        if signupStore.signupForm.firstName.isValid && signupStore.signupForm.lastName.isValid {
                            signupStore.moveToNextStep()
                        }
                    }
                }
            }
            .dismissKeyboardOnDrag()
        }
    }
}

#Preview {
    NameStepView(signupStore: SignupStore())
        .padding()
} 
