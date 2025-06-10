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
    
    /// Applies the appropriate button style based on the provided button type, state, and appearance parameters.
    /// - Parameters:
    ///   - type: The type of button style to apply (e.g., stroked, flat, basic).
    ///   - isDisabled: A Boolean value indicating whether the button is disabled.
    ///   - textColor: The color to use for the button's text (used for basic type).
    ///   - backgroundColor: The background color of the button.
    ///   - borderColor: The color of the button's border (used for stroked type).
    ///   - cornerRadius: The corner radius of the button.
    ///   - theme: The color palette to use for default values.
    ///   - buttonSize: The size of the button.
    /// - Returns: A view styled according to the specified button type and state.
    @ViewBuilder
    func applyButtonStyle(
        for type: ButtonType,
        isDisabled: Bool,
        textColor: Color?,
        backgroundColor: Color?,
        borderColor: Color?,
        cornerRadius: CGFloat?,
        theme: AppColors.Palette,
        buttonSize: ButtonSize
    ) -> some View {
        switch type {
        case .stroked:
            // Apply bordered or disabled bordered style for stroked buttons
            if isDisabled {
                self.disabledBorderedbuttonStyle(
                    backgroundColor: backgroundColor ?? theme.textInverse,
                    borderColor: borderColor ?? theme.actionPrimaryDisabled,
                    cornerRadius: cornerRadius ?? .radiusPill,
                    buttonSize: buttonSize
                )
            } else {
                self.borderedButtonStyle(
                    backgroundColor: backgroundColor ?? theme.textInverse,
                    borderColor: borderColor ?? theme.actionPrimary,
                    cornerRadius: cornerRadius ?? .radiusPill,
                    buttonSize: buttonSize
                )
            }

        case .flat:
            // Apply flat or disabled flat style for flat buttons
            if isDisabled {
                self.disabledflatButtonStyle(
                    backgroundColor: backgroundColor ?? theme.actionPrimaryDisabled,
                    cornerRadius: cornerRadius ?? .radiusPill,
                    buttonSize: buttonSize
                )
            } else {
                self.flatButtonStyle(
                    backgroundColor: backgroundColor ?? theme.actionPrimary,
                    cornerRadius: cornerRadius ?? .radiusPill,
                    buttonSize: buttonSize
                )
            }

        case .basic:
            // Apply basic or disabled basic style for basic buttons
            if isDisabled {
                self.disabledBasicButtonStyle(foregroundColor: textColor ?? theme.actionPrimaryDisabled)
            } else {
                self.basicButtonStyle(foregroundColor: textColor ?? theme.actionPrimary)
            }
        }
    }
    
    /// Applies the basic button style with the specified foreground color.
    /// - Parameter foregroundColor: The color to use for the button's foreground.
    /// - Returns: A view styled as a basic button.
    public func basicButtonStyle(foregroundColor: Color) -> some View{
        self.modifier(BasicButtonStyle(foreGroundColor: foregroundColor))
    }
    
    /// Applies the disabled basic button style with the specified foreground color.
    /// - Parameter foregroundColor: The color to use for the button's foreground when disabled.
    /// - Returns: A view styled as a disabled basic button.
    public func disabledBasicButtonStyle(foregroundColor: Color) -> some View {
        self.modifier(DisabledBasicButtonStyle(foreGroundColor: foregroundColor))
    }
    
    /// Applies a bordered button style with customizable background, border, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - borderColor: The color of the button's border.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button.
    /// - Returns: A view styled as a bordered button.
    public func borderedButtonStyle(backgroundColor: Color, borderColor: Color, cornerRadius: CGFloat, buttonSize: ButtonSize) -> some View {
        self.modifier(BorderedButtonStyle(backgroundColor: backgroundColor, borderColor: borderColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
    }
    
    /// Applies a disabled bordered button style with customizable background, border, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button when disabled.
    ///   - borderColor: The color of the button's border when disabled.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button.
    /// - Returns: A view styled as a disabled bordered button.
    public func disabledBorderedbuttonStyle(backgroundColor: Color, borderColor: Color, cornerRadius: CGFloat, buttonSize: ButtonSize) -> some View {
        self.modifier(DisabledBorderedButtonStyle(backgroundColor: backgroundColor, borderColor: borderColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
    }
    
    /// Applies a flat button style with customizable background, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button.
    /// - Returns: A view styled as a flat button.
    public func flatButtonStyle(backgroundColor: Color, cornerRadius: CGFloat, buttonSize: ButtonSize) -> some View {
        self.modifier(FlatButtonStyle(backgroundColor: backgroundColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
    }
    
    /// Applies a disabled flat button style with customizable background, corner radius, and size.
    /// - Parameters:
    ///   - backgroundColor: The background color of the button when disabled.
    ///   - cornerRadius: The corner radius of the button.
    ///   - buttonSize: The size of the button.
    /// - Returns: A view styled as a disabled flat button.
    public func disabledflatButtonStyle(backgroundColor: Color, cornerRadius: CGFloat, buttonSize: ButtonSize) -> some View {
        self.modifier(DisabledFlatButtonStyle(backgroundColor: backgroundColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
    } 
    
}
