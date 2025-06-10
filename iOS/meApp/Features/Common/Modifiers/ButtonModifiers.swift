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

struct DisabledBasicButtonStyle: ViewModifier {
    var foreGroundColor: Color
    @Environment(\.appTheme) var theme
    func body(content: Content) -> some View{
        content
            .modifier(BasicButtonStyle(foreGroundColor: foreGroundColor))
            .opacity(0.5)
            
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

struct DisabledBorderedButtonStyle: ViewModifier {
    var backgroundColor: Color
    var borderColor: Color
    var cornerRadius: CGFloat
    var buttonSize: ButtonSize
    func body(content: Content) -> some View {
        content
            .modifier(BorderedButtonStyle(backgroundColor: backgroundColor, borderColor: borderColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
            .opacity(0.5)
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

struct DisabledFlatButtonStyle: ViewModifier {
    var backgroundColor: Color
    var cornerRadius: CGFloat
    var buttonSize: ButtonSize
    func body(content: Content) -> some View {
        content
            .modifier(FlatButtonStyle(backgroundColor: backgroundColor, cornerRadius: cornerRadius, buttonSize: buttonSize))
            .opacity(0.5)
    }
}
