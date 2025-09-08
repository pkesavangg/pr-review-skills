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
        SignupStepWrapper(title: dateOfBirthStepLang.title, subtitle: dateOfBirthStepLang.subtitle) {
            VStack(alignment: .leading, spacing: 4) {
                DateLabelView(
                    date: signupStore.signupForm.birthday.value,
                    chipStyle: showDatePicker ? ChipStyle.bordered : ChipStyle.normal
                ) {
                    withAnimation { showDatePicker.toggle() }
                }
                // The date picker appears when showDatePicker is true
                DatePickerView(isPresented: $showDatePicker,
                               date: $signupStore.signupForm.birthday.value,
                               endDate: maxDate)
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    DateOfBirthStepView(signupStore: SignupStore())
        .padding()
}
