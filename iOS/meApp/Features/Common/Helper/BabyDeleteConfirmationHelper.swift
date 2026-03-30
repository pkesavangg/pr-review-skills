//
//  BabyDeleteConfirmationHelper.swift
//  meApp
//

import Foundation

extension NotificationHelperServiceProtocol {
    /// Presents a confirmation alert for removing a baby, then calls the provided closure on confirm.
    func showDeleteBabyConfirmation(onDelete: @escaping () -> Void) {
        let lang = AlertStrings.RemoveBabyAlert.self
        let alert = AlertModel(
            title: lang.title,
            message: lang.message,
            buttons: [
                AlertButtonModel(title: lang.deleteButton, type: .danger) { _ in
                    onDelete()
                },
                AlertButtonModel(title: lang.cancelButton, type: .secondary) { _ in }
            ]
        )
        showAlert(alert)
    }
}
