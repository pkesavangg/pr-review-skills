package com.dmdbrands.gurus.weight.features.common.components.strings

/**
 * Copy for the shared baby empty state shown on Dashboard / Manual Entry / History
 * when the user owns a baby scale but has no baby profiles. (MOB-416)
 */
object BabyEmptyStateStrings {
    const val Title = "No babies added yet"
    const val Description = "Add a baby profile to view their measurement history."
    // Manual Entry variant: there's no history to view here, the user logs readings. (MOB-592)
    const val EntryDescription = "Add a baby profile to log measurements manually."
    // Dashboard snapshot card variant. (MOB-592)
    const val SnapshotDescription = "Add a baby to start tracking growth and overall health."
    const val AddABaby = "Add a Baby"
    const val IconContentDescription = "Baby"
}
