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
    
    /// Presents a loader with the provided loader data.
    /// - Parameter loaderData: A binding to the `LoaderModel?` that contains the loader configuration.
    /// - Returns: A view that presents the loader when `loaderData` is not nil.
    func presentLoader(loaderData: Binding<LoaderModel?>) -> some View {
        self.modifier(LoaderModifier(loaderData: loaderData))
    }
    
    /// Presents a modal view with the provided modal view data.
    /// - Parameter modalViewData: A binding to an array of `ModalData` that contains the modal configuration.
    /// - Returns: A view that presents the modal when `modalViewData` is not empty.
    func presentModal(modalViewData: Binding<[ModalData]>) -> some View {
        self.modifier(ModalViewModifier(modalStack: modalViewData))
    }
    
    /// Applies a basic button style with the specified foreground color.
    /// - Parameter foregroundColor: The color to use for the button's text.
    /// - Returns: A view styled as a basic button.
    func basicButtonStyle(foregroundColor: Color) -> some View {
        self.modifier(BasicButtonStyle(foreGroundColor: foregroundColor))
    }
    
    /// Applies a bordered button style with customizable background, border color, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - borderColor: The color of the button's border.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button (regular or small).
    /// - Returns: A view styled as a bordered button.
    func borderedButtonStyle(backgroundColor: Color, borderColor: Color, buttonSize: ButtonSize) -> some View {
        self.modifier(BorderedButtonStyle(backgroundColor: backgroundColor, borderColor: borderColor,buttonSize: buttonSize))
    }
    
    /// Applies a flat button style with customizable background, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button (regular or small).
    /// - Returns: A view styled as a flat button.
    func flatButtonStyle(
            foregroundColor: Color,
            backgroundColor: Color,
            buttonSize: ButtonSize
        ) -> some View {
            self.modifier(FlatButtonStyle(
                foregroundColor: foregroundColor,
                backgroundColor: backgroundColor,
                buttonSize: buttonSize
            ))
        }
    
    /// Presents a picker sheet with the provided configuration.
    /// - Parameters:
    /// - isPresented: A binding to a Boolean value that determines whether the picker sheet is presented.
    /// - selectedValues: An array of selected values of type `T`.
    /// - options: A 2D array of options of type `T` to choose from.
    /// - displayValue: A closure that takes a value of type `T` and returns a string to display.
    /// - pickerType: The type of picker to display (time, heightInches, heightCm).
    /// - onUpdate: A closure that is called when the selected values are updated.
    /// - Returns: A view that presents the picker sheet when `isPresented` is true.
    func pickerSheet<T: Hashable>(
        isPresented: Binding<Bool>,
        selectedValues: [T],
        options: [[T]],
        displayValue: @escaping (T) -> String,
        pickerType: PickerType = .default,
        onUpdate: @escaping ([T]) -> Void
    ) -> some View {
        modifier(
            PickerSheetModifier(
                isPresented: isPresented,
                selectedValues: selectedValues,
                options: options,
                displayValue: displayValue,
                pickerType: pickerType,
                onUpdate: onUpdate
            )
        )
    }
    
    /// Hides the keyboard by resigning the first responder status.
    func hideKeyboard() {
        let resign = #selector(UIResponder.resignFirstResponder)
        UIApplication.shared.sendAction(resign, to: nil, from: nil, for: nil)
    }
    
    /// Dismisses the keyboard when the view is dragged.
    func dismissKeyboardOnDrag() -> some View {
        self
            .contentShape(Rectangle()) // Ensures transparent areas can receive gestures
            .highPriorityGesture(
                DragGesture().onChanged { _ in
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
                }
            )
    }
    
    /// Applies a border to the view with customizable sides, thickness, and color.
    /// - Parameters:
    ///   - sides: An array of sides where the border should be applied.
    ///   - thickness: The thickness of the border.
    ///   - color: The color of the border. If nil, the default color will be used.
    ///   - Returns: A view with a border applied to the specified sides.
    func border(
        sides: [BorderModifier.Side] = [.top, .bottom, .leading, .trailing],
        thickness: CGFloat = 1,
        color: Color? = nil
    ) -> some View {
        modifier(BorderModifier(sides: sides, thickness: thickness, color: color))
    }
    
    /// Applies a modifier to set the row insets for settings views.
    /// - Returns: A view with modified row insets suitable for settings.
    func settingsRowInsets() -> some View {
        self.modifier(SettingsRowInsetModifier())
    }
}
