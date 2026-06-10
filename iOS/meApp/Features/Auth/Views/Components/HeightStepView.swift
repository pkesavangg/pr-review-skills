//
//  HeightStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

// MARK: - HeightStepView
/// A view for selecting a user's height during the signup process.
/// Displays the title, subtitle, a tappable field showing the selected height, and a
/// Ft/In ↔ CM unit selector. Tapping the field opens a unit-aware picker (inches or cm).
struct HeightStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    let heightStepLang = SignupStrings.HeightStep.self
    let labels = InputFieldLabels.self
    
    var body: some View {
        SignupStepWrapper(title: heightStepLang.title, subtitle: heightStepLang.subtitle) {

            VStack(spacing: .spacingSM) {
                // Tappable height field: label on the left, selected value + chevron on the right.
                UnitValuePickerField(
                    label: labels.height,
                    value: signupStore.getFormattedHeight(),
                    isActive: signupStore.showHeightInchesPicker || signupStore.showHeightCmPicker
                ) {
                    signupStore.showHeightPicker()
                }

                // Ft/In ↔ CM selector — bound to the shared `useMetric` flag so the Goal
                // step's lbs/kg selector flips to match (Imperial ↔ Metric stay paired).
                UnitSelectionToggle(
                    imperialTitle: heightStepLang.imperialUnit,
                    metricTitle: heightStepLang.metricUnit,
                    isMetric: $signupStore.signupForm.useMetric.value
                )
            }
            .frame(maxWidth: .infinity)
            .padding(.top, .spacingLG)
        }
        .pickerSheet(
            isPresented: $signupStore.showHeightInchesPicker,
            selectedValues: signupStore.selectedHeightInches,
            options: signupStore.heightInchesOptions,
            displayValue: { $0 },
            pickerType: .heightInches,
            title: heightStepLang.pickerHeader
        ) { newValues in
            signupStore.updateFormHeight(fromMetric: false, values: newValues)
        }
        .pickerSheet(
            isPresented: $signupStore.showHeightCmPicker,
            selectedValues: signupStore.selectedHeightCm,
            options: signupStore.heightCmOptions,
            displayValue: { $0 },
            pickerType: .heightCm,
            title: heightStepLang.pickerHeader
        ) { newValues in
            signupStore.updateFormHeight(fromMetric: true, values: newValues)
        }
    }
}
