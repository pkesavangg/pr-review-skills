//
//  SegmentedPickerView.swift
//  meApp
//
//  Created by Lakshmi Priya on 09/06/25.
//

import SwiftUI

struct SegmentedPickerView<T: CaseIterable & RawRepresentable & Identifiable & Hashable>: View where T.RawValue == String {
    let segments: [T]
    @Binding var selectedSegment: T
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        HStack(spacing: 0) {
            ForEach(segments) { segment in
                Button(action: { selectedSegment = segment }) {
                    Text(segment.rawValue.uppercased())
                        .fontWeight(.bold)
                        .foregroundColor(selectedSegment == segment ? theme.textInverse : theme.actionSecondary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                        .padding(.horizontal, 12)
                        .background(
                            selectedSegment == segment
                            ? theme.actionSecondary
                            : theme.textInverse
                        )
                        .cornerRadius(12)
                }
            }
        }
        .padding(.vertical, 18)
        .padding(.horizontal, 15)
    }
}
