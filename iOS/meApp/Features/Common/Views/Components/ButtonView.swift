//
//  CustomButton.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import Foundation
import SwiftUI

struct ButtonView: View {
    let type: ButtonType
    let buttonSize: ButtonSize
    let text: String
    let isDisabled: Bool
    var textColor: Color?
    var backgroundColor: Color?
    var borderColor: Color?
    var cornerRadius: CGFloat?
    let action: (() -> Void)?
    
    @Environment(\.appTheme) var theme
    
    var fontStyle: CustomTextStyle {(buttonSize == .small ? .button2 : .button1)}
    
    var body: some View {
           Button(action: {
               action?()
           }) {
               Text(text.uppercased())
                   .fontOpenSans(fontStyle)
                   .fontWeight(.bold)
                   .foregroundColor(textColor ?? theme.textInverse)
                   .applyButtonStyle(
                       for: type,
                       isDisabled: isDisabled,
                       textColor: textColor,
                       backgroundColor: backgroundColor,
                       borderColor: borderColor,
                       cornerRadius: cornerRadius,
                       theme: theme,
                       buttonSize: buttonSize
                   )
           }
           .disabled(isDisabled)
       }
}

/// A view for testing and previewing various button styles and states in the app.
struct TestingCommonButtonsView: View {
    @Environment(\.appTheme) var theme

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 20) {
                // Basic buttons with actionPrimary color
                ButtonView(type: .basic, buttonSize: .regular, text: "button", isDisabled: false, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .regular, text: "disabled", isDisabled: true, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .small, text: "small", isDisabled: false, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .small, text: "small", isDisabled: true, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                
                // Basic buttons with textInverse color
                ButtonView(type: .basic, buttonSize: .regular, text: "button", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .regular, text: "disabled", isDisabled: true, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .small, text: "small", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .basic, buttonSize: .small, text: "small", isDisabled: true, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                
                // Stroked buttons with various states and colors
                ButtonView(type: .stroked, buttonSize: .regular, text: "stroked button", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .stroked, buttonSize: .regular, text: "stroked button", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, borderColor: theme.textInverse, action: {})
                ButtonView(type: .stroked, buttonSize: .regular, text: "stroked button", isDisabled: true, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .stroked, buttonSize: .small, text: "small", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .stroked, buttonSize: .small, text: "small", isDisabled: true, textColor: theme.actionPrimary, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .stroked, buttonSize: .small, text: "small", isDisabled: true, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, borderColor: theme.textInverse, action: {})
                
                // Flat buttons with various states
                ButtonView(type: .flat, buttonSize: .regular, text: "Flat button", isDisabled: false, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .flat, buttonSize: .regular, text: "Flat button", isDisabled: true, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .flat, buttonSize: .regular, text: "Flat button", isDisabled: false, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .flat, buttonSize: .small, text: "small", isDisabled: false, backgroundColor: theme.actionPrimary, action: {})
                ButtonView(type: .flat, buttonSize: .small, text: "small", isDisabled: true, backgroundColor: theme.actionPrimary, action: {})
                
                // Custom stroked buttons for demonstration
                ButtonView(type: .stroked, buttonSize: .regular, text: "custom stroked regular", isDisabled: false, textColor: theme.actionPrimary, backgroundColor: theme.textInverse, borderColor: theme.actionPrimary, action: {})
                ButtonView(type: .stroked, buttonSize: .regular, text: "custom stroked regular", isDisabled: false, textColor: theme.textInverse, backgroundColor: theme.actionPrimary, borderColor: theme.textInverse, action: {})
            }
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        TestingCommonButtonsView()
    }
}

