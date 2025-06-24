//
//  CallButtonView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//
import SwiftUI

struct CallButtonView: View {
    let phoneNumber: String
    let buttonText: String

    init(phoneNumber: String = AppConstants.Help.phoneNumber,
         buttonText: String = AppConstants.Help.phoneNumber) {
        self.phoneNumber = phoneNumber
        self.buttonText = buttonText
    }

    var body: some View {
        ButtonView(text: buttonText, type: .inlineTextPrimary, size: .large, isDisabled: false) {
            let cleanedNumber = phoneNumber.replacingOccurrences(of: "-", with: "")
            if let url = URL(string: "tel://\(cleanedNumber)"),
               UIApplication.shared.canOpenURL(url) {
                UIApplication.shared.open(url)
            }
        }
    }
}

#Preview(body: {
    EmailButtonView()
})

