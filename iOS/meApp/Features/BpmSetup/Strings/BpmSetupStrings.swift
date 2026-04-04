///
///  BpmSetupStrings.swift
///  meApp
///

import Foundation

struct BpmSetupStrings {
    static let setupHeader: (String) -> String = { sku in
        let displaySku = DeviceHelper.mapSkuForDisplay(sku)
        return "Device Setup - \(displaySku)"
    }

    struct A3Permissions {
        static let title = "Permission Settings"
        static let description = "Balance Health needs access to the following permissions to connect to your monitor."
        static let authorizeBluetoothAccess = "Authorize Bluetooth access"
        static let bluetoothTurnedOff = "Bluetooth is turned off"
    }

    struct ModelSelection {
        static let title = "Select your monitor model."
        static let description = "Choose the model number printed on the bottom of your device."
    }

    struct SelectUser {
        static let title = "Which user do you want to be?"
        static let description = "Make sure to pick a user no one else is using."
    }

    struct SetUser {
        static let title: (Int) -> String = { userNumber in
            "Set the monitor to User \(userNumber)."
        }
        static let description = "Change the user by tapping the USER button."
    }

    struct ConfirmUser {
        static let title = "Press the START/STOP button to confirm user selection."
        static let description = "The monitor will confirm and turn off."
    }

    struct PrePairing {
        static let title = "Press and hold the MEM button."
        static let description = "Hold until \"PAIR\" starts flashing on the screen of the monitor."
    }

    struct Scanning {
        static let title = "Searching for monitor..."
    }

    struct Nickname {
        static let title = "What should this monitor be called?"
        static let placeholder = "nickname"
        static let defaultName = "Smart Wrist Blood Pressure Monitor"
    }

    struct Paired {
        static let title = "Your monitor is paired!"
        static let learnLink = "learn how to measure"
        static let descriptionPrefix = "You can wrap things up by tapping FINISH, or "
        static let descriptionSuffix = " with a quick tutorial."
    }

    struct MeasureSetup {
        static let title = "Let's take a measurement."
        static let description = "Put on the wrist cuff and make sure it's lined up properly. "
            + "Sit down with your feet flat on the floor and your arm angled so that the monitor is level "
            + "with your heart. Press the START button to turn on the monitor."
    }

    struct MeasureStart {
        static let title = "Relax and take a deep breath."
        static let description = "Then press the START button again to begin taking your measurement."
    }

    struct Complete {
        static let title = "Your measurement has been recorded!"
        static let description = "That's it! When you want to record your next measurement simply open the app, "
            + "put the cuff on, and press start on the monitor."
    }
}
