//
//  NotificationName+Extensions.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import Foundation

extension Notification.Name {
    static let didReceiveNotification = Notification.Name("didReceiveNotification")
    static let accountWeightUnitChanged = Notification.Name("accountWeightUnitChanged")
    static let scaleAddedOrUpdated = Notification.Name("scaleAddedOrUpdated")
    static let goalTypeChanged = Notification.Name("goalTypeChanged")
    static let dashboardMetricsUpdated = Notification.Name("dashboardMetricsUpdated")
    /// Posted when the Apple Health integration sheet is presented
    static let appleHealthSheetPresented = Notification.Name("appleHealthSheetPresented")
    /// Posted when the Apple Health integration sheet is dismissed
    static let appleHealthSheetDismissed = Notification.Name("appleHealthSheetDismissed")
}
