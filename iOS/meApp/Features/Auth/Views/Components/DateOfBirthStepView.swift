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
            VStack(alignment: .leading, spacing: 0) {
                DateLabelView(
                    date: signupStore.signupForm.birthday.value,
                    isSelected: showDatePicker
                ) {
                    withAnimation { showDatePicker.toggle() }
                }
                .appAccessibility(id: AccessibilityID.signupDobField)
                .padding(.top, .spacingLG)
                .padding(.leading, 2)
                // The date picker appears when showDatePicker is true
                DatePickerView(isPresented: $showDatePicker,
                               date: $signupStore.signupForm.birthday.value,
                               endDate: maxDate)
                    
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .onChange(of: signupStore.currentStep) { _, _ in
            // Close calendar when step changes
            showDatePicker = false
        }
    }
}

#Preview {
    DateOfBirthStepView(signupStore: SignupStore())
        .padding()
}
