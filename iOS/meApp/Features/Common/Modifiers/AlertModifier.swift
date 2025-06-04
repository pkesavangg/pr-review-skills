//
//  AlertModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//


import SwiftUI

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
    @Environment(\.appTheme) private var theme
    @Binding var alertData: AlertModel?

    var isAlertPresented: Binding<Bool> {
        Binding<Bool>(
            get: { alertData != nil },
            set: { newValue in
                if !newValue {
                    alertData = nil
                }
            }
        )
    }

    func body(content: Content) -> some View {
        content
            .alert(
                alertData?.title ?? "",
                isPresented: isAlertPresented
            ) {
                if let alert = alertData {
                    if let inputField = alert.inputField {
                        let binding = Binding(
                            get: { alertData?.inputField?.value ?? "" },
                            set: { alertData?.inputField?.value = $0 }
                        )

                        Group {
                            if inputField.type == .password {
                                SecureField(inputField.placeholder, text: binding)
                            } else {
                                TextField(inputField.placeholder, text: binding)
                                    .keyboardType(inputField.type == .email ? .emailAddress : .default)
                            }
                        }
                        .autocapitalization(.none)
                    }
                    
                    ForEach(alert.buttons.indices, id: \.self) { index in
                        let button = alert.buttons[index]
                        Button(button.title.uppercased(), role: buttonRole(for: button.type)) {
                            if button.type == .primary {
                                button.action(alertData?.inputField?.value)
                            }
                            alertData = nil
                        }
                        .if(index == alert.buttons.count - 1) { view in
                            view.keyboardShortcut(.defaultAction)
                        }
                    }
                }
            } message: {
                if let message = alertData?.message {
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
}
