//
//  DeviceInfoRequest.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 26/07/25.
//

struct DeviceInfoRequest: Codable {
    let appVersion: String
    let deviceManufacturer: String
    let deviceOSName: String
    let deviceOSVersion: String
    let deviceUUID: String
    let deviceModel: String
    let fcmToken: String
}
