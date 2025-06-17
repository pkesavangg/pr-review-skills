//
//  LoadingSpinnerIndicator.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/06/25.
//
import SwiftUI

struct LoadingSpinnerIndicator: View {
    @State private var isAnimating = false
    @Environment(\.appTheme) private var theme
    var color: Color?
    var size: CGFloat = 22
    var body: some View {
        let spinnerColor = color ?? theme.textHeading

        AppIconView(icon: AppAssets.loader, size: IconSize(width: size, height: size))
            .foregroundColor(spinnerColor)
            .rotationEffect(.degrees(isAnimating ? 360 : 0))
            .animation(.linear(duration: 1.0).repeatForever(autoreverses: false), value: isAnimating)
            .onAppear {
                isAnimating = true
            }
    }
}

#Preview(body: {
    VStack {
        LoadingSpinnerIndicator()
        
        LoadingSpinnerIndicator(color: .red, size: 50)
    }
})
