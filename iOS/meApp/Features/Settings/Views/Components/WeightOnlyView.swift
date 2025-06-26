//
//  WeightOnlyView.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct WeightOnlyView: View {
    @Environment(\.appTheme) private var theme
    let lang = ScaleModesStrings.self
    
    var body: some View {
        VStack{
            VStack(alignment: .center, spacing: .spacingMD) {
                
                HStack(alignment: .center, spacing: .spacingXS) {
                    Image(AppAssets.weightOnlyMode)
                        .resizable()
                        .scaledToFit()
                        .frame(width: 18, height: 18)
                    
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

#Preview {
    WeightOnlyView()
}

