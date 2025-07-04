//
//  BorderModifier.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//

import SwiftUI

// MARK: - BorderModifier
/// A view modifier that adds borders to specified sides of a view.
/// Usage:
/// ```swift
/// Text("Hello, World!")
///    .modifier(BorderModifier(sides: [.top, .bottom], thickness: 2, color: .blue))
/// ```
struct BorderModifier: ViewModifier {
    @Environment(\.appTheme) var theme
    enum Side: CaseIterable, Hashable {
        case top, bottom, leading, trailing
    }

    let sides: [Side]
    let thickness: CGFloat
    var color: Color?

    func body(content: Content) -> some View {
        content.overlay(
            ZStack {
                ForEach(sides, id: \.self) { side in
                    borderView(for: side)
                }
            }
        )
    }

    @ViewBuilder
    private func borderView(for side: Side) -> some View {
        switch side {
        case .top:
            makeBorder(width: nil, height: thickness, alignment: .top)
        case .bottom:
            makeBorder(width: nil, height: thickness, alignment: .bottom)
        case .leading:
            makeBorder(width: thickness, height: nil, alignment: .leading)
        case .trailing:
            makeBorder(width: thickness, height: nil, alignment: .trailing)
        }
    }

    private func makeBorder(width: CGFloat?, height: CGFloat?, alignment: Alignment) -> some View {
        Rectangle()
            .frame(width: width, height: height)
            .foregroundColor(color ?? theme.statusUtilityPrimary)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: alignment)
    }
}
