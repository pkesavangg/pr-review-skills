package com.greatergoods.meapp.features.goal.strings

/**
 * String constants for the Goal screen.
 * All static text used in the goal feature must be declared here.
 */
object GoalStrings {
    const val PageTitle = "Goal Setting"
    const val Title = "Update Goal"
    const val Subtitle = "Choose to maintain or set a lose/gain goal."

    // Form Labels
    const val GoalTypeLabel = "Goal Type"
    const val CurrentWeightLabel = "Current Weight (%s)"
    const val GoalWeightLabel = "Goal Weight (%s)"

    // Goal Type Options
    const val MaintainGoal = "Maintain"
    const val LoseGainGoal = "Lose / Gain"

    // Goal Type Descriptions
    const val MaintainDescription = "Maintain your current weight"
    const val LoseGainDescription = "Set a target weight to lose or gain"

    // Button Labels
    const val SaveGoalButton = "Save"
    const val CancelButton = "Cancel"

    // Status Messages
    const val SaveSuccessMessage = "Goal saved successfully"
    const val SaveErrorMessage = "Failed to save goal"
    const val GoalMetTitle = "Congratulations!"
    const val GoalMetMessage = "You've reached your goal! Would you like to set a new goal or maintain your current weight?"
    const val NewGoalButton = "New Goal"
    const val MaintainButton = "Maintain"

    // Goal Leave Alert
    const val GoalLeaveTitle = "Goal Change"
    const val GoalLeaveMessage = "Your weight has changed from your maintain goal. Would you like to update your goal?"
    const val YesButton = "Yes"
    const val NoButton = "No"

    // Loading Messages
    const val LoaderMessage = "Saving goal..."

    // Validation Messages
    const val CurrentWeightRequired = "Current weight is required"
    const val GoalWeightRequired = "Goal weight is required"
    const val InvalidWeightFormat = "Please enter a valid weight"
    const val WeightOutOfRange = "Weight must be between 50-500 lbs or 20-200 kg"

    // Help and Info
    const val GoalInfoTitle = "About Goal Setting"
    const val GoalInfoMessage = "Setting a realistic goal helps track your progress. You can change your goal at any time."

    // Milestone Display
    const val GoalReached = "Goal Reached!"
    const val ToGoal = "to "
    const val GoalWeight = "goal weight"

    // Toast Messages
    const val SuccessTitle = "Success!"
    const val SuccessMessage = "Goal Updated."

    // Unsaved Changes Dialog
    const val UnsavedChangesTitle = "Unsaved Changes"
    const val UnsavedChangesMessage = "You have unsaved changes. Do you want to save before leaving?"
    const val SaveButton = "Save"
    const val DiscardButton = "Discard"
}
