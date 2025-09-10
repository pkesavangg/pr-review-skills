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
    @Environment(\.dismiss) var dismiss
    @EnvironmentObject var router: Router<AuthRoute>
    var commonLang = CommonStrings.self
    var isFromAccountSwitching: Bool = false
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
                title: isFromAccountSwitching ? commonLang.signUp.capitalized : "",
                leadingContent: {
                    AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
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
                    signupStore.handleExit(router: isFromAccountSwitching ? nil : router)
                },
                onTrailingTap: {},
                canShowPresentationIndicator: isFromAccountSwitching,
                shouldShowBackground: false
            )
            
            ProgressBarView(progress: signupStore.progressValue)
                .padding([.horizontal, .top], .spacingSM)
            
            SwiperView(
                selectedIndex: $signupStore.currentStepIndex,
                views: stepViews
            )
            .padding(.top, .spacing2XL)
            // Footer Buttons
            footerButtons
                .padding(.vertical, .spacingSM)
                .padding(.trailing, .spacingSM)
            
        }
        .onAppear {
            signupStore.isFromAccountSwitching = isFromAccountSwitching
            if isFromAccountSwitching {
                signupStore.dismissAction = dismiss
            } else {
                signupStore.onSignupSuccess = { router.navigateBack() }
            }
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
        .overlay {
            HStack {
                Spacer()
                if signupStore.currentStep == SignupStep.goal {
                    ButtonView(text: commonLang.skip, type: .textTertiary, size: .small, isDisabled: false, action: {
                        withAnimation {
                            hideKeyboard()
                            signupStore.handleSkip()
                        }
                    })
                }
                Spacer()
            }
        }
    }
}

#Preview {
    SignupScreen()
}
