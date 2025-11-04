//
//  LegalLinksRow.swift
//  meApp
//
//  Created by Assistant on 28/10/25.
//

import SwiftUI

/// A reusable row that displays Terms of Service and Privacy Policy links
/// with an "and" separator. Presents links using the in-app browser.
struct LegalLinksRow: View {
    @Environment(\.appTheme) private var theme
    @State private var showTerms = false
    @State private var showPrivacy = false

    let termsLabel: String
    let andLabel: String
    let privacyLabel: String
    let termsURL: URL
    let privacyURL: URL

    var body: some View {
        HStack {
            Button {
                showTerms = true
            } label: {
                Text(termsLabel.uppercased())
                    .fontOpenSans(.link2)
                    .foregroundColor(theme.actionPrimary)
            }
            .inAppBrowser(url: termsURL, isPresented: $showTerms)

            Text(andLabel)
                .fontOpenSans(.link2)
                .foregroundColor(theme.textBody)

            Button {
                showPrivacy = true
            } label: {
                Text(privacyLabel.uppercased())
                    .fontOpenSans(.link2)
                    .foregroundColor(theme.actionPrimary)
            }
            .inAppBrowser(url: privacyURL, isPresented: $showPrivacy)
        }
    }
}

#Preview {
    LegalLinksRow(
        termsLabel: "Terms of Service",
        andLabel: "and",
        privacyLabel: "Privacy Policy",
        termsURL: AppConstants.LegalURLs.termsOfService,
        privacyURL: AppConstants.LegalURLs.privacyPolicy
    )
}


