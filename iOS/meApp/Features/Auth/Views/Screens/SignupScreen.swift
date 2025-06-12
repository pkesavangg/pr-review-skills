//
//  SignupScreen.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 11/06/25.
//

import SwiftUI

struct SignupScreen: View {
    @StateObject var signupStore = SignupStore()
    @Environment(\.appTheme) private var theme
    var commonLang = CommonStrings.self
    
    private var stepViews: [AnyView] {
        [
            AnyView(
                NameStepView(signupStore: signupStore)
            ),
            
            AnyView(
                DateOfBirthStepView(signupStore: signupStore)
            ),
            AnyView(
                SexStepView(signupStore: signupStore)
            ),
            AnyView(
                HeightStepView(signupStore: signupStore)
            ),
            
            // TODO: These are for the testing purpose need to replace with the actual views
            AnyView(
                GoalStepView()
            ),
            
            AnyView(
                EmailStepView(email: $signupStore.signupForm.email.value)
                    .onChange(of: signupStore.signupForm.email.value) {
                        signupStore.updateNextButtonState()
                    }
            ),
            AnyView(
                PasswordStepView(password: $signupStore.signupForm.password.value)
                    .onChange(of: signupStore.signupForm.password.value) {
                        signupStore.updateNextButtonState()
                    }
            )
        ]
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // TODO: Need to replace with the PageHeaderView from the common components
            PageHeaderView(
                leadingButtonView: AnyView(
                    Button(action: {
                        signupStore.showExitAlert()
                    }) {
                        AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                            .foregroundColor(theme.statusIconPrimary)
                    }
                ),
                trailingButtonView: AnyView(
                    Button(action: {
                        signupStore.showExitAlert()
                    }) {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                ),
                onLeadingButtonTap: nil,
                onTrailingButtonTap: nil
            )
            .padding(.horizontal, .spacingSM)
            
            AppProgressView(progressValue: signupStore.progressValue)
                .padding(.top, .spacingMD)
                .padding(.horizontal, .spacingSM)
            
            SwiperView(
                selectedIndex: $signupStore.currentStepIndex,
                views: stepViews
            )
            .padding(.top, .spacingLG)
            // Footer Buttons
            footerButtons
                .padding(.horizontal, .spacingSM)
            
        }
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        // TODO: Need to replace with the button component from the common components
        HStack {
            Button(commonLang.back) {
                withAnimation {
                    hideKeyboard()
                    signupStore.moveToPreviousStep()
                }
            }
            .foregroundColor(.blue)
            
            Spacer()
            if signupStore.currentStep == SignupStep.goal {
                HStack {
                    Button(commonLang.skip) {
                        withAnimation {
                            hideKeyboard()
                            signupStore.moveToNextStep()
                        }
                    }
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 12)
                    .background(signupStore.isNextEnabled ? Color.blue : Color.gray)
                    .cornerRadius(8)
                    .disabled(!signupStore.isNextEnabled)
                }
            }
            
            Button(signupStore.currentStep == SignupStep.password ? commonLang.done : commonLang.next) {
                withAnimation {
                    hideKeyboard()
                    signupStore.moveToNextStep()
                }
            }
            .foregroundColor(.white)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(signupStore.isNextEnabled ? Color.blue : Color.gray)
            .cornerRadius(8)
            .disabled(!signupStore.isNextEnabled)
        }
    }
}

// TODO: Need to replace with the PageHeaderview from the common components
import SwiftUI

struct PageHeaderView: View {
    @Environment(\.appTheme) private var theme
    let leadingButtonView: AnyView
    var trailingButtonView: AnyView?
    let onLeadingButtonTap: (() -> Void)?
    let onTrailingButtonTap: (() -> Void)?
    let title: String? = nil
    
    var body: some View {
        HStack {
            leadingButtonView
            Spacer()
            if let title = title {
                Text(title)
                    .fontOpenSans(.heading5)
                    .foregroundColor(theme.textHeading)
                    .padding(.trailing, trailingButtonView != nil ? 0 : 24)
            }
            Spacer()
            
            if let view = trailingButtonView {
                view
            }
        }
    }
}

#Preview {
    SignupScreen()
}
