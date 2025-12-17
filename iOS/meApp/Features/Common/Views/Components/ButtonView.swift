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
    /// Controls both the alignment of the button's frame and the alignment of the text within the button.
    /// Adjust this to change how the button and its text are positioned horizontally.
    var alignment: Alignment = .center
    /// Optional override for background color for styles that support it (e.g., .filledPrimary)
    var backgroundColorOverride: Color? = nil
    /// Optional vertical padding for filled primary buttons. Default is false.
    var verticalPadding: Bool = false
    /// Optional horizontal padding for text buttons. Default is false.
    var padding: Bool = false
    /// Throttle interval in seconds. Default is 0.5 seconds. Set to 0 to disable throttling.
    /// With throttle, the first tap executes immediately, and subsequent taps within the interval are ignored.
    var throttleInterval: TimeInterval = 0.5
    let action: () -> Void
    
    @State private var lastTapTime: Date = Date.distantPast
    
    var body: some View {
        Button(action: {
            let now = Date()
            let timeSinceLastTap = now.timeIntervalSince(lastTapTime)
            
            // Execute immediately if throttle is disabled or enough time has passed since last tap
            if throttleInterval == 0 || timeSinceLastTap >= throttleInterval {
                lastTapTime = now
                action()
            }
        }) {
            Text(text.uppercased())
                .fontWeight(.bold)
                .fontOpenSans(size == .large ? .button1 : .button2)
                .if(!type.isInlineText) { view in
                    view.frame(minWidth: size == .small ? 75 : 96, minHeight: size == .small ? 30 : 40, alignment: alignment)
                }
                .multilineTextAlignment(.leading)
        }
        .buttonStyle(AppPressableButtonStyle(
            type: type,
            size: size,
            backgroundColorOverride: backgroundColorOverride,
            verticalPadding: verticalPadding,
            padding: padding
        ))
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

