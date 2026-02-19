//
//  AddInfoView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import SwiftUI

struct AddInfoView: View {
    @Environment(\.appTheme) private var theme
    private let lang = AppSyncStrings.AddInfoViewStrings.self
    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            HStack {
                VStack(alignment: .leading, spacing: .spacingLG) {
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.title)
                            .fontOpenSans(.heading4)
                            .foregroundColor(theme.textHeading)
                        Text(lang.description.asAttributed(withBoldWords: lang.boldWords))
                            .foregroundColor(theme.textBody)
                    }

                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.userNumberTitle)
                            .fontOpenSans(.heading5)
                            .foregroundColor(theme.textHeading)
                        Text(lang.userNumberDescription)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.bodyCompositionTitle)
                            .fontOpenSans(.heading5)
                            .foregroundColor(theme.textHeading)
                        Text(lang.bodyCompositionDescription)
                            .fontOpenSans(.body2)
                            .foregroundColor(theme.textBody)
                    }
                    VStack(alignment: .leading, spacing: .spacingXS) {
                        Text(lang.heightAgeTitle)
                            .fontOpenSans(.heading5)
                            .foregroundColor(theme.textHeading)
                    }
                }
                Spacer()
            }
            .padding(.top, .spacingLG)
        }

    }
}

#Preview {
    AddInfoView()
}
