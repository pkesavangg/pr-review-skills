//
//  InlineButtonText.swift
//  meApp
//
//  Created by Lakshmi Priya on 25/06/25.
//

import SwiftUI

struct InlineButtonText: View {
    let prefix: String
    let linkText: String
    let suffix: String
    let action: () -> Void
    @Environment(\.appTheme) private var theme
    
    var body: some View {
        let attributed = createAttributedString()
        Text(attributed)
            .fontOpenSans(.body2)
            .foregroundColor(theme.textBody)
            .onTapGesture {
                action()
            }
    }
    
    private func createAttributedString() -> AttributedString {
        var attributed = AttributedString(prefix + linkText + suffix)
        if let range = attributed.range(of: linkText) {
            attributed[range].foregroundColor = theme.actionPrimary
            attributed[range].underlineStyle = .single
            attributed[range].font = .custom("OpenSans-Regular", size: CustomTextStyle.body2.size)
        }
        return attributed
    }
}
