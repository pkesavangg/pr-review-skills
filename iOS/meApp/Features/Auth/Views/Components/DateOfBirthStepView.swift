//
//  DateOfBirthStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//


import SwiftUI

// MARK: DateOfBirthStepView
/// This view is responsible for the date of birth step of the signup process.
struct DateOfBirthStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    var dateOfBirthStepLang = SignupStrings.DateOfBirthStep.self
    let maxDate = DateTimeTools.minAllowedBirthdayDate()
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(dateOfBirthStepLang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    
                    Text(dateOfBirthStepLang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }
                
                VStack(alignment: .leading, spacing: 4) {
                    Text(dateOfBirthStepLang.birthdayLabel)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                    
                    // TODO: Add a custom date picker with a minimum age limit of 18 years
                    DatePicker(
                        "",
                        selection: $signupStore.signupForm.birthday.value,
                        in: ...maxDate,
                        displayedComponents: .date
                    )
                    .datePickerStyle(.wheel)
                    .labelsHidden()
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, .spacingLG)
            }
        }
    }
}

#Preview {
    DateOfBirthStepView(signupStore: SignupStore())
        .padding()
}
