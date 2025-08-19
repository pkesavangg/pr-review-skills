//
//  CustomButton.swift
//  meApp
//
//  Created by Lakshmi Priya on 10/06/25.
//

import Foundation
import SwiftUI

struct ButtonView: View {
    let text: String
    let type: ButtonType
    let size: ButtonSize
    let isDisabled: Bool
    let action: () -> Void
    
    var body: some View {
        Button(action: action) {
            Text(text.uppercased())
                .fontOpenSans(size == .large ? .button1 : .button2)
                .fontWeight(.bold)
                .modifier(CustomButtonStyle(type: type, buttonSize: size))
                .frame(minWidth: 96)
        }
        .disabled(isDisabled)
        .opacity(isDisabled ? 0.5 : 1.0)
    }
}

/// A view for testing and previewing various button styles and states in the app.
struct TestingCommonButtonsView: View {
    @Environment(\.appTheme) var theme

    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 20) {
                //Filled Primary buttons
                ButtonView(text: "button", type: .filledPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .filledPrimary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .filledPrimary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .filledPrimary, size: .small, isDisabled: true, action: {})
                
                // Filled Secondary  buttons
                ButtonView(text: "button", type: .filledSecondary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .filledSecondary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .filledSecondary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .filledSecondary, size: .small, isDisabled: true, action: {})

                // outlined Primary buttons
                ButtonView(text: "button", type: .outlinedPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .outlinedPrimary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .outlinedPrimary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .outlinedPrimary, size: .small, isDisabled: true, action: {})
                
                // outlinedSecondary buttons
                ButtonView(text: "button", type: .outlinedSecondary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .outlinedSecondary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .outlinedSecondary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .outlinedSecondary, size: .small, isDisabled: true, action: {})
                
                // textPrimary buttons
                ButtonView(text: "button", type: .textPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textPrimary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textPrimary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .textPrimary, size: .small, isDisabled: true, action: {})
                                
                // textSecondary buttons
                ButtonView(text: "button", type: .textSecondary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textSecondary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textSecondary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .textSecondary, size: .small, isDisabled: true, action: {})
                
                // textTertiary buttons
                ButtonView(text: "button", type: .textTertiary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textTertiary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .textTertiary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .textTertiary, size: .small, isDisabled: true, action: {})
                
                // inlineTextPrimary buttons
                ButtonView(text: "button", type: .inlineTextPrimary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextPrimary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextPrimary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .inlineTextPrimary, size: .small, isDisabled: true, action: {})
                
                // inlineTextSecondary buttons
                ButtonView(text: "button", type: .inlineTextSecondary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextSecondary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextSecondary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .inlineTextSecondary, size: .small, isDisabled: true, action: {})
                                
                // inlineTextTertiary buttons
                ButtonView(text: "button", type: .inlineTextTertiary, size: .large, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextTertiary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .inlineTextTertiary, size: .large, isDisabled: true, action: {})
                ButtonView(text: "button", type: .inlineTextTertiary, size: .small, isDisabled: true, action: {})
             }
            .padding()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        TestingCommonButtonsView()
            .background(.gray)
    }
}

