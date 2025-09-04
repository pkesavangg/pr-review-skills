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
    @State private var showDatePicker = false
    
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
                    DateLabelView(
                        date: signupStore.signupForm.birthday.value,
                        isSelected: showDatePicker
                    ) {
                        withAnimation { showDatePicker.toggle() }
                    }
                    .padding(.top, .spacingMD)
                    .padding(.leading, 2)
                    // The date picker appears when showDatePicker is true
                    DatePickerView(isPresented: $showDatePicker,
                                   date: $signupStore.signupForm.birthday.value,
                                   endDate: maxDate)
                        
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }
}

#Preview {
    DateOfBirthStepView(signupStore: SignupStore())
        .padding()
}
