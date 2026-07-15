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
    private let identityDisplay: (String) -> String = { $0 }

    var body: some View {
        SignupStepWrapper(title: heightStepLang.title, subtitle: heightStepLang.subtitle) {
            VStack(spacing: .spacingMD) {
                UnitValuePickerField(
                    label: heightStepLang.fieldLabel,
                    value: signupStore.getFormattedHeight(),
                    isActive: signupStore.showHeightInchesPicker || signupStore.showHeightCmPicker,
                    accessibilityIdentifier: AccessibilityID.signupHeightField
                ) {
                    signupStore.showHeightPicker()
                }
                .padding(.top, .spacingLG)

                UnitSelectionToggle(
                    imperialTitle: heightStepLang.imperialUnit,
                    metricTitle: heightStepLang.metricUnit,
                    isMetric: $signupStore.signupForm.useMetric.value,
                    accessibilityIdentifier: AccessibilityID.signupHeightUnitToggle
                )
            }
        }
        .pickerSheet(
            isPresented: $signupStore.showHeightInchesPicker,
            selectedValues: signupStore.selectedHeightInches,
            options: signupStore.heightInchesOptions,
            displayValue: identityDisplay,
            pickerType: .heightInches,
            title: heightStepLang.pickerHeader
        ) { newValues in
            signupStore.updateFormHeight(fromMetric: false, values: newValues)
        }
        .pickerSheet(
            isPresented: $signupStore.showHeightCmPicker,
            selectedValues: signupStore.selectedHeightCm,
            options: signupStore.heightCmOptions,
            displayValue: identityDisplay,
            pickerType: .heightCm,
            title: heightStepLang.pickerHeader
        ) { newValues in
            signupStore.updateFormHeight(fromMetric: true, values: newValues)
        }
    }
}
