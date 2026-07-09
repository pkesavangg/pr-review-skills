import Combine
//
//  MetricInputField.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 10/06/25.
//
import SwiftUI

// MARK: - Metric Input Field (Wrapper over AppInputField)

struct MetricInputField: View {
    // Configuration
    var config: TextInputConfig
    
    // Bindings
    @Binding var value: String
    @Binding var focusedField: FocusField?
    
    // Callbacks
    var onCommit: (() -> Void)?
    var onEditingChanged: ((Bool) -> Void)?

    // Accessibility
    var accessibilityIdentifier: String?

    // Internal state and formatter
    @State private var displayValue: String = ""
    @State private var isInitialState: Bool = true
    @StateObject private var formatter: MetricFieldFormatter

    init(
        config: TextInputConfig,
        value: Binding<String>,
        focusedField: Binding<FocusField?>,
        accessibilityIdentifier: String? = nil,
        onCommit: (() -> Void)? = nil,
        onEditingChanged: ((Bool) -> Void)? = nil
    ) {
        self.config = config
        self._value = value
        self._focusedField = focusedField
        self.accessibilityIdentifier = accessibilityIdentifier
        self.onCommit = onCommit
        self.onEditingChanged = onEditingChanged
        self._formatter = StateObject(wrappedValue: MetricFieldFormatter(config: config))
    }

    var body: some View {
        AppInputField(
            config: modifiedConfig,
            value: $displayValue,
            focusedField: $focusedField,
            accessibilityIdentifier: accessibilityIdentifier,
            onCommit: onCommit,
            onEditingChanged: onEditingChanged
        )
        .onChange(of: displayValue) { oldValue, newValue in
            handleValueChange(oldValue: oldValue, newValue: newValue)
        }
        .onChange(of: value) { oldValue, newValue in
            if oldValue != newValue {
                let formatted = formatter.formatInput(newValue)
                if displayValue != formatted {
                    displayValue = formatted
                }
            }
        }
        .onAppear {
            initializeValue()
        }
    }
    
    // MARK: - Private Methods
    
    private var modifiedConfig: TextInputConfig {
        var modifiedConfig = config
        modifiedConfig.inputType = .metric
        return modifiedConfig
    }
    
    private func initializeValue() {
        if value.isEmpty {
            // Initial state should be empty
            displayValue = ""
            isInitialState = true
        } else {
            let formatted = formatter.formatInput(value)
            displayValue = formatted
            isInitialState = false
            if formatted != value {
                value = formatted
            }
        }
    }
    
    private func handleValueChange(oldValue: String, newValue: String) {
        // If user has entered text for the first time, we're no longer in initial state
        if !newValue.isEmpty && isInitialState {
            isInitialState = false
        }

        // Allow empty values
        if newValue.isEmpty {
            displayValue = ""
            value = ""
            return
        }

        let formatted = formatter.formatInput(newValue)

        // Check if the new value is valid (doesn't exceed max)
        guard formatter.shouldUpdateValue(from: oldValue, to: newValue) else {
            // Revert displayValue to prevent unlimited text growth
            displayValue = oldValue
            return
        }

        // Update display value if it changed
        if displayValue != formatted {
            displayValue = formatted
        }

        // Only update bound value if it's different
        if value != formatted {
            value = formatted
        }
    }
}

// MARK: - Metric Input TestingView (for testing the MetricInputField)

struct MetricInputTestingView: View {
    @State var text: String = ""
    @State var password: String = ""
    @State var number: String = ""
    @State var disabledText: String = ""
    @State var bankWeightValue: String = ""
    @State var bankBodyFatValue: String = ""
    @State var bankExperienceValue: String = ""
    @State var bankDisabledValue: String = "42.5"
    @State var focusedField: FocusField?
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Text("Modular Input System Demo")
                    .font(.title2)
                    .fontWeight(.bold)
                    .padding(.bottom, 10)
                   
                Group {
                    Text("BankInputField Examples (Built on AppInputField)")
                        .font(.headline)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    
                    // Bank input examples
                    MetricInputField(
                        config: TextInputConfig(
                            label: "weight (lb)",
                            placeholder: "0.0",
                            inputType: .metric,
                            focusField: .weight,
                            maxLength: 4,
                            maxValue: 999.9
                        ),
                        value: $bankWeightValue,
                        focusedField: $focusedField
                    )
                    
                    MetricInputField(
                        config: TextInputConfig(
                            label: "body fat %",
                            placeholder: "0.0",
                            inputType: .metric,
                            focusField: .bodyFat,
                            maxLength: 3,
                            maxValue: 99.9
                        ),
                        value: $bankBodyFatValue,
                        focusedField: $focusedField
                    )
                    
                    MetricInputField(
                        config: TextInputConfig(
                            label: "Years of Experience",
                            placeholder: "0",
                            inputType: .metric,
                            maxLength: 2,
                            allowWholeNumbers: true
                        ),
                        value: $bankExperienceValue,
                        focusedField: $focusedField
                    )
                    
                    // Disabled bank input example
                    MetricInputField(
                        config: TextInputConfig(
                            label: "Disabled Bank Input",
                            placeholder: "0.0",
                            inputType: .metric,
                            isDisabled: true,
                            maxLength: 3
                        ),
                        value: $bankDisabledValue,
                        focusedField: $focusedField
                    )
                }
                
                Divider()
                
                // Display current values
                VStack(alignment: .leading, spacing: 8) {
                    Text("Current Values:")
                        .font(.headline)
                    
                    Group {
                        Text("Username: '\(text)'")
                        Text("Password: '\(String(repeating: "•", count: password.count))'")
                        Text("Phone: '\(number)'")
                        Text("Weight: '\(bankWeightValue)'")
                        Text("Body Fat: '\(bankBodyFatValue)'")
                        Text("Experience: '\(bankExperienceValue)'")
                        Text("Disabled Bank: '\(bankDisabledValue)'")
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(10)
                
                // Formatting examples
                VStack(alignment: .leading, spacing: 8) {
                    Text("Formatting Examples:")
                        .font(.headline)
                    
                    Group {
                        Text("• Type '123' in Weight → displays '12.3'")
                        Text("• Type '4567' in Body Fat → displays '45.6' (max 3 digits)")
                        Text("• Type '25' in Experience → displays '25' (whole numbers)")
                        Text("• Weight max value: 999.9kg")
                        Text("• Body Fat max value: 99.9%")
                    }
                    .font(.caption2)
                    .foregroundColor(.secondary)
                }
                .padding()
                .background(Color(.systemGray6))
                .cornerRadius(10)
            }
            .padding()
        }
        .background(Color(.systemGroupedBackground))
    }
}

#Preview {
    MetricInputTestingView()
}
