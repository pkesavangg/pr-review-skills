//
//  EmailButtonView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//
import SwiftUI

struct EmailButtonView: View {
    let email: String
    let buttonText: String

    init(email: String = AppConstants.Help.email,
         buttonText: String = AppConstants.Help.email.uppercased()) {
        self.email = email
        self.buttonText = buttonText
    }

    var body: some View {
        ButtonView(text: buttonText, type: .inlineTextPrimary, size: .large, isDisabled: false) {
            if let url = URL(string: "mailto:\(email)"),
               UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
            }
        }
    }
}

#Preview(body: {
    EmailButtonView()
})
