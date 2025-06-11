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
                .fontOpenSans(size == .regular ? .button1 : .button2)
                .modifier(CustomButtonStyle(type: type, buttonSize: size))               
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
                //Primary buttons
                ButtonView(text: "button", type: .primary, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .primary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .primary, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .primary, size: .small, isDisabled: true, action: {})
                
                //Primary inverse  buttons
                ButtonView(text: "button", type: .primaryInverse, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .primaryInverse, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .primaryInverse, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .primaryInverse, size: .small, isDisabled: true, action: {})
                
                // secondary buttons
                ButtonView(text: "button", type: .secondary, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .secondary, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .secondary, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .secondary, size: .small, isDisabled: true, action: {})
                
                // secondary inverse button
                ButtonView(text: "button", type: .secondaryInverse, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .secondaryInverse, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .secondaryInverse, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .secondaryInverse, size: .small, isDisabled: true, action: {})
                
                // tertiary Buttons
                ButtonView(text: "Button", type: .smallTertiaryLink, size: .small, isDisabled: false, action: {})
                ButtonView(text: "Button", type: .smallTertiaryLink, size: .small, isDisabled: true, action: {})
                
                // Links
                //link blue default
                ButtonView(text: "button", type: .linkBlueDefault, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkBlueDefault, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkBlueDefault, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .linkBlueDefault, size: .small, isDisabled: true, action: {})
                
                //Link blue inline
                ButtonView(text: "button", type: .linkBlueInline, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkBlueInline, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkBlueInline, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .linkBlueInline, size: .small, isDisabled: true, action: {})
                
                
                //link White Default
                ButtonView(text: "button", type: .linkWhiteDefault, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkWhiteDefault, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkWhiteDefault, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .linkWhiteDefault, size: .small, isDisabled: true, action: {})
                
                //Link White inline
                ButtonView(text: "button", type: .linkWhiteInline, size: .regular, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkWhiteInline, size: .small, isDisabled: false, action: {})
                ButtonView(text: "button", type: .linkWhiteInline, size: .regular, isDisabled: true, action: {})
                ButtonView(text: "button", type: .linkWhiteInline, size: .small, isDisabled: true, action: {})
                
            }
            .padding()
        }
    }
}

struct ContentView_Previews: PreviewProvider {
    static var previews: some View {
        TestingCommonButtonsView()
    }
}

