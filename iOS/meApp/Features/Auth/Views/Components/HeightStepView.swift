//
//  HeightStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

// MARK: - HeightStepView
/// A view for selecting a user's height during the signup process.
/// This view displays the title, subtitle, and a chip with the selected height.
/// It also provides a picker for selecting height in either inches or centimeters.
struct HeightStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    let heightStepLang = SignupStrings.HeightStep.self
    let labels = InputFieldLabels.self
    
    var body: some View {
        SignupStepWrapper(title: heightStepLang.title, subtitle: heightStepLang.subtitle) {
            
            VStack(alignment: .leading, spacing: .spacingLG) {
                // Height Selection Chip
                ChipView(
                    text: signupStore.getFormattedHeight(),
                    style: .bordered,
                    isSelected: signupStore.showHeightInchesPicker || signupStore.showHeightCmPicker
                )                    {
                        signupStore.showHeightPicker()
                    }
                .padding(.top, .spacingLG)
                
                CustomToggleView(isOn: $signupStore.signupForm.useMetric.value,
                                 text: labels.useMetric)
                
            }
            .padding(.leading, 2)
        }
        .pickerSheet(
            isPresented: $signupStore.showHeightInchesPicker,
            selectedValues: signupStore.selectedHeightInches,
            options: signupStore.heightInchesOptions,
            displayValue: { $0 },
            pickerType: .heightInches,
            title: heightStepLang.pickerHeader
        )            { newValues in
                signupStore.updateFormHeight(fromMetric: false, values: newValues)
            }
        .pickerSheet(
            isPresented: $signupStore.showHeightCmPicker,
            selectedValues: signupStore.selectedHeightCm,
            options: signupStore.heightCmOptions,
            displayValue: { $0 },
            pickerType: .heightCm,
            title: heightStepLang.pickerHeader
        )            { newValues in
                signupStore.updateFormHeight(fromMetric: true, values: newValues)
            }
    }
}
