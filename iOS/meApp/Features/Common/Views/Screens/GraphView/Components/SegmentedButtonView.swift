//
//  SegmentedPickerView.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//
import SwiftUI

struct SegmentedButtonView<T: CaseIterable & RawRepresentable & Identifiable & Hashable>: View where T.RawValue == String {
    let segments: [T]
    @Binding var selectedSegment: T
    @Environment(\.appTheme) private var theme
    @State private var segmentSize: CGSize = .zero
    
    var body: some View {
        HStack(spacing: 0) {
            ForEach(Array(segments.enumerated()), id: \.element) { index, segment in
                Button(action: {
                    withAnimation(.spring(response: 0.4, dampingFraction: 0.8, blendDuration: 0)) {
                        selectedSegment = segment
                    }
                }) {
                    Text(segment.rawValue.uppercased())
                        .fontWeight(.bold)
                        .foregroundColor(selectedSegment == segment ? theme.textInverse : theme.actionSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(
                            GeometryReader { geometry in
                                Color.clear
                                    .onAppear {
                                        segmentSize = geometry.size
                                    }
                            }
                        )
                }
                .zIndex(1)
            }
        }
        .background(
            // Animated background
            RoundedRectangle(cornerRadius: .radiusMD)
                .fill(theme.actionSecondary)
                .frame(width: segmentSize.width)
                .offset(x: calculateOffset())
                .animation(.spring(response: 0.4, dampingFraction: 0.8, blendDuration: 0), value: selectedSegment)
        )
        .clipShape(RoundedRectangle(cornerRadius: .radiusMD))
    }
    
    private func calculateOffset() -> CGFloat {
        guard let selectedIndex = segments.firstIndex(where: { $0.id == selectedSegment.id }) else {
            return 0
        }
        
        let segmentWidth = segmentSize.width
        let totalWidth = segmentWidth * CGFloat(segments.count)
        let startPosition = -totalWidth / 2 + segmentWidth / 2
        
        return startPosition + (segmentWidth * CGFloat(selectedIndex))
    }
}

// MARK: - Preview
#Preview(body: {
    @Previewable
    @State var selectedSegment: GoalTypeSegment = .loseGain
    SegmentedButtonView(
        segments: GoalTypeSegment.allCases,
        selectedSegment: $selectedSegment
    )
})
