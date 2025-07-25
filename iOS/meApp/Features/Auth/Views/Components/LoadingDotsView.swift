// LoadingDotsView.swift
// meApp
// Created for animated three-dot loading effect

import SwiftUI

/// A view that displays an animated three-dot loading effect
struct LoadingDotsView: View {
    // MARK: - Properties
    @StateObject private var viewModel: LoadingDotsViewModel
    
    // MARK: - Initialization
    init(
        jumpUpHeight: CGFloat = 6,
        jumpDownHeight: CGFloat = 2.5,
        dotSize: CGFloat = .spacingXS / 2,
        spacing: CGFloat = 4,
        animationDuration: Double = 1.5,
        color: Color = .primary
    ) {
        self._viewModel = StateObject(wrappedValue: LoadingDotsViewModel(
            jumpUpHeight: jumpUpHeight,
            jumpDownHeight: jumpDownHeight,
            dotSize: dotSize,
            spacing: spacing,
            animationDuration: animationDuration,
            color: color
        ))
    }
    
    // MARK: - Body
    var body: some View {
        TimelineView(.animation) { context in
            let time = context.date.timeIntervalSinceReferenceDate
            
            HStack(spacing: viewModel.spacing) {
                ForEach(0..<viewModel.dotCount, id: \.self) { index in
                    let phase = viewModel.phaseFor(index: index, currentTime: time)
                    let (offsetY, scale) = viewModel.interpolatedAnimationState(for: phase)
                    
                    Circle()
                        .frame(width: viewModel.dotSize, height: viewModel.dotSize)
                        .foregroundColor(viewModel.color)
                        .scaleEffect(scale)
                        .offset(y: offsetY)
                }
            }
        }
    }
}

// MARK: - Preview
#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        LoadingDotsView()
    }
}
