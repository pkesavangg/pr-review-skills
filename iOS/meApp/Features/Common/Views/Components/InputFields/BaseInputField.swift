//
//  BaseInputField.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//
import SwiftUI

// MARK: - Base Input Field

struct BaseInputField: View {
    @Environment(\.appTheme) private var theme
    
    // Configuration
    var inputType: TextFieldType
    var keyboardType: UIKeyboardType
    var submitLabel: SubmitLabel
    var isDisabled: Bool
    
    // Bindings
    @Binding var value: String
    @FocusState.Binding var isFocused: Bool
    
    // Callbacks
    var onCommit: (() -> Void)?
    var onEditingChanged: ((Bool) -> Void)?
    
    // Internal state for password visibility
    @State private var isSecureTextVisible: Bool = false
    
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
        .foregroundColor(theme.textBody)
        .focused($isFocused)
        .autocorrectionDisabled(true)
        .onChange(of: isFocused) {
            onEditingChanged?(isFocused)
        }
        .onSubmit {
            onCommit?()
        }
        .overlay(
            HStack {
                Spacer()
                // Password visibility toggle
                if inputType == .password && !value.isEmpty && !isDisabled {
                    Button(action: {
                        isSecureTextVisible.toggle()
                    }) {
                        AppIconView(icon: isSecureTextVisible ? AppAssets.eyeClosed : AppAssets.eyeOpen)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                    .padding(.trailing, 14)
                }
            }
        )
    }
}

import SwiftUI

struct BaseInputTestView: View {
    @EnvironmentObject var themeManager: Theme
    @Environment(\.appTheme) private var theme
    @State var text: String = "dfsdfsdfs"
    @FocusState private var isFocused: Bool
    var body: some View {
        VStack {
            BaseInputField(
                inputType: .password,
                keyboardType: .default,
                submitLabel: .done,
                isDisabled: true,
                value: $text,
                isFocused: $isFocused,
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
