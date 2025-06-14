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
                    AppIconView(icon: AppAssets.helpCircle)
                        .foregroundColor(theme.statusIconPrimary)
                },
                onLeadingTap: {
                    signupStore.showExitAlert()
                },
                onTrailingTap: {
                    signupStore.showHelpModal()
                }
            )
            .padding(.horizontal, .spacingSM)
            
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
                .padding(.horizontal, .spacingSM)
                .padding(.bottom, .spacingSM)
            
        }
        .background(theme.backgroundSecondary)
    }
    
    private var footerButtons: some View {
        HStack {
            ButtonView(text: commonLang.back,
                       type: .linkBlueInline,
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
                ButtonView(text: commonLang.skip, type: .linkBlueDefault, size: .small, isDisabled: false, action: {
                    withAnimation {
                        hideKeyboard()
                        signupStore.handleSkip()
                    }
                })
                .padding(.trailing, .spacingSM)
            }
            
            ButtonView(text: signupStore.currentStep == SignupStep.password ? commonLang.complete : commonLang.next,
                       type: .primary,
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
