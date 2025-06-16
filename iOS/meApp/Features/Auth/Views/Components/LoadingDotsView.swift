// LoadingDotsView.swift
// meApp
// Created for animated three-dot loading effect

import SwiftUI

struct LoadingDotsView: View {
    let dotCount: Int = 3
    var jumpHeight: CGFloat = .spacingXS
    var animationDuration: Double = 1.5
    var color: Color = .primary
    var dotSize: CGFloat = 4
    
    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date.timeIntervalSinceReferenceDate
            let progress = now.truncatingRemainder(dividingBy: animationDuration) / animationDuration
            
            HStack(spacing: .spacingXS) {
                ForEach(0..<dotCount, id: \.self) { index in
                    let phase = (progress - Double(index) * 0.16 + 1.0).truncatingRemainder(dividingBy: 1.0)
                    let y = -sin(phase * .pi) * jumpHeight
                    
                    Circle()
                        .frame(width: dotSize, height: dotSize)
                        .foregroundColor(color)
                        .offset(y: y)
                }
            }
        }
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        LoadingDotsView(
            jumpHeight: 8,
            animationDuration: 1.5,
            color: .blue,
            dotSize: 8
        )
    }
}
