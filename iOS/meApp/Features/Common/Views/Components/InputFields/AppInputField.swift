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
    @Environment(\.colorScheme) private var colorScheme
    // Configuration
    var config: TextInputConfig

    // Bindings
    @Binding var value: String
    @Binding var focusedField: FocusField?

    // Accessibility
    var accessibilityIdentifier: String?

    // Callbacks
    var onCommit: (() -> Void)?
    var onEditingChanged: ((Bool) -> Void)?
    
    // Internal state
    @FocusState private var fieldIsFocused: Bool
    
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            inputBox
            Text(config.errorMessage ?? "")
                .fontOpenSans(.subHeading2)
                .foregroundColor(theme.textError)
                .padding(.leading, .spacingSM)
                .frame(height: 20, alignment: .center)
            Spacer()
        }
        .frame(height: 76)
    }

    private var inputBox: some View {
        VStack(alignment: .leading, spacing: 4) {
            inputContent
            .padding(.vertical, .spacingXS)
            .accentColor(accentColor)
        }
        .frame(height: 56)
        .background(theme.backgroundPrimary)
        .cornerRadius(.radiusSM)
        .overlay(alignment: .trailing) { trailingIconView }
        .overlay { disabledOverlay }
        .onTapGesture {
            if !config.isDisabled {
                fieldIsFocused = true
                focusedField = config.focusField
            }
        }
        .onChange(of: focusedField) { _, newValue in
            if newValue == config.focusField {
                fieldIsFocused = true
            } else if newValue == nil && fieldIsFocused {
                fieldIsFocused = false
            }
        }
        .onChange(of: fieldIsFocused) { _, newValue in
            if !newValue && focusedField == config.focusField {
                focusedField = nil
            }
        }
        .onAppear {
            if focusedField == config.focusField { fieldIsFocused = true }
        }
    }

    private var inputContent: some View {
        ZStack(alignment: .leading) {
            floatingLabelView
            baseInputView
        }
    }

    private var floatingLabelView: some View {
        Text(config.label)
            .fontOpenSans(isFloatingLabelActive ? .subHeading2 : .subHeading1)
            .foregroundColor(floatingLabelColor)
            .offset(y: isFloatingLabelActive ? -15 : 0)
            .offset(x: 16)
            .animation(.easeInOut(duration: 0.1), value: isFloatingLabelActive)
            .disabled(config.isDisabled)
    }

    private var baseInputView: some View {
        BaseInputField(
            inputType: config.inputType,
            keyboardType: keyboardTypeForInput,
            submitLabel: config.submitLabel,
            isDisabled: config.isDisabled,
            fieldType: config.focusField,
            value: $value,
            focusedField: $focusedField,
            onCommit: onCommit,
            onEditingChanged: { focused in
            fieldIsFocused = focused
            onEditingChanged?(focused)
            if focused { focusedField = config.focusField }
            },
            accessibilityIdentifier: accessibilityIdentifier
        )
        .focused($fieldIsFocused)
        .padding(.leading, .spacingSM)
    }

    private var disabledOverlay: some View {
        theme.supportOverlay
            .opacity(config.isDisabled ? disabledOverlayOpacity : 0)
            .cornerRadius(.radiusSM)
    }
    
    private var floatingLabelColor: Color {
        if config.isDisabled { return theme.textBody.opacity(0.38) }
        return config.errorMessage != nil ? theme.textError : theme.textSubheading
    }

    private var isFloatingLabelActive: Bool {
        fieldIsFocused || !value.isEmpty
    }

    private var accentColor: Color {
        config.errorMessage != nil ? theme.textError : theme.actionPrimary
    }

    private var disabledOverlayOpacity: Double {
        colorScheme == .dark ? 0.5 : 0.2
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
        HStack(spacing: 8) {
            if let customIcon = config.customIcon {
                Button(action: {
                    config.onCustomIconTap?()
                }, label: {
                    AppIconView(icon: customIcon, size: IconSize(width: 35, height: 35))
                        .foregroundColor(theme.actionPrimary)
                })
            } else {
                if config.isDisabled && !value.isEmpty {
                    disabledIcon
                } else if config.inputType != .password && !value.isEmpty {
                    clearButton
                }
            }
        }
        .padding(.trailing, .spacingXS)
    }

    private var disabledIcon: some View {
        AppIconView(icon: AppAssets.closeCircle)
            .foregroundColor(theme.actionSecondaryDisabled)
    }

    private var clearButton: some View {
        Button(action: {
            withAnimation {
                value = ""
            }
        }, label: {
            AppIconView(icon: AppAssets.closeCircle)
                .foregroundColor(config.errorMessage != nil ? theme.textError : theme.actionPrimary)
        })
    }
}

// MARK: - APP Input Field Testing View
struct AppInputTestingField: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @State var text: String = "Enter text here"
    @State var email: String = ""
    @State var password: String = ""
    @State var number: String = ""
    @State var disabledText: String = "Enter text here"
    @State var modelNumber: String = ""
    @State var focusedField: FocusField?
    
    var body: some View {
        VStack {
            AppInputField(
                config: TextInputConfig(
                    label: "Username",
                    placeholder: "Enter your username",
                    inputType: .text,
                    focusField: .firstName
                ),
                value: $text,
                focusedField: $focusedField) {
                    focusedField = .email
                }
            
            AppInputField(
                config: TextInputConfig(
                    label: "Email",
                    placeholder: "Enter your email",
                    inputType: .email,
                    errorMessage: "Email is too short"
                ),
                value: $email,
                focusedField: $focusedField) {
                    focusedField = .password
                }
            
            AppInputField(
                config: TextInputConfig(
                    label: "Password",
                    placeholder: "Enter your password",
                    inputType: .password,
                    submitLabel: .done,
// swiftlint:disable:next multiline_arguments
                    errorMessage: password.count < 6 && !password.isEmpty ? "Password is too short" : nil, focusField: .password
                ),
                value: $password,
                focusedField: $focusedField
            ) {
                focusedField = .bodyFat
            }
            
            AppInputField(
                config: TextInputConfig(
                    label: "Phone Number",
                    placeholder: "Enter your phone number",
                    inputType: .number,
                    focusField: .bodyFat
                ),
                value: $number,
                focusedField: $focusedField
            )
            
            AppInputField(
                config: TextInputConfig(
                    label: "Disabled Input",
                    placeholder: "This field is disabled",
                    inputType: .text,
                    isDisabled: true
                ),
                value: $disabledText,
                focusedField: $focusedField
            ) {
                focusedField = .password
            }
            
            AppInputField(
                config: TextInputConfig(
                    label: "Model Number",
                    placeholder: "Enter model number",
                    inputType: .text,
                    customIcon: AppAssets.helpCircle
                ) { },
                value: $modelNumber,
                focusedField: $focusedField
            ) {
                focusedField = FocusField.none
            }
        }
        .padding(.horizontal)
        .background(theme.backgroundSecondary)
        .onAppear {
            focusedField = .firstName
        }
    }
}

#Preview(body: {
    AppInputTestingField()
        .environmentObject(Theme.shared)
})
