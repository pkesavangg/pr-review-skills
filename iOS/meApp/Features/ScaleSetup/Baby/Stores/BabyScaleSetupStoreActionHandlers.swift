//
//  BabyScaleSetupStoreActionHandlers.swift
//  meApp
//

import Foundation

@MainActor
extension BabyScaleSetupStore {

    // MARK: - Next Button

    func handleNextButtonClick() {
        switch currentStep {
        case .intro:
            moveToNextStep()
        case .permissions:
            moveToNextStep()
        case .scaleName:
            // TODO: Re-enable when API is ready
            // updateScaleNickname()
            moveToNextStep()
        case .paired:
            // Move to baby profile creation
            moveToNextStep()
        case .babyProfile:
            Task {
                await saveBabyProfile()
            }
        case .babyAdded:
            handleFinish()
        default:
            break
        }
    }

    // MARK: - Back Button

    func handleBackButtonClick() {
        switch currentStep {
        case .paired:
            moveToPreviousStep()
        case .babyProfile:
            moveToPreviousStep()
        default:
            moveToPreviousStep()
        }
    }

    // MARK: - Exit

    func handleExit() {
        guard !isExiting else { return }
        isExiting = true

        // TODO: Re-enable scale-saved check when API is ready
        // if currentStep.rawValue >= BabyScaleSetupStep.scaleName.rawValue && isScaleSaved {
        //     performExitCleanup()
        //     return
        // }

        // For UI-only mode, just confirm and exit
        let alert = AlertModel(
            title: "Exit Setup?",
            message: "Are you sure you want to exit scale setup?",
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary, action: { [weak self] _ in
                    self?.isExiting = false
                }),
                AlertButtonModel(title: "Exit", type: .danger, action: { [weak self] _ in
                    self?.performExitCleanup()
                })
            ]
        )
        notificationService.showAlert(alert)
    }

    /// Tab-deactivation handler (returns true if setup can be left).
    func confirmExit() async -> Bool {
        handleExit()
        return false
    }

    // MARK: - Try Again

    func tryAgainButtonHandler() {
        scaleSetupError = .none
        connectionState = .loading

        switch currentStep {
        case .connectingBluetooth:
            navigateToStep(.wakeup)
        default:
            break
        }
    }

    // MARK: - Private

    // TODO: Re-enable when API is ready
    /*
    private func updateScaleNickname() {
        guard let scale = savedScale else { return }
        let nickname = scaleNicknameForm.nickname.value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !nickname.isEmpty else { return }
        scale.nickname = nickname
        LoggerService.shared.log(level: .info, tag: tag, message: "Scale nickname updated to: \(nickname)")
    }
    */
}
