//
//  CopyMacAddressViewModel.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/07/25.
//
import SwiftUI

@MainActor
class CopyMacAddressViewModel: ObservableObject {
    @Injector var notificationService: NotificationHelperServiceProtocol
    private let toastLang = ToastStrings.self
    
    func copyMacAddress(macAddress: String) {
        UIPasteboard.general.string = macAddress
        notificationService.showToast(ToastModel(message: toastLang.copiedToClipboard))
    }
}
