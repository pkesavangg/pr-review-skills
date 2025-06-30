//
//  AttributedTextView.swift
//  meApp
//
//  Created by Lakshmi Priya on 26/06/25.
//

import SwiftUI

struct AttributedTextView: View {
    let title: String
    let content: String
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        Text("**\(title)** \(content)")
            .fontOpenSans(.body3)
            .foregroundColor(theme.textBody)
            .multilineTextAlignment(.leading)
    }
}
