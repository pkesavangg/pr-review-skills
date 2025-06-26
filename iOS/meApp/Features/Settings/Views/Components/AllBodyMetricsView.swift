//
//  AllBodyMetricsView.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct AllBodyMetricsView: View {
    @Environment(\.appTheme) private var theme
    @State private var isHeartRateOn: Bool = true
    
    let lang = ScaleModesStrings.self
    
    var body: some View {
        VStack() {
            VStack(spacing:0){
                HStack(alignment: .center, spacing: .spacingXS) {
                    
                    Image(AppAssets.heartIcon)
                        .resizable()
                        .frame(width: 20, height: 20)
                        .foregroundColor(theme.actionPrimary)
                    
                    Text(lang.heartRateLabel)
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                    
                    Text(isHeartRateOn ? lang.on.uppercased() : lang.off.uppercased())
                        .fontOpenSans(.heading5)
                        .fontWeight(.bold)
                    
                    Spacer()
                    
                    CustomToggleView(isOn: $isHeartRateOn)
                    
                }
                
                Text(lang.heartRateInfoDescription)
                    .fontOpenSans(.body3)
                    .foregroundColor(theme.textBody)
                    .padding(.top, .spacingSM)
            }
            .padding(.bottom, .spacingMD)
            
            NoteBox(title: lang.noteTitle.uppercased(), content: lang.medicalNoteDescription)
            
        }
        .background(theme.backgroundSecondary)
        
    }
}
