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
    var onAccountSwitchingSignupSuccess: (() -> Void)?
    private var stepViews: [AnyView] {
        signupStore.steps.map { step in
            switch step {
            case .name:
                AnyView(NameStepView(signupStore: signupStore))
            case .dateOfBirth:
                AnyView(DateOfBirthStepView(signupStore: signupStore))
            case .pickDevice:
                AnyView(PickDeviceStepView(signupStore: signupStore))
            case .addBaby:
                AnyView(BabyProfileFormView(
                    form: signupStore.babyProfileForm,
                    showDatePicker: $signupStore.showBabyDatePicker,
                    showSexPicker: $signupStore.showBabySexPicker,
                    headerTitle: SignupStrings.AddBabyStep.title,
                    headerSubtitle: SignupStrings.AddBabyStep.subtitle
                ))
            case .babyList:
                AnyView(BabyListStepView(
                    title: SignupStrings.BabyListStep.title,
                    addButtonText: SignupStrings.BabyListStep.addBabyButton,
                    babies: signupStore.babies.map {
                        BabyListItem(id: $0.id, accountID: $0.id.uuidString, name: $0.name)
                    },
                    onTapBaby: { signupStore.editBaby(at: $0) },
                    onEditBaby: { signupStore.editBaby(at: $0) },
                    onDeleteBaby: { signupStore.confirmDeleteBaby(at: $0) },
                    onAddBaby: { signupStore.addAnotherBaby() }
                ))
            case .sex:
                AnyView(SexStepView(signupStore: signupStore))
            case .height:
                AnyView(HeightStepView(signupStore: signupStore))
            case .goal:
                AnyView(GoalStepView(signupStore: signupStore))
            case .email:
                AnyView(EmailStepView(signupStore: signupStore))
            case .password:
                AnyView(PasswordStepView(signupStore: signupStore))
            case .profileReady:
                AnyView(ProfileReadyStepView(title: signupStore.profileReadyTitle))
            case .allProfilesReady:
                AnyView(SignupSuccessStepView(
                    deviceTypes: signupStore.deviceStatuses.map(\.device)
                ))
            case .signupError:
                AnyView(SignupErrorStepView(deviceStatuses: signupStore.deviceStatuses))
            }
        }
    }
    
    @ViewBuilder
    private func navbarLeadingContent() -> some View {
        if signupStore.currentStep != .allProfilesReady {
            AppIconView(icon: AppAssets.xmarkSmall, size: IconSize(width: 24, height: 24))
                .foregroundColor(theme.statusIconPrimary)
        }
    }

    @ViewBuilder
    private func navbarTrailingContent() -> some View {
        if signupStore.currentStep != .allProfilesReady && signupStore.currentStep != .signupError {
            Button {
                signupStore.showHelpModal()
            } label: {
                AppIconView(icon: AppAssets.helpCircle, size: IconSize(width: 24, height: 24))
                    .foregroundColor(theme.statusIconPrimary)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            NavbarHeaderView(
                title: isFromAccountSwitching ? commonLang.signUp.capitalized : "",
                leadingContent: navbarLeadingContent,
                trailingContent: navbarTrailingContent,
                onLeadingTap: {
                    if signupStore.currentStep != .allProfilesReady {
                        signupStore.handleExit(router: isFromAccountSwitching ? nil : router)
                    }
                },
                onTrailingTap: {},
                canShowBorder: isFromAccountSwitching,
                canShowPresentationIndicator: isFromAccountSwitching,
                shouldShowBackground: false
            )
            
            if signupStore.currentStep != .allProfilesReady && signupStore.currentStep != .signupError {
                ProgressBarView(progress: signupStore.progressValue)
                    .padding([.horizontal, .top], .spacingSM)
            }
            
            SwiperView(
                selectedIndex: $signupStore.currentStepIndex,
                views: stepViews
            )
            .padding(.top, .spacing2XL)
            // Footer Buttons
            footerButtons
            
        }
        .onAppear {
            signupStore.isFromAccountSwitching = isFromAccountSwitching
            if isFromAccountSwitching {
                signupStore.dismissAction = dismiss
                signupStore.onSignupSuccess = onAccountSwitchingSignupSuccess ?? {
                    dismiss()
                }
            } else {
                // Successful auth promotes the root ContentView from landing to dashboard.
                // Avoid relying on auth-stack pops so all signup success paths land on dashboard.
                signupStore.onSignupSuccess = {}
            }
        }
        .navigationBarBackButtonHidden(true)
        .background(theme.backgroundSecondary)
        
    }
    
    private var footerButtons: some View {
        Group {
            if signupStore.currentStep == .profileReady {
                VStack(spacing: .spacingXS) {
                    ButtonView(
                        text: SignupStrings.ProfileReadyStep.finishButton,
                        type: .filledPrimary,
                        size: .large,
                        isDisabled: false
                    ) {
                        signupStore.finishSignup()
                    }
                    .padding(.horizontal, .spacingSM)

                    ButtonView(
                        text: SignupStrings.ProfileReadyStep.connectAnotherDevice,
                        type: .textPrimary,
                        size: .small,
                        isDisabled: !signupStore.canConnectAnotherDevice
                    ) {
                        withAnimation {
                            signupStore.connectAnotherDevice()
                        }
                    }
                }
                .padding(.vertical, .spacingSM)

            } else if signupStore.currentStep == .allProfilesReady {
                ButtonView(
                    text: SignupStrings.AllProfilesReadyStep.doneButton,
                    type: .filledPrimary,
                    size: .large,
                    isDisabled: false
                ) {
                    signupStore.completeSignup()
                }
                .padding(.horizontal, .spacingSM)
                .padding(.vertical, .spacingSM)

            } else if signupStore.currentStep == .signupError {
                HStack {
                    ButtonView(
                        text: SignupStrings.SignupErrorStep.cancelButton,
                        type: .textPrimary,
                        size: .small,
                        isDisabled: false,
                        padding: true
                    ) {
                        signupStore.cancelSignup(router: isFromAccountSwitching ? nil : router)
                    }
                    Spacer()
                    ButtonView(
                        text: SignupStrings.SignupErrorStep.tryAgainButton,
                        type: .filledPrimary,
                        size: .small,
                        isDisabled: false,
                        customHorizontalPadding: .spacingXS / 2,
                        customVerticalPadding: .spacingXS / 4
                    ) {
                        signupStore.retryFailedDevices()
                    }
                }
                .padding(.vertical, .spacingSM)
                .padding(.trailing, .spacingSM)

            } else {
                HStack {
                    ButtonView(text: commonLang.back,
                               type: .textPrimary,
                               size: .small,
                               isDisabled: signupStore.currentStep == SignupStep.name,
                               padding: true) {
                        withAnimation {
                            hideKeyboard()
                            signupStore.moveToPreviousStep()
                        }
                    }

                    Spacer()

                    ButtonView(text: signupStore.currentStep == SignupStep.password ? commonLang.complete : commonLang.next,
                               type: .filledPrimary,
                               size: .small,
                               isDisabled: !signupStore.isNextEnabled,
                               customHorizontalPadding: signupStore.currentStep == SignupStep.password ? .spacingXS : .spacingXS / 2,
                               customVerticalPadding: .spacingXS / 4) {
                        hideKeyboard()
                        if signupStore.currentStep == .password {
                            withAnimation {
                                signupStore.createAccount()
                            }
                        } else {
                            withAnimation {
                                signupStore.moveToNextStep()
                            }
                        }
                    }
                }
                .overlay {
                    HStack {
                        Spacer()
                        if signupStore.currentStep == .goal || signupStore.currentStep == .addBaby {
                            ButtonView(text: commonLang.skip, type: .textTertiary, size: .small, isDisabled: false) {
                                withAnimation {
                                    hideKeyboard()
                                    signupStore.handleSkip()
                                }
                            }
                        }
                        Spacer()
                    }
                }
                .padding(.vertical, .spacingSM)
                .padding(.trailing, .spacingSM)
            }
        }
    }
}

#Preview {
    SignupScreen()
}
