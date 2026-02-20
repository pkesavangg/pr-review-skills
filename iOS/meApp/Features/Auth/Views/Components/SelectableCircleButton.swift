//
//  SelectableCircleButton.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

struct SelectableCircleButton: View {
    @Environment(\.appTheme) private var theme
    let label: String
    let isSelected: Bool
    let action: () -> Void
    let size: CGFloat = 100
    var body: some View {
        Button(action: action) {
            Text(label)
                .fontOpenSans(.heading5)
                .foregroundColor(isSelected ? theme.actionInverse : theme.actionPrimary)
                .frame(width: size, height: size)
                .background(isSelected ? theme.actionPrimary : Color.clear)
                .overlay(
                    Circle()
                        .stroke(theme.actionPrimary, lineWidth: 2)
                )
                .clipShape(Circle())
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    SelectableCircleButton(label: "Male", isSelected: true) {
        print("Male button pressed")
    }
}
