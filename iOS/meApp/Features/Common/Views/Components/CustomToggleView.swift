//
//  CustomToggleView.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// Toggle with optional label and customizable styling.
struct CustomToggleView: View {
    @Binding var isOn: Bool
    let text: String?
    let onColor: Color
    let offColor: Color
    let thumbColor: Color
    let isDisabled: Bool
    let toggleTintColor: Color?
    let textColor: Color?
    let textFont: CustomTextStyle?
    @Environment(\.appTheme) var theme
    
    init(
        isOn: Binding<Bool>,
        text: String? = nil,
        onColor: Color = .blue800,
        offColor: Color = .neutral400,
        thumbColor: Color = .neutral100,
        isDisabled: Bool = false,
        toggleTintColor: Color? = nil,
        textColor: Color? = nil,
        textFont: CustomTextStyle? = nil
    ) {
        self._isOn = isOn
        self.text = text
        self.onColor = onColor
        self.offColor = offColor
        self.thumbColor = thumbColor
        self.isDisabled = isDisabled
        self.toggleTintColor = toggleTintColor
        self.textColor = textColor
        self.textFont = textFont
    }
    
    private var foregroundColor: Color {
        isDisabled ? theme.actionTertiaryDisabled : (textColor ?? theme.textBody)
    }
    
    private var toggleForegroundColor: Color {
        isDisabled ? theme.actionTertiaryDisabled : (toggleTintColor ?? theme.actionPrimary)
    }
    
    private var opacity: Double {
        isDisabled ? 0.5 : 1.0
    }
    
    var body: some View {
        HStack {
            if let text = text {
                Text(text)
                    .fontOpenSans(textFont ?? .body2)
                    .foregroundColor(foregroundColor)
                    .opacity(opacity)
            }
            
            Spacer()
            
            Toggle("", isOn: $isOn)
                .labelsHidden()
                .accessibilityLabel(text ?? "Toggle")
                .toggleStyle(CustomToggleStyle(
                    onColor: onColor,
                    offColor: offColor,
                    thumbColor: thumbColor,
                    isDisabled: isDisabled
                ))
                .disabled(isDisabled)
        }
        .frame(height: 44)
    }
}

/// Demo/testing view for CustomToggleView
struct TestingCommonTogglesView: View {
    @Environment(\.appTheme) var theme
    @State private var toggle1 = true
    @State private var toggle2 = false
    @State private var toggle3 = true
    @State private var toggle4 = false
    @State private var toggle5 = true
    @State private var toggle6 = false
    @State private var toggle7 = true
    @State private var toggle8 = false
    @State private var toggle9 = true
    
    var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 24) {
                // Default/Basic
                CustomToggleView(isOn: $toggle1, text: "Basic Toggle")
                CustomToggleView(isOn: $toggle2, text: "Basic Toggle (Off)")
                
                // Custom visual properties
                CustomToggleView(
                    isOn: $toggle3,
                    text: "Custom Colors",
                    onColor: .green,
                    offColor: .gray.opacity(0.2),
                    thumbColor: .blue,
                    toggleTintColor: .purple,
                    textColor: .orange,
                    textFont: .heading5
                )
                
                // Disabled state
                CustomToggleView(
                    isOn: $toggle4,
                    text: "Disabled Toggle",
                    isDisabled: true
                )
                
                // Custom font (bold/heading)
                CustomToggleView(
                    isOn: $toggle5,
                    text: "Large Heading Font",
                    textFont: .heading5
                )
                
                // Custom thumb
                CustomToggleView(
                    isOn: $toggle6,
                    text: "Yellow Thumb",
                    thumbColor: .yellow
                )
                
                // Custom tint color, no explicit on/off color
                CustomToggleView(
                    isOn: $toggle7,
                    text: "ToggleTint (actionPrimary)",
                    toggleTintColor: theme.actionPrimary
                )
                
                // Custom text color
                CustomToggleView(
                    isOn: $toggle8,
                    text: "Coloured text",
                    textColor: theme.textError
                )
                
                // ON color only (OFF will use actionTertiaryDisabled)
                CustomToggleView(
                    isOn: $toggle9,
                    text: "Green When ON",
                    onColor: .green
                )
            }
            .padding()
        }
    }
}

#Preview {
    TestingCommonTogglesView()
}
