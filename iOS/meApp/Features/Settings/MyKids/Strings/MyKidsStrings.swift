//
//  MyKidsStrings.swift
//  meApp
//

import Foundation

struct MyKidsStrings {
    static let title = "My Kids"
    static let subtitle = "Add and manage baby profiles for use with the baby scale."
    static let addABaby = "ADD A BABY"
    static let addBaby = "Add Baby"
    static let save = "SAVE"
    static let saveFailed = "Unable to save baby profile. Please try again."

    /// Field labels + fallbacks for the expandable baby row's expanded detail section (MOB-1605).
    struct Details {
        static let birthday = "Birthday"
        static let biologicalSex = "Biological sex"
        static let birthLength = "Birth length"
        static let birthWeight = "Birth weight"
        /// Shown when a profile field has no recorded value.
        static let empty = "--"
        /// VoiceOver labels for the expand/collapse chevron.
        static let expandAccessibilityLabel = "Show baby details"
        static let collapseAccessibilityLabel = "Hide baby details"
    }

    struct RemoveBaby {
        static let title = "Remove Baby"
        static let message = "Are you sure you want to remove this baby? You will not be able to get it back."
        static let delete = "DELETE"
        static let cancel = "CANCEL"
    }
}
