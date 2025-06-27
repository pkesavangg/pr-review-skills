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
    func showHelpModal() {
        notificationService.showModal(ModalData(
            presentedView: AnyView(HelpModalView(onClose: {
                self.notificationService.dismissModal()
            })),
        ))
    }
}