package com.dmdbrands.gurus.weight.features.goal.strings

/**
 * String constants for the Goal screen.
 * All static text used in the goal feature must be declared here.
 */
object GoalStrings {
  const val PageTitle = "Goal Setting"
  const val Title = "Update Goal"
  const val Subtitle = "Choose to maintain or set a lose/gain goal."

  // Button Labels
  const val SaveGoalButton = "Save"
  const val UpdateGoalButton = "Update goal"
  const val KeepGoalButton = "Keep goal"
  const val SetNewGoalButton = "Set new goal"

  // Status Messages
  const val SaveErrorMessage = "Failed to save goal"
  const val GoalMetTitle = "Congratulations! You've hit your goal weight!"
  const val GoalMetMessage =
    "Would you like to set a new goal or maintain this goal weight?"
  const val MaintainButton = "Maintain"

  // Goal Leave Alert
  const val GoalLeaveTitle = "Goal Change"
  const val GoalLeaveMessage = "It looks like you’re moving away from your target weight. Do you want to set a new goal to get back on track?"
  const val YesButton = "Yes"
  const val NoButton = "No"

  // Loading Messages
  const val LoaderMessage = "Saving..."

  // Milestone Display
  const val GoalReached = "goal reached!"
  const val To = " to "
  const val Goal = " to goal"
  const val GoalWeight = "goal weight"
  const val Current = "Current"

  // Toast Messages
  const val SuccessTitle = "Success!"
  const val SuccessMessage = "Goal Saved."

  // Unsaved Changes Dialog
  const val UnsavedChangesTitle = "Confirm"
  const val UnsavedChangesMessage = "Are you sure you want to leave?"
  const val SaveButton = "Exit"
  const val DiscardButton = "Return"

  // region Accessibility (TalkBack)
  /** Spoken label for the icon-only close button in the app bar. */
  const val accCloseLabel = "Close"
  // endregion
}
