//
//  HKIntegrationHealthAccessViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/06/25.
//


import SwiftUI

@MainActor
class HKIntegrationHealthAccessViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperService
    let alertLang = AlertStrings.HKExitAlert.self
    
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(onClose: {
                self.notificationService.dismissModal()
            })),
        ))
    }
    
    func showExitAlert(state: AppleHealthIntegrationState, dismiss: (() -> Void)?) {
        
        if state == .integrationFailed || state == .userConflict || state == .integrationComplete  {
            dismiss?()
            return
        }
        
        let alert = AlertModel(
            title: alertLang.title,
            message: alertLang.message,
            buttons: [
                AlertButtonModel(title: alertLang.exitButton, type: .primary) { _ in
                    dismiss?()
                },
                AlertButtonModel(title: alertLang.cancelButton, type: .secondary) { _ in }
            ]
        )
        notificationService.showAlert(alert)
    }
}
