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
        SignupStepWrapper(title: goalStepLang.title, subtitle: goalStepLang.subtitle) {
            VStack {
                SegmentedButtonView(
                    segments: GoalTypeSegment.allCases,
                    selectedSegment: $selectedSegment
                )
                .onChange(of: selectedSegment) { _, newValue in
                    signupStore.signupForm.goalType.value = newValue.goalTypeValue
                    // Mark as dirty and touched when goal type changes
                    signupStore.signupForm.goalType.markAsDirty()
                    signupStore.signupForm.goalType.markAsTouched()
                    // Trigger validation to clear form-level errors when switching modes
                    signupStore.signupForm.validate()
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
                                signupStore.touchAndValidate(field: .currentWeight)
                                focusedField = .goalWeight
                            },
                            onEditingChanged: { isEditing in
                                guard !signupStore.isGoalSkipped else { return }
                                signupStore.handleEditingChanged(isEditing, field: .currentWeight)
                            }
                        )
                        .onChange(of: focusedField) { oldValue, newValue in
                            guard !signupStore.isGoalSkipped else { return }
                            if oldValue == .currentWeight && newValue != .currentWeight {
                                signupStore.touchAndValidate(field: .currentWeight)
                            }
                        }
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
                            signupStore.touchAndValidate(field: .goalWeight)
                            focusedField = nil
                        },
                        onEditingChanged: { isEditing in
                            guard !signupStore.isGoalSkipped else { return }
                            signupStore.handleEditingChanged(isEditing, field: .goalWeight)
                        }
                    )
                    .onChange(of: focusedField) { oldValue, newValue in
                        guard !signupStore.isGoalSkipped else { return }
                        // Mark goal weight as touched when focus moves away from it
                        if oldValue == .goalWeight && newValue != .goalWeight {
                            signupStore.touchAndValidate(field: .goalWeight)
                        }
                    }
                }
                .padding(.top, .spacingMD)
            }
            .padding(.top, .spacingLG)
            .onChange(of: signupStore.signupForm.goalType.value) { _, _ in
                selectedSegment = GoalTypeSegment.fromGoalType(signupStore.signupForm.goalType.value)
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
    GoalStepView(signupStore: SignupStore())
        .padding()
}
