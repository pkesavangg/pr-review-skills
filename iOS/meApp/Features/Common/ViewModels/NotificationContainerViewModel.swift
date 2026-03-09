//
//  NotificationContainerViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 05/06/25.
//

import Combine
import Foundation
import SwiftUI

@MainActor
class NotificationContainerViewModel: ObservableObject {
    @Injector var notificationHelperService: NotificationHelperService

    @Published var alertData: AlertModel?
    @Published var toastData: ToastModel?
    @Published var loaderData: LoaderModel?
    @Published var modalViewData: [ModalData] = []

    var cancellables = Set<AnyCancellable>()

    init() {
        notificationHelperService.$alertData
            .sink { alertData in
                self.alertData = alertData
            }
            .store(in: &cancellables)

        notificationHelperService.$toastData.sink { toastData in
            self.toastData = toastData
        }
        .store(in: &cancellables)

        notificationHelperService.$loaderData.sink { loaderData in
            self.loaderData = loaderData
        }
        .store(in: &cancellables)

        notificationHelperService.$modalViewData.sink { modalViewData in
            self.modalViewData = modalViewData
        }
        .store(in: &cancellables)
    }
}
