//
//  AppSyncStrings.swift
//  meApp
//
//  Created by Kesavan Panchabakesan on 02/07/25.
//

import Foundation

struct AppSyncStrings {
    /// Text constants used in `ActivateYourScaleView`.
    struct ActivateYourScaleViewStrings {
        static let title = "Activate your scale."
        static let description = "Wake up your scale by tapping it with your foot. Then press and hold the SET button until the screen is flashing."
    }

    /// Text constants used in `AddInfoView`.
    struct AddInfoViewStrings {
        static let title = "Add your info"
        static let description = "Use the UP and DOWN arrows to make selections with your scale, and hit the SET button to confirm."
        static let userNumberTitle = "User number:"
        static let userNumberDescription = "Pick one that no one else is using for this scale."
        static let bodyCompositionTitle = "Body composition:"
        static let bodyCompositionDescription = "Choose the option that most accurately describes you. \"Athlete\" is for those who spend 12+ hours a week vigorously exercising."
        static let heightAgeTitle = "Height & Age"
    }

    /// Text constants used in the "Time to weigh in" screen.
    struct WeighInTimeStrings {
        static let title = "Time to weigh in!"
        static let description = "Set your scale on a hard, flat surface, step on, and wait for your results. When you see the barcode on the scale, press NEXT and aim your phone's camera at the code."
    }
    
    ///
    struct FinishViewStrings {
        static let title = "Your scale is paired and ready to go!"
        static let description = "To sync new entries, tap the icon at the bottom right of the app when you see the result code display on your scale’s screen."
    }
}
