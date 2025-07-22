//
//  ErrorCodeSelectionView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 22/07/25.
//

import SwiftUI

struct ErrorCodeSelectionView: View {
    @Environment(\.appTheme) private var theme
    private let lang = WifiScaleSetupStrings.ErrorCodeSelectionViewStrings.self
    @State var selectedError: String? = nil
    
    /// Callback triggered when an error code is tapped.
    var onErrorSelected: ((String) -> Void)? = nil
    
    /// All available error codes in a grid format
    private let errorCodes = [
        ["t163", "t164", "t165"],
        ["t204", "t205", "t206"],
        ["t315", "t323", "t325"]
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
                    onErrorSelected: onErrorSelected
                )
            }
            .padding(.top, .spacingLG)
        }
    }
}

#Preview {
    ErrorCodeSelectionView() { errorCode in
        print("Selected error: \(errorCode)")
    }
    .environmentObject(Theme.shared)
}
