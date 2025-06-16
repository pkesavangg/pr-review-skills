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
    enum Side {
        case top, bottom, leading, trailing
    }

    let sides: [Side]
    let thickness: CGFloat
    let color: Color

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
            Rectangle()
                .frame(height: thickness)
                .foregroundColor(color)
                .alignmentGuide(.top) { $0[.top] }
                .frame(maxHeight: .infinity, alignment: .top)
        case .bottom:
            Rectangle()
                .frame(height: thickness)
                .foregroundColor(color)
                .frame(maxHeight: .infinity, alignment: .bottom)
        case .leading:
            Rectangle()
                .frame(width: thickness)
                .foregroundColor(color)
                .frame(maxWidth: .infinity, alignment: .leading)
        case .trailing:
            Rectangle()
                .frame(width: thickness)
                .foregroundColor(color)
                .frame(maxWidth: .infinity, alignment: .trailing)
        }
    }
}
