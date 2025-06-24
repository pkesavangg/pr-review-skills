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
    @EnvironmentObject var router: Router<AuthRoute>
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
            AnyView(
                GoalStepView(signupStore: signupStore)
            ),
            AnyView(
                EmailStepView(signupStore: signupStore)
            ),
            AnyView(
                PasswordStepView(signupStore: signupStore)
            )
        ]
    }
    
    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                leadingContent: {
                    AppIconView(icon: AppAssets.xmark, size: IconSize(width: 25, height: 22))
                        .foregroundColor(theme.statusIconPrimary)
                },
                trailingContent: {
                    Button {
                        signupStore.showHelpModal()
                    } label: {
                        AppIconView(icon: AppAssets.helpCircle)
                            .foregroundColor(theme.statusIconPrimary)
                    }
                },
                onLeadingTap: {
                    signupStore.handleExit(router: router)
                },
                onTrailingTap: {}
            )
            
            ProgressBarView(progress: signupStore.progressValue)
                .padding(.top, .spacingMD)
                .padding(.horizontal, .spacingSM)
            
            SwiperView(
                selectedIndex: $signupStore.currentStepIndex,
                views: stepViews
            )
            .padding(.top, .spacing2XL)
            // Footer Buttons
            footerButtons
                .padding(.spacingSM)
            
        }
        .onAppear {
            signupStore.onSignupSuccess = { router.navigateBack() }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: commonLang.back,
                       type: .inlineTextPrimary,
                       size: .small,
                       isDisabled: signupStore.currentStep == SignupStep.name,
                       action: {
                withAnimation {
                    hideKeyboard()
                    signupStore.moveToPreviousStep()
                }
            })
            
            Spacer()
            
            if signupStore.currentStep == SignupStep.goal {
                ButtonView(text: commonLang.skip, type: .textPrimary, size: .small, isDisabled: false, action: {
                    withAnimation {
                        hideKeyboard()
                        signupStore.handleSkip()
                    }
                })
                .padding(.trailing, .spacingSM)
            }
            
            ButtonView(text: signupStore.currentStep == SignupStep.password ? commonLang.complete : commonLang.next,
                       type: .filledPrimary,
                       size: .small,
                       isDisabled: !signupStore.isNextEnabled,
                       action: {
                withAnimation {
                    hideKeyboard()
                    signupStore.moveToNextStep()
                }
            })
        }
    }
}

#Preview {
    SignupScreen()
}
