//
//  DeviceInfoHelper.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 29/06/25.
//

import Foundation
import UIKit

func getDeviceId() -> String {
    return UIDevice.current.identifierForVendor?.uuidString ?? ""
}
