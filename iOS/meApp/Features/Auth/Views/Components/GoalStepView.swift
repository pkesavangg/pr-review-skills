import SwiftUI

struct GoalStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    @State private var selectedSegment: GoalTypeSegment = .loseGain
    @State private var focusedField: FocusField?
    
    var goalStepLang = SignupStrings.GoalStep.self
    
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
                        MetricInputField(
                            config: TextInputConfig(
                                label: "\(goalStepLang.currentWeightLabel) (\(signupStore.signupForm.useMetric.value ? "kg" : "lbs"))",
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
                        
                        MetricInputField(
                            config: TextInputConfig(
                                label: "\(goalStepLang.goalWeightLabel) (\(signupStore.signupForm.useMetric.value ? "kg" : "lbs"))",
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
                        
                        // TODO: Need to replace with the custom toggle view
                        Toggle(goalStepLang.useMetricLabel, isOn: $signupStore.signupForm.useMetric.value)
                            .padding(.top, .spacingXS)
                            .padding(.horizontal, 2)
                    }
                    .padding(.top, .spacingMD)
                }
                .padding(.top, .spacingLG)
                

            }
        }
        .onAppear {
            // Set initial segment based on form data
            selectedSegment = GoalTypeSegment.fromGoalType(signupStore.signupForm.goalType.value)
        }
    }
}

#Preview {
    GoalStepView(signupStore: SignupStore())
        .padding()
} 
