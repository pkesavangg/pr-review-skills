//
//  NotificationName+Extensions.swift
//  meApp
//
//  Created by Lakshmi Priya on 06/06/25.
//

import Foundation

extension Notification.Name {
    static let didReceiveNotification = Notification.Name("didReceiveNotification")
    static let scaleAddedOrUpdated = Notification.Name("scaleAddedOrUpdated")
    static let goalTypeChanged = Notification.Name("goalTypeChanged")
    static let dashboardMetricsUpdated = Notification.Name("dashboardMetricsUpdated")
}
