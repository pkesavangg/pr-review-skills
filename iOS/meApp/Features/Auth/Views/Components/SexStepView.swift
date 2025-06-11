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
    var sexStepLang = SignupStrings.SexStep.self

    var body: some View {
        ScrollView(.vertical) {
            VStack(alignment: .leading) {
                Text(sexStepLang.title)
                    .fontOpenSans(.heading4)
                    .foregroundColor(theme.textHeading)
                    .padding(.top, .spacingXL)
                
                Text(sexStepLang.subtitle)
                    .fontOpenSans(.body2)
                    .foregroundColor(theme.textHeading)
                    .padding(.top, .spacingXS)

                HStack(spacing: 32) {
                    
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
