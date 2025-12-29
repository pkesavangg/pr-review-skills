//
//  AlertModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import SwiftUI
import UIKit

/// A view modifier that presents a customizable alert with optional input and multiple buttons.
///
/// Usage:
/// 1. Define your `AlertModel` with a title, message, optional input field, and an array of `AlertButtonModel`.
/// 2. Each `AlertButtonModel` accepts a title, type (primary, secondary, danger), and a closure with the input value (if any).
/// 3. Attach `.presentAlert(alertData: $alertData)` to your root view.
///
/// Example:
/// ```swift
/// @State private var alertData: AlertModel? = nil
///
/// alertData = AlertModel(
///     title: "Confirm Action",
///     message: "Are you sure you want to delete this item?",
///     buttons: [
///         AlertButtonModel(title: "Delete", type: .danger) { _ in print("Deleted") },
///         AlertButtonModel(title: "Cancel", type: .secondary) { _ in print("Cancelled") }
///     ],
///     inputField: AlertInputField(placeholder: "Reason", value: "", type: .text)
/// )
/// ```
///
/// .danger → Red destructive button
/// .secondary → Cancel role
/// .primary → Default button with input field value (if any)

struct AlertModifier: ViewModifier {
    @Binding var alertData: AlertModel?

    var isAlertPresented: Binding<Bool> {
        Binding(
            get: { alertData != nil },
            set: { if !$0 { alertData = nil } }
        )
    }

    func body(content: Content) -> some View {
        content
            .onChange(of: alertData != nil) { wasNil, isPresented in
                if !wasNil && isPresented, alertData?.inputField != nil {
                    focusTextFieldInAlert()
                }
            }
            .alert(alertData?.title ?? "", isPresented: isAlertPresented) {
                alertContent
            } message: {
                alertMessage
            }
    }
    
    @ViewBuilder
    private var alertContent: some View {
        if let alert = alertData {
            if let inputField = alert.inputField {
                inputFieldView(for: inputField)
            }
            ForEach(alert.buttons.indices, id: \.self) { index in
                alertButton(alert.buttons[index])
            }
        }
    }
    
    @ViewBuilder
    private func inputFieldView(for inputField: AlertInputField) -> some View {
        let binding = Binding(
            get: { alertData?.inputField?.value ?? "" },
            set: { alertData?.inputField?.value = $0 }
        )
        
        if inputField.type == .password {
            SecureField(inputField.placeholder, text: binding)
        } else {
            TextField(inputField.placeholder, text: binding)
                .keyboardType(inputField.type == .email ? .emailAddress : .default)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled(true)
        }
    }
    
    private func alertButton(_ button: AlertButtonModel) -> some View {
        Button(button.title.uppercased(), role: buttonRole(for: button.type)) {
            let inputValue = button.type == .primary ? alertData?.inputField?.value : nil
            button.action(inputValue)
            alertData = nil
        }
        .disabled(isPrimaryButtonInvalidEmail(button))
        .if(button.type == .primary) { $0.keyboardShortcut(.defaultAction) }
    }
    
    @ViewBuilder
    private var alertMessage: some View {
        if let alert = alertData {
            if let input = alert.inputField, input.type == .email {
                let value = alert.inputField?.value ?? ""
                if !value.isEmpty && !isValidEmail(value) {
                    Text(FormErrorMessages.email)
                } else if let msg = alert.message {
                    Text(msg)
                }
            } else if let message = alert.message {
                Text(message)
            }
        }
    }
    
    private func buttonRole(for type: AlertButtonType) -> ButtonRole? {
        switch type {
        case .primary:
            return nil
        case .secondary:
            return .cancel
        case .danger:
            return .destructive
        }
    }

    private func isPrimaryButtonInvalidEmail(_ button: AlertButtonModel) -> Bool {
        guard button.type == .primary,
              let field = alertData?.inputField,
              field.type == .email else { return false }
        return field.value.isEmpty || !isValidEmail(field.value)
    }

    private func isValidEmail(_ value: String) -> Bool {
        Validator<String>.email.fn(value)
    }
    
    private func focusTextFieldInAlert() {
        let delays: [TimeInterval] = [0.15, 0.3, 0.5, 0.7]
        
        for delay in delays {
            DispatchQueue.main.asyncAfter(deadline: .now() + delay) {
                guard self.alertData != nil,
                      let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene else { return }
                
                for window in windowScene.windows {
                    if let textField = self.findTextField(in: window), !textField.isFirstResponder {
                        textField.becomeFirstResponder()
                        return
                    }
                }
            }
        }
    }
    
    private func findTextField(in view: UIView) -> UITextField? {
        if let textField = view as? UITextField, textField.isUserInteractionEnabled {
            return textField
        }
        return view.subviews.compactMap { findTextField(in: $0) }.first
    }
}
