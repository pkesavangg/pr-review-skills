//
//  Devices.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 23/06/25.
//

/// Devices data for the scale list in the help screen
let SCALES: [DeviceItemInfo] = [
    // AppSync series
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0340", imgPath: AppAssets.scale0341, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0341", imgPath: AppAssets.scale0341, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Bathroom Scale", sku: "0342", imgPath: AppAssets.scale0342, setupType: .appSync, bodyComp: false),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0343", imgPath: AppAssets.scale0343, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0345", imgPath: AppAssets.scale0345, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0346", imgPath: AppAssets.scale0346, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0347", imgPath: AppAssets.scale0347, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "Basic AppSync Bathroom Scale", sku: "0358", imgPath: AppAssets.scale0358, setupType: .appSync, bodyComp: false),
    DeviceItemInfo(productName: "Basic AppSync Bathroom Scale", sku: "0359", imgPath: AppAssets.scale0359, setupType: .appSync, bodyComp: false),
    DeviceItemInfo(productName: "AppSync Bathroom Scale", sku: "0364", imgPath: AppAssets.scale0364, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0369", imgPath: AppAssets.scale0369, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Body Fat Scale", sku: "0370", imgPath: AppAssets.scale0370, setupType: .appSync, bodyComp: true),
    DeviceItemInfo(productName: "AppSync Bathroom Scale", sku: "0371", imgPath: AppAssets.scale0371, setupType: .appSync, bodyComp: false),

    // Bluetooth series
    DeviceItemInfo(productName: "Bluetooth Smart Scale", sku: "0375", imgPath: AppAssets.scale0375, setupType: .bluetooth, bodyComp: false),
    DeviceItemInfo(productName: "Bluetooth Smart Scale", sku: "0376", imgPath: AppAssets.scale0376, setupType: .bluetooth, bodyComp: false),
    DeviceItemInfo(productName: "Bluetooth Smart Scale", sku: "0378", imgPath: AppAssets.scale0378, setupType: .lcbt, bodyComp: true),
    DeviceItemInfo(productName: "Bluetooth Smart Scale", sku: "0380", imgPath: AppAssets.scale0380, setupType: .bluetooth, bodyComp: false),
    DeviceItemInfo(productName: "Bluetooth Smart Scale", sku: "0382", imgPath: AppAssets.scale0382, setupType: .bluetooth, bodyComp: true),
    DeviceItemInfo(productName: "Bluetooth Scale", sku: "0383", imgPath: AppAssets.scale0383, setupType: .lcbt, bodyComp: true),

    // WiFi series
    DeviceItemInfo(productName: "Wi-Fi Smart Scale", sku: "0384", imgPath: AppAssets.scale0384, setupType: .espTouchWifi, bodyComp: true),
    DeviceItemInfo(productName: "Wi-Fi Smart Scale", sku: "0385", imgPath: AppAssets.scale0385, setupType: .wifi, bodyComp: true),
    DeviceItemInfo(productName: "Wi-Fi Smart Scale", sku: "0396", imgPath: AppAssets.scale0396And0397, setupType: .wifi, bodyComp: false),
    DeviceItemInfo(productName: "Wi-Fi Smart Scale", sku: "0397", imgPath: AppAssets.scale0396And0397, setupType: .espTouchWifi, bodyComp: false),

    // BtWiFi series
    DeviceItemInfo(productName: "AccuCheck Verve Smart Scale", sku: "0412", imgPath: AppAssets.scale0412, setupType: .btWifiR4, bodyComp: true),

    // Baby scale series
    DeviceItemInfo(productName: "Smart Baby Scale", sku: "0220", imgPath: AppAssets.scale0220, setupType: .babyScale, bodyComp: false),
    DeviceItemInfo(productName: "Smart Baby Scale", sku: "0222", imgPath: AppAssets.scale0222, setupType: .babyScale, bodyComp: false)
]
