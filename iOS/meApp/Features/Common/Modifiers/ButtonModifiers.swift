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
    var cornerRadius: CGFloat
    var buttonSize: ButtonSize
    func body(content: Content) -> some View{
        content
            .padding(buttonSize == .small ? 5 : 10)
            .padding(.horizontal, buttonSize == .small ? 11 : 22)
            .foregroundColor(borderColor)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(borderColor, lineWidth: 2)
            )
            .cornerRadius(cornerRadius)
    }
}

struct FlatButtonStyle: ViewModifier {
    var backgroundColor: Color
    var cornerRadius: CGFloat
    var buttonSize: ButtonSize
    @Environment(\.appTheme) var theme
    func body(content: Content) -> some View {
        content
            .padding(buttonSize == .small ? 5 : 10)
            .padding(.horizontal, buttonSize == .small ? 11 : 22)
            .foregroundColor(theme.textInverse)
            .background(backgroundColor)
            .fontWeight(.bold)
            .cornerRadius(cornerRadius)
    }
}

struct CustomButtonStyle: ViewModifier {
    let type: ButtonType
    let buttonSize: ButtonSize
    @Environment(\.appTheme) var theme
    
    func body(content: Content) -> some View {
        switch type {
            // FLAT BUTTONS
        case .primary:
            content
                .flatButtonStyle(
                    backgroundColor: theme.actionPrimary,
                    cornerRadius: .radiusPill,
                    buttonSize: buttonSize
                )
                .foregroundColor(theme.textInverse)
        case .primaryInverse:
            content
                .flatButtonStyle(
                    backgroundColor: theme.textInverse,
                    cornerRadius: .radiusPill,
                    buttonSize: buttonSize
                )
                .foregroundColor(theme.actionPrimary)
            // STROKED BUTTONS
        case .secondary:
            content
                .borderedButtonStyle(
                    backgroundColor: theme.textInverse,
                    borderColor: theme.actionPrimary,
                    cornerRadius: .radiusPill,
                    buttonSize: buttonSize
                )
                .foregroundColor(theme.actionPrimary)
        case .secondaryInverse:
            content
                .borderedButtonStyle(
                    backgroundColor: theme.actionPrimary,
                    borderColor: theme.textInverse,
                    cornerRadius: .radiusPill,
                    buttonSize: buttonSize
                )
                .foregroundColor(theme.textInverse)
            // BASIC BUTTONS
        case .linkBlueDefault, .linkBlueInline:
            content.basicButtonStyle(foregroundColor: theme.actionPrimary)
        case .linkWhiteDefault, .linkWhiteInline:
            content.basicButtonStyle(foregroundColor: theme.textInverse)
        case .smallTertiaryLink:
            content.basicButtonStyle(foregroundColor: theme.actionTertiary)
        }
    }
}

