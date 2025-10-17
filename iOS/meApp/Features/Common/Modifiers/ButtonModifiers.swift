//
//  ButtonModifiers.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import SwiftUI

struct BasicButtonStyle: ViewModifier {
    var foreGroundColor: Color
    @Environment(\.appTheme) var theme
    func body(content: Content) -> some View {
        content
            .foregroundColor(foreGroundColor)
    }
}

struct BorderedButtonStyle: ViewModifier {
    var backgroundColor: Color
    var borderColor: Color
    var buttonSize: ButtonSize
    var foregroundColor: Color? = nil
    func body(content: Content) -> some View{
        content
            .padding(.horizontal, buttonSize == .small ? .spacingXS : .spacingLG)
            .foregroundColor(foregroundColor ?? borderColor)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: .radiusPill)
                    .stroke(borderColor, lineWidth: 4)
            )
            .cornerRadius(.radiusPill)
            .contentShape(Rectangle())
    }
}

struct FlatButtonStyle: ViewModifier {
    var foregroundColor: Color
    var backgroundColor: Color
    var buttonSize: ButtonSize
    
    func body(content: Content) -> some View {
        content
            .padding(.horizontal, buttonSize == .small ? .spacingXS : .spacingLG)
            .foregroundColor(foregroundColor)
            .background(backgroundColor)
            .cornerRadius(.radiusPill)
            .contentShape(Rectangle())
         
    }
}


// MARK: - Pressed-state aware ButtonStyle

/// A unified ButtonStyle that applies pressed-state feedback for all ButtonType variants.
/// - For flat buttons: darkens background and scales slightly when pressed.
/// - For bordered buttons: darkens border/background and scales slightly when pressed.
/// - For basic/text buttons: adjusts foreground color opacity when pressed.
struct AppPressableButtonStyle: ButtonStyle {
    let type: ButtonType
    let size: ButtonSize
    let backgroundColorOverride: Color?
    @Environment(\.appTheme) private var theme

    func makeBody(configuration: Configuration) -> some View {
        let isPressed = configuration.isPressed

        switch type {
        case .filledPrimary:
            let baseBg = backgroundColorOverride ?? theme.actionPrimary
            let bg = isPressed ? theme.actionPrimaryPressed : baseBg
            let fg = theme.textInverse
            return AnyView(
                configuration.label
                    .modifier(FlatButtonStyle(
                        foregroundColor: fg,
                        backgroundColor: bg,
                        buttonSize: size
                    ))
            )

        case .filledSecondary:
            let baseBg = theme.actionInverse
            let bg = isPressed ? theme.actionInversePressed : baseBg
            let fg = theme.actionPrimary
            return AnyView(
                configuration.label
                    .modifier(FlatButtonStyle(
                        foregroundColor: fg,
                        backgroundColor: bg,
                        buttonSize: size
                    ))
            )

        case .outlinedPrimary:
            let baseBg = theme.actionInverse
            let bg = isPressed ? theme.actionInversePressed : baseBg
            let baseBorder = theme.actionPrimary
            let border = isPressed ? theme.actionPrimaryPressed : baseBorder
            let fg = theme.actionPrimary
            return AnyView(
                configuration.label
                    .modifier(BorderedButtonStyle(
                        backgroundColor: bg,
                        borderColor: border,
                        buttonSize: size,
                        foregroundColor: fg
                    ))
            )

        case .outlinedSecondary:
            let baseBg = theme.actionPrimary
            let bg = isPressed ? theme.actionPrimaryPressed : baseBg
            let baseBorder = theme.actionInverse
            let border = isPressed ? theme.actionInversePressed : baseBorder
            let fg = theme.actionInverse
            return AnyView(
                configuration.label
                    .modifier(BorderedButtonStyle(
                        backgroundColor: bg,
                        borderColor: border,
                        buttonSize: size,
                        foregroundColor: fg
                    ))
            )

        case .textPrimary, .inlineTextPrimary:
            let baseFg = theme.actionPrimary
            let fg = isPressed ? theme.actionPrimaryPressed : baseFg
            return AnyView(
                configuration.label
                    .modifier(BasicButtonStyle(foreGroundColor: fg))
            )

        case .textSecondary, .inlineTextSecondary:
            let baseFg = theme.textInverse
            let fg = isPressed ? theme.actionInversePressed : baseFg
            return AnyView(
                configuration.label
                    .modifier(BasicButtonStyle(foreGroundColor: fg))
            )

        case .textTertiary, .inlineTextTertiary:
            let baseFg = theme.actionTertiary
            let fg = isPressed ? theme.actionTertiaryPressed : baseFg
            return AnyView(
                configuration.label
                    .modifier(BasicButtonStyle(foreGroundColor: fg))
            )
        }
    }
}
