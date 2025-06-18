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
    func body(content: Content) -> some View{
        content
            .frame(width: buttonSize  == .small ? 47 : 96)
            .fontWeight(.bold)
            .padding(.vertical, buttonSize == .small ? .spacingXS/2 : .spacingXS)
            .padding(.horizontal, buttonSize == .small ? .spacingSM : .spacingLG)
            .foregroundColor(borderColor)
            .background(backgroundColor)
            .overlay(
                RoundedRectangle(cornerRadius: .radiusPill)
                    .stroke(borderColor, lineWidth: 2)
            )
            .cornerRadius(.radiusPill)
            .contentShape(Rectangle())
            .frame(minWidth:  buttonSize  == .small ? 75 : 160, minHeight: buttonSize  == .small ? 30 :40)
    }
}

struct FlatButtonStyle: ViewModifier {
    var foregroundColor: Color
    var backgroundColor: Color
    var buttonSize: ButtonSize
    
    func body(content: Content) -> some View {
        content
            .frame(width: buttonSize  == .small ? 47 : 96)
            .padding(.vertical, buttonSize == .small ? .spacingXS/2 : .spacingXS)
            .padding(.horizontal, buttonSize == .small ? .spacingSM : .spacingLG)
            .foregroundColor(foregroundColor)
            .background(backgroundColor)
            .cornerRadius(.radiusPill)
            .contentShape(Rectangle())
            .frame(minWidth:  buttonSize  == .small ? 75 : 160, minHeight: buttonSize  == .small ? 30 :40)
    }
}

struct CustomButtonStyle: ViewModifier {
    let type: ButtonType
    let buttonSize: ButtonSize
    @Environment(\.appTheme) var theme
    
    func body(content: Content) -> some View {
        switch type {
        case .filledPrimary:
            content
                .flatButtonStyle(
                    foregroundColor: theme.textInverse,
                    backgroundColor: theme.actionPrimary,
                    buttonSize: buttonSize
                )
            
        case .filledSecondary:
            content
                .flatButtonStyle(
                    foregroundColor: theme.actionPrimary,
                    backgroundColor: theme.textInverse,
                    buttonSize: buttonSize
                )
        case .outlinedPrimary:
            content
                .borderedButtonStyle(
                    backgroundColor: theme.textInverse,
                    borderColor: theme.actionPrimary,
                    buttonSize: buttonSize
                )
            
        case .outlinedSecondary:
            content
                .borderedButtonStyle(
                    backgroundColor: theme.actionPrimary,
                    borderColor: theme.textInverse,
                    buttonSize: buttonSize
                )
        case .textPrimary, .inlineTextPrimary:
            content
                .basicButtonStyle(foregroundColor: theme.actionPrimary)
            
        case .textSecondary, .inlineTextSecondary:
            content
                .basicButtonStyle(foregroundColor: theme.textInverse)
            
        case .textTertiary, .inlineTextTertiary:
            content
                .basicButtonStyle(foregroundColor: theme.actionTertiary)
        }
    }
}
