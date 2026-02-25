//
//  ErrorCodeSelectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 22/07/25.
//

import SwiftUI

struct ErrorCodeSelectionView: View {
    @Environment(\.appTheme) private var theme
    @EnvironmentObject var themeManager: Theme
    let sku: String
    private let lang = WifiScaleSetupStrings.ErrorCodeSelectionViewStrings.self
    private let wifiSetuplang = WifiScaleSetupStrings.self
    @State var selectedError: WifiErrorCode?
    
    /// Callback triggered when an error code is tapped.
    var onErrorSelected: ((WifiErrorCode?) -> Void)?
    var onClickButton: (() -> Void)?
    
    /// All available error codes in a grid format
    private let errorCodes: [[WifiErrorCode]] = [
        [.t163, .t164, .t165],
        [.t204, .t205, .t206],
        [.t315, .t323, .t325]
    ]
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(spacing: .spacingLG) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(lang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                        .multilineTextAlignment(.leading)
                        .lineLimit(nil)
                    
                    Text(lang.description.asAttributed(withBoldWords: lang.boldWords))
                        .foregroundColor(theme.textBody)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, .spacingSM)
                
                ErrorCodeButtonGrid(
                    errorCodes: errorCodes,
                    selectedError: $selectedError,
                    onErrorSelected: onErrorSelected,
                    sku: sku
                )
                
                ButtonView(text: wifiSetuplang.seeSomethingElse, type: .inlineTextPrimary, size: .large, isDisabled: false) {
                    selectedError = nil
                    onErrorSelected?(nil)
                    onClickButton?()
                }
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ErrorCodeSelectionView(
        sku: "0384"
    ) { _ in }
    .environmentObject(Theme.shared)
}
