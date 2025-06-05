//
//  View+Extension.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 04/06/25.
//

import SwiftUI

extension View {
    /// Presents an alert with the provided alert data.
    /// - Parameter alertData: A binding to the `AlertModel?` that contains the alert configuration.
    /// - Returns: A view that presents the alert when `alertData` is not nil.
    func presentAlert(alertData: Binding<AlertModel?>) -> some View {
        self.modifier(AlertModifier(alertData: alertData))
    }
    
    /// Conditional modifier that applies a transformation to the view if the condition is true.
    /// - Parameters:
    ///   - condition: A Boolean value that determines whether the transformation should be applied.
    ///   - transform: A closure that takes the current view and returns a transformed view.
    /// - Returns: A view that conditionally applies the transformation based on the condition.
    @ViewBuilder
    func `if`<Transform: View>(_ condition: Bool, transform: (Self) -> Transform) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
    
    /// Presents a toast notification with the provided toast data.
    /// - Parameter data: A binding to the `ToastModel?` that contains the toast configuration.
    /// - Returns: A view that presents the toast when `data` is not nil.
    func presentToast(data: Binding<ToastModel?>) -> some View {
        self.modifier(ToastModifier(toastData: data))
    }
}
