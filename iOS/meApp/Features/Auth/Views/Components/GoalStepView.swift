//
//  GoalStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 12/06/25.
//

import SwiftUI

// MARK: - GoalStepView
/// View for the goal step in the signup process.
/// It allows users to select their goal type (lose, gain, maintain) and input their current and goal weights.
struct GoalStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    @State private var selectedSegment: GoalTypeSegment = .loseGain
    @State private var focusedField: FocusField?
    
    let goalStepLang = SignupStrings.GoalStep.self
    let labels = InputFieldLabels.self
    
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(goalStepLang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    
                    Text(goalStepLang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }
                VStack {
                    SegmentedButtonView(
                        segments: GoalTypeSegment.allCases,
                        selectedSegment: $selectedSegment
                    )
                    .onChange(of: selectedSegment) { oldValue, newValue in
                        signupStore.signupForm.goalType.value = newValue.goalTypeValue
                    }
                    
                    VStack(spacing: 4) {
                        // Current Weight Input
                        if signupStore.signupForm.goalType.value != GoalType.maintain.rawValue {
                            MetricInputField(
                                config: TextInputConfig(
                                    label: "\(labels.startingWeight) (\(signupStore.signupForm.useMetric.value ? "kg" : "lbs"))",
                                    placeholder: "0.0",
                                    inputType: .metric,
                                    errorMessage: signupStore.getError(for: signupStore.signupForm.currentWeight),
                                    isDisabled: signupStore.signupForm.goalType.value == GoalType.maintain.rawValue,
                                    focusField: .currentWeight,
                                    maxLength: 4,
                                    maxValue: 999.9
                                ),
                                value: $signupStore.signupForm.currentWeight.value,
                                focusedField: $focusedField,
                                onCommit: {
                                    focusedField = .goalWeight
                                }
                            )
                        }
                        
                        MetricInputField(
                            config: TextInputConfig(
                                label: "\(labels.goalWeight) (\(signupStore.signupForm.useMetric.value ? "kg" : "lbs"))",
                                placeholder: "0.0",
                                inputType: .metric,
                                errorMessage: signupStore.getError(for: signupStore.signupForm.goalWeight),
                                focusField: .goalWeight,
                                maxLength: 4,
                                maxValue: 999.9
                            ),
                            value: $signupStore.signupForm.goalWeight.value,
                            focusedField: $focusedField,
                            onCommit: {
                                focusedField = nil
                            }
                        )
                    }
                    .padding(.top, .spacingMD)
                }
                .padding(.top, .spacingLG)
                .onChange(of: signupStore.signupForm.goalType.value) { oldValue, newValue in
                    selectedSegment = GoalTypeSegment.fromGoalType(signupStore.signupForm.goalType.value)
                }
            }
            .padding(.bottom, .spacing3XL)
        }
        .scrollDismissesKeyboard(.interactively) // Dismiss keyboard when dragging
    }
}

#Preview {
    GoalStepView(signupStore: SignupStore())
        .padding()
}
