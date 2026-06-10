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
        SignupStepWrapper(title: sexStepLang.title, subtitle: sexStepLang.subtitle) {
            HStack(spacing: .spacingLG) {
                ForEach(Sex.allCases.filter { $0 != .private }, id: \.self) { sex in
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
