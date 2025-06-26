//
//  NoteBox.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct NoteBox: View {
    let title: String
    let content: String
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        Text("**\(title)** \(content)")
            .fontOpenSans(.body3)
            .foregroundColor(theme.textBody)
            .multilineTextAlignment(.leading)
            .padding(.spacingSM)
            .frame(maxWidth: .infinity, alignment: .leading) 
            .background(theme.backgroundPrimary)
            .cornerRadius(.radiusSM)
    }
}

#Preview(){
    VStack{
        NoteBox(title: "NOTE:", content: "Other scale users can temporarily enable All Body Metrics for one session via their app.")
        NoteBox(title: "NOTE:", content: "If you have certain medical conditions —like implanted medical devices or you are pregnant — you should not use All Body Metrics Mode without first consulting your doctor.")
    }
    .padding(.spacingSM)
}
