//
//  BaseInputField.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

// MARK: - Base Input Field

struct BaseInputField: View {
    @EnvironmentObject var themeManager: Theme
    
    // Configuration
    var inputType: TextFieldType
    var keyboardType: UIKeyboardType
    var submitLabel: SubmitLabel
    var isDisabled: Bool
    var fieldType: FocusField
    
    // Bindings
    @Binding var value: String
    @Binding var focusedField: FocusField?
    
    // Callbacks
    var onCommit: (() -> Void)?
    var onEditingChanged: ((Bool) -> Void)?
    
    // Internal state for password visibility
    @State private var isSecureTextVisible: Bool = false
    @FocusState private var isFocused: Bool
    @State private var themeRefreshId: String = ""
    
    // Constants
    let focusedTopPadding: CGFloat = 15
    let trailingPadding: CGFloat = 40
    
    // Computed property for theme palette (derived from themeManager)
    private var theme: AppColors.Palette {
        AppColors.Theme.primary.palette
    }
    
    // Computed property for text color that updates with theme
    private var textColor: Color {
        theme.textBody.opacity(isDisabled ? 0.38 : 1)
    }
    
    // Theme identifier that changes when theme changes
    private var themeId: String {
        "\(themeManager.appearanceMode.rawValue)-\(themeManager.systemColorScheme == .dark ? "dark" : "light")"
    }
    
    // Helper function to handle theme changes and preserve focus
    private func handleThemeChange() {
        let wasFocused = isFocused
        themeRefreshId = themeId
        
        // Restore focus after TextField is recreated on next run loop
        if wasFocused {
            DispatchQueue.main.async {
                isFocused = true
                focusedField = fieldType
            }
        }
    }
    
    var body: some View {
        Group {
            if inputType == .password && !isSecureTextVisible {
                SecureField("", text: $value)
                    .submitLabel(submitLabel)
                    .disabled(isDisabled)
            } else {
                TextField("", text: $value)
                    .submitLabel(submitLabel)
                    .keyboardType(keyboardType)
                    .disabled(isDisabled)
            }
        }
        .id("\(fieldType)-\(themeRefreshId)")
        .padding(.top, (isFocused || !value.isEmpty) ? focusedTopPadding : 0)
        .padding(.trailing, trailingPadding)
        .foregroundStyle(textColor)
        .focused($isFocused)
        .autocorrectionDisabled(true)
        .autocapitalization(inputType == .email || inputType == .password ? .none : .sentences)
        .onChange(of: isFocused) {
            onEditingChanged?(isFocused)
            if isFocused {
                focusedField = fieldType
            }
        }
        .onChange(of: focusedField) {
            isFocused = focusedField == fieldType
        }
        .onChange(of: themeManager.appearanceMode) { _, _ in
            handleThemeChange()
        }
        .onChange(of: themeManager.systemColorScheme) { _, _ in
            if themeManager.appearanceMode == .system {
                handleThemeChange()
            }
        }
        .onAppear {
            themeRefreshId = themeId
        }
        .onSubmit {
            onCommit?()
        }
        .overlay(
            HStack {
                Spacer()
                // Password visibility toggle
                if inputType == .password && !isDisabled {
                    Button(action: {
                        withAnimation {
                            isSecureTextVisible.toggle()
                        }
                    }) {
                        AppIconView(icon: isSecureTextVisible ? AppAssets.eyeClosed : AppAssets.eyeOpen)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                    .padding(.trailing, .spacingXS)
                }
            }
        )
    }
}

// MARK: - Base Input Field Preview
struct BaseInputTestView: View {
    @EnvironmentObject var themeManager: Theme
    @State var text: String = ""
    @State var focusedField: FocusField?
    
    var body: some View {
        VStack {
            BaseInputField(
                inputType: .password,
                keyboardType: .default,
                submitLabel: .done,
                isDisabled: true,
                fieldType: .password,
                value: $text,
                focusedField: $focusedField,
                onCommit: {
                    print("Submitted: \(text)")
                },
                onEditingChanged: { isEditing in
                    print("Editing changed: \(isEditing)")
                })
        }
    }
}

#Preview {
    BaseInputTestView()
        .environmentObject(Theme.shared)
}
