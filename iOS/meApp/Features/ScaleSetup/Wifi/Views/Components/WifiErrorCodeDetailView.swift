//
//  WifiErrorCodeDetailView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//

import SwiftUI

struct WifiErrorCodeDetailView: View {
    @Environment(\.appTheme) private var theme
    let errorCode: WifiErrorCode?
    
    private let lang = WifiScaleSetupStrings.ErrorDetailViewStrings.self
    private let wifiSetuplang = WifiScaleSetupStrings.self
    private let maxNumberedMessages = 3
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                contentView
            }
            .padding(.top, .spacingLG)
        }
    }
    
    private var contentView: some View {
        VStack(alignment: .leading, spacing: .spacingLG) {
            titleSection
            errorDetailSection
        }
    }
    
    private var titleSection: some View {
        Text("\(lang.troubleshooting) - \(errorCode?.rawValue ?? "other")")
            .fontOpenSans(.heading4)
            .foregroundColor(theme.textHeading)
            .multilineTextAlignment(.leading)
            .lineLimit(nil)
    }
    
    private var errorDetailSection: some View {
        VStack(alignment: .leading, spacing: .spacingMD) {
            if let code = errorCode, let detail = lang.errorDetails[code] {
                VStack(alignment: .leading, spacing: 0) {
                    noteSection(text: detail.note)
                        .padding(.bottom, .spacingXS)

                    ForEach(Array(detail.messages.enumerated()), id: \.offset) { index, message in
                        if code.shouldUseNumberedMessages && index < maxNumberedMessages {
                            textView(text: "\(index + 1). \(message)")
                        } else {
                            textView(text: message)
                        }
                    }
                }
            } else {
                textView(text: lang.other)
            }
        }
    }
    
    private func textView(text: String) -> some View {
        Text(text)
            .fontOpenSans(.body2)
            .foregroundColor(theme.textBody)
    }
    
    private func noteSection(text: String) -> some View {
        NoteBox {
            Text(text)
                .fontOpenSans(.body3)
                .foregroundColor(theme.textBody)
        }
    }
}

#Preview {
    VStack {
        WifiErrorCodeDetailView(errorCode: .t204)
        WifiErrorCodeDetailView(errorCode: nil)
    }
    .environmentObject(Theme.shared)
}
