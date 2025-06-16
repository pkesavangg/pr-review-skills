//
//  SexStepView.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//
import SwiftUI

// MARK: SexStepView
/// This view is responsible for the sex selection step of the signup process.
struct SexStepView: View {
    @ObservedObject var signupStore: SignupStore
    @Environment(\.appTheme) private var theme
    let sexStepLang = SignupStrings.SexStep.self

    var body: some View {
        ScrollView(.vertical, showsIndicators: false) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: .spacingXS) {
                    Text(sexStepLang.title)
                        .fontOpenSans(.heading4)
                        .foregroundColor(theme.textHeading)
                    
                    Text(sexStepLang.subtitle)
                        .fontOpenSans(.body2)
                        .foregroundColor(theme.textHeading)
                }

                HStack(spacing: .spacingLG) {
                    ForEach(Sex.allCases, id: \.self) { sex in
                        SelectableCircleButton(
                            label: sex.rawValue.uppercased(),
                            isSelected: signupStore.signupForm.gender.value == sex.rawValue
                        ) {
                            signupStore.signupForm.gender.value = sex.rawValue
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .padding(.top, .spacingLG)
            }
        }
    }
}
