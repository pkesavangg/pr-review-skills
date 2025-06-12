//
//  CustomToggleStyle.swift
//  meApp
//
//  Created by Lakshmi Priya on 11/06/25.
//

import SwiftUI

/// Rounded switch style with customizable colors.
struct CustomToggleStyle: ToggleStyle {
    let onColor: Color
    let offColor: Color
    let thumbColor: Color
    let isDisabled: Bool

    func makeBody(configuration: Configuration) -> some View {
        RoundedRectangle(cornerRadius: .radiusLG)
            .fill(configuration.isOn ? onColor : offColor)
            .frame(width: 50, height: 30)
            .opacity(isDisabled ? 0.5 : 1.0)
            .overlay(
                Circle()
                    .fill(thumbColor)
                    .shadow(radius: 1)
                    .frame(width: 27, height: 27)
                    .offset(x: configuration.isOn ? 10 : -10)
                    .animation(.spring(response: 0.2), value: configuration.isOn)
            )
            .onTapGesture {
                if !isDisabled {
                    withAnimation { configuration.isOn.toggle() }
                }
            }
    }
}
