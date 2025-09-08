//
//  AppIconView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 09/06/25.
//

import SwiftUI
struct ChipView: View {
    @Environment(\.appTheme) private var theme
    let text: String
    var style: ChipStyle = .normal
    var isSelected: Bool = false
    var onTap: (() -> Void)? = nil
    
    var body: some View {
        Button(action: {
            onTap?()
        }) {
            Text(text)
                .fontWeight(.bold)
                .fontOpenSans(.link1)
                .foregroundColor(theme.actionPrimary)
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingXS)
                .background(
                    RoundedRectangle(cornerRadius: .radiusXS)
                        .fill(theme.backgroundPrimary)
                )
                .overlay(
                    Group {
                        if style == .bordered && isSelected {
                            RoundedRectangle(cornerRadius: .radiusXS)
                                .stroke(theme.actionPrimary, lineWidth: 1.5)
                        }
                    }
                )
        }
    }
}

#Preview {
    VStack(spacing: 20) {
        ChipView(text: "Normal Chip") // Uses .normal style
        
        ChipView(text: "Bordered Chip (Always)", style: .bordered) {
            print("Tapped!")
        }
        
        ChipView(text: "Selected Chip", style: .bordered, isSelected: true) {
            print("Tapped!")
        }
        
        ChipView(text: "Unselected Chip", style: .bordered, isSelected: false) {
            print("Tapped!")
        }
    }
    .background(.gray)
}
