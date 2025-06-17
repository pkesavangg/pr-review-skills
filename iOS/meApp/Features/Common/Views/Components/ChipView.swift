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
                        if style == .bordered {
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
        ChipView(text: "Bordered Chip") // Uses .normal style
        
        ChipView(text: "Bordered Chip", style: .bordered) {
            print("Tapped!")
        }
    }
    .background(.gray)
}
