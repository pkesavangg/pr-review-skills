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
            updateScaleNickname()
            moveToNextStep()
        case .paired:
            // Move to baby profile creation
            moveToNextStep()
        case .babyProfile:
            Task {
                await saveBabyProfile()
            }
        case .babyAdded:
            navigateToStep(.done)
        case .done:
            performExitCleanup()
        default:
            break
        }
    }

    // MARK: - Back Button

    func handleBackButtonClick() {
        switch currentStep {
        case .babyAdded:
            navigateToStep(.babyProfile)
        case .babyProfile:
            editingBaby = nil
            babyProfileForm.reset()
            if savedBabies.isEmpty {
                moveToPreviousStep()
            } else {
                navigateToStep(.babyAdded)
            }
        default:
            moveToPreviousStep()
        }
    }

    // MARK: - Exit

    func handleExit() {
        guard !isExiting else { return }
        isExiting = true

        if currentStep.rawValue >= BabyScaleSetupStep.scaleName.rawValue && isScaleSaved {
            performExitCleanup()
            return
        }

        let alert = AlertModel(
            title: "Exit Setup?",
            message: "Are you sure you want to exit scale setup?",
            buttons: [
                AlertButtonModel(title: commonLang.cancel, type: .secondary) { [weak self] _ in
                    self?.isExiting = false
                },
                AlertButtonModel(title: "Exit", type: .danger) { [weak self] _ in
                    self?.performExitCleanup()
                }
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
        case .connectingBluetooth, .connectionError:
            navigateToStep(.wakeup)
        default:
            break
        }
    }

    // MARK: - Private
    /// Saves the user-entered nickname to the locally persisted Device record.
    private func updateScaleNickname() {
        guard let scale = savedScale else { return }
        let nickname = scaleNicknameForm.nickname.value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !nickname.isEmpty else { return }
        Task {
            do {
                _ = try await scaleService.editDevice(
                    scale.id,
                    properties: ["nickname": nickname]
                )
                LoggerService.shared.log(
                    level: .info,
                    tag: tag,
                    message: "Scale nickname saved: \(nickname)"
                )
            } catch {
                LoggerService.shared.log(
                    level: .error,
                    tag: tag,
                    message: "Failed to save nickname: \(error)"
                )
            }
        }
    }
}
