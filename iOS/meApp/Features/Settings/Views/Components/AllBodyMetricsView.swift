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
    let commonLang = CommonStrings.self
    
    var body: some View {

        let iconAndLabelColor = isHeartRateOn ? theme.statusIconPrimary : theme.statusIconSecondary
        
        VStack() {
            VStack(spacing:0){
                HStack(alignment: .center, spacing: .spacingXS) {
                    
                    StatusRowView(
                        iconName: AppAssets.heartIcon,
                        label: commonLang.heartRateLabel,
                        statusText: isHeartRateOn ? commonLang.on.uppercased() : commonLang.off.uppercased(),
                        foregroundColor: iconAndLabelColor
                    )
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
            
            NoteBox {
                AttributedTextView(title: lang.noteTitle.uppercased(), content: lang.medicalNoteDescription)
            }
            
        }
        .background(theme.backgroundSecondary)
        
    }
}
