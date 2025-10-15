//
//  WeightOnlyContentView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 16/07/25.
//
import SwiftUI

/// Extracted content view for Weight Only
struct WeightOnlyContentView: View {
    @Environment(\.appTheme) private var theme
    private let lang = ScaleModesStrings.self
    
    var body: some View {
        VStack {
            VStack(alignment: .center, spacing: .spacingMD) {
                HStack(alignment: .center, spacing: .spacingXS) {
                    Image(AppAssets.weightOnlyMode)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 18, height: 18)
                        .themeDropShadow()
                    
                    Text(lang.weightOnlyIndicatorLabel)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textBody)
                }
                
                Image(AppAssets.scaleWeightOnlyMode)
                    .resizable()
                    .scaledToFit()
                    .frame(width: 179, height: 169)
                    .padding(.bottom, .spacingMD)
            }
            
            NoteBox {
                AttributedTextView(title: lang.noteTitle.uppercased(), content: lang.WeightOnlyNoteDescription)
            }
        }
        .background(theme.backgroundSecondary)
    }
}
