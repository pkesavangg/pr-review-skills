//
//  AppInputField.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

// MARK: - App Input Field

/// A customizable input field with floating label, validation message, and optional clear/disabled icons.
///
/// Features:
/// 1. **Floating Label**: Label floats above the field when focused or when input is not empty.
/// 2. **Themed Styling**: Dynamically applies colors based on disabled, error, or active state using `appTheme`.
/// 3. **Trailing Icon Support**: Shows a disabled icon if the field is disabled, or a clear icon for non-password inputs.
/// 4. **Error Handling**: Displays error message below the field when `config.errorMessage` is present.
/// 5. **Focus Sync**: Keeps internal `FocusState` in sync with external `@Binding isFocused` for programmatic control.
struct AppInputField: View {
    @Environment(\.appTheme) private var theme
    // Configuration
    var config: TextInputConfig
    
    // Bindings
    @Binding var value: String
    @Binding var isFocused: Bool
    
    // Callbacks
    var onCommit: (() -> Void)? = nil
    var onEditingChanged: ((Bool) -> Void)? = nil
    
    // Internal state
    @FocusState private var fieldIsFocused: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            VStack(alignment: .leading, spacing: 4) {
                // Main input container
                ZStack(alignment: .leading) {
                    // Floating label
                    Text(config.label)
                        .fontOpenSans((fieldIsFocused || !value.isEmpty) ? .subHeading2 : .subHeading1)
                        .foregroundColor(config.isDisabled ? theme.textBody.opacity(0.38) : (config.errorMessage != nil ? theme.textError : theme.textSubheading))
                        .offset(y: (fieldIsFocused || !value.isEmpty) ? -15 : 0)
                        .offset(x: 16)
                        .animation(.easeInOut(duration: 0.1), value: fieldIsFocused || !value.isEmpty)
                        .disabled(config.isDisabled)
                    
                    // Base input field
                    BaseInputField(
                        inputType: config.inputType,
                        keyboardType: keyboardTypeForInput,
                        submitLabel: config.submitLabel,
                        isDisabled: config.isDisabled,
                        value: $value,
                        isFocused: $fieldIsFocused,
                        onCommit: onCommit,
                        onEditingChanged: { focused in
                            isFocused = focused
                            onEditingChanged?(focused)
                        }
                    )
                    .padding(.leading, 16)
                }
                .padding(.vertical, 8)
                .padding(.horizontal, 4)
                .accentColor((config.errorMessage != nil ? theme.textError : theme.actionPrimary))
            }
            .frame(height: 56)
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusSM)
            .overlay(
                HStack {
                    Spacer()
                    trailingIconView
                }
            )
            .overlay(content: {
                theme.supportOverlay.opacity(config.isDisabled ? 0.2 : 0)
                    .cornerRadius(.radiusSM)
            })
            .onTapGesture {
                if !config.isDisabled {
                    fieldIsFocused = true
                }
            }
            .onChange(of: isFocused) {
                fieldIsFocused = isFocused
            }
            .onAppear {
                fieldIsFocused = isFocused
            }
            
            Text(config.errorMessage ?? "")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textError)
                .padding(.bottom, 4)
                .padding(.leading, 16)
                .frame(height: 15)
        }
    }
    
    private var keyboardTypeForInput: UIKeyboardType {
        switch config.inputType {
        case .number, .metric:
            return .numberPad
        case .email:
            return .emailAddress
        default:
            return .default
        }
    }
    
    private var trailingIconView: some View {
        Group {
            if config.isDisabled {
                disabledIcon
            } else if config.inputType != .password {
                clearButton
            }
        }
    }

    private var disabledIcon: some View {
        AppIconView(icon: AppAssets.closeCircle)
            .foregroundColor(theme.actionSecondaryDisabled)
            .padding(.trailing, 14)
    }

    private var clearButton: some View {
        Button(action: {
            value = ""
        }) {
            AppIconView(icon: AppAssets.closeCircle)
                .foregroundColor(config.errorMessage != nil ? theme.textError : theme.actionPrimary)
        }
        .padding(.trailing, 12)
    }
}

// MARK: - APP Input Field Testing View
struct AppInputTestingField : View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @State var text: String = ""
    @State var email: String = ""
    @State var password: String = ""
    @State var number: String = ""
    @State var disabledText: String = "asdasd"
    var body: some View {
        VStack {
            // Text input example
            // Text input example
            AppInputField(
                config: TextInputConfig(
                    label: "Username",
                    placeholder: "Enter your username",
                    inputType: .text
                ),
                value: $text,
                isFocused: .constant(false)
            )
            Text(email.count < 6 && !email.isEmpty ? "Password is too short" : "nil")
            
            Text(password.count < 6 && !password.isEmpty ? "Password is too short" : "password")
            
            AppInputField(
                config: TextInputConfig(
                    label: "Email",
                    placeholder: "Enter your email",
                    inputType: .email,
                    errorMessage: email.count < 6 && !email.isEmpty ? "Password is too short" : nil
                ),
                value: $email,
                isFocused: .constant(false)
            )
            
            // Password input example
            AppInputField(
                config: TextInputConfig(
                    label: "Password",
                    placeholder: "Enter your password",
                    inputType: .password,
                    submitLabel: .done,
                    errorMessage: password.count < 6 && !password.isEmpty ? "Password is too short" : nil
                ),
                value: $password,
                isFocused: .constant(true)
            )
            
            // Number input example
            AppInputField(
                config: TextInputConfig(
                    label: "Phone Number",
                    placeholder: "Enter your phone number",
                    inputType: .number
                ),
                value: $number,
                isFocused: .constant(false)
            )
            
            // Disabled input example
            AppInputField(
                config: TextInputConfig(
                    label: "Disabled Input",
                    placeholder: "This field is disabled",
                    inputType: .text,
                    isDisabled: true
                ),
                value: $disabledText,
                isFocused: .constant(false)
            )
        }
        .padding(.horizontal)
        .background(theme.backgroundSecondary)
    }
}

#Preview(body: {
    AppInputTestingField()
        .environmentObject(Theme.shared)
})
