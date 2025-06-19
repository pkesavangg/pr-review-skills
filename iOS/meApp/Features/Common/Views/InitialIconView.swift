//
//  InitialIconView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 18/06/25.
//
import SwiftUI

// MARK: - Initial Icon View
/// A view that displays an initial icon with customizable styles and colors.
struct InitialIconView: View {
    @Environment(\.appTheme) private var theme
    let character: String
    var textColor: Color? = nil
    var backgroundColor: Color? = nil
    let size: CGFloat
    let style: InitialIconStyle

    var body: some View {
        let textColor = textColor ?? theme.backgroundPrimary
        let backgroundColor = backgroundColor ?? theme.statusIconPrimary
        Text(character)
            .font(.system(size: size * 0.5, weight: .semibold))
            .foregroundColor(style == .fill ? textColor : backgroundColor)
            .frame(width: size, height: size)
            .background(
                Group {
                    if style == .fill {
                        backgroundColor
                    } else {
                        Color.clear
                    }
                }
            )
            .overlay(
                Circle()
                    .stroke(backgroundColor, lineWidth: style == .outline ? 4 : 0)
            )
            .clipShape(Circle())
    }
}

#Preview {
    VStack(spacing: 20) {
        InitialIconView(
            character: "K",
            size: 50,
            style: .fill
        )
        
        InitialIconView(
            character: "B",
            size: 50,
            style: .outline
        )
    }
}
