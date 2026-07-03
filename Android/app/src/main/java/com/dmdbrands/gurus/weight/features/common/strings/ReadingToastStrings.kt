package com.dmdbrands.gurus.weight.features.common.strings

import com.dmdbrands.gurus.weight.domain.enums.ProductType

object ReadingToastStrings {
    // Figma 30295-24793 / 30295-25144: the arrival card title is the generic "New Reading Received"
    // for every product (the value + its product color convey the type). Single-baby uses
    // [titleForBaby].
    fun title(type: ProductType): String = when (type) {
        ProductType.MY_WEIGHT -> "New Reading Received"
        ProductType.BLOOD_PRESSURE -> "New Reading Received"
        ProductType.BABY -> "New Reading Received"
    }

    /** Single-baby arrival card title, e.g. "New Reading Received for PRINCY". */
    fun titleForBaby(name: String): String = "New Reading Received for ${name.uppercase()}"

    /** Manual-entry confirmation card title (Figma 30456-24170). */
    const val SavedToLog = "New Reading saved to your log"

    fun primaryAction(type: ProductType): String = when (type) {
        ProductType.BABY -> "ASSIGN"
        else -> "SAVE"
    }

    fun secondaryAction(type: ProductType): String = when (type) {
        ProductType.BABY -> "DON'T ASSIGN"
        else -> "DISCARD"
    }

    fun assignedTo(name: String): String = "Reading assigned to ${name.uppercase()}"

    const val WrongBaby = "Have you assigned to Wrong baby?"
    const val Reassign = "Reassign"
    const val Save = "SAVE"

    /** Count-pill copy shown when multiple readings were buffered this session. */
    fun moreReadings(count: Int): String = "$count more readings received for this session"
    const val View = "VIEW"

    /** Shown when a baby scale reading arrives but no baby profile exists to save it to (Figma 30295-25144). */
    const val NoBabyTitle = "New Reading Received"
    // Short per Figma 30295-25144; the longer "…discarded and won't appear in History" copy is a
    // design annotation/note, not the visible subtitle.
    const val NoBabySubtitle = "Add a baby to save this reading."
    const val AddBaby = "ADD A BABY"
    const val Discard = "DISCARD"

    /** Shown when persisting a baby reading fails, so the user isn't told it succeeded. */
    const val SaveFailed = "Couldn't save the reading. Please try again."

    object AssignModal {
        const val Title = "Assign Measurement"
        const val Subtitle = "Which baby is this measurement for?"
        const val Assign = "ASSIGN"
        const val DontAssign = "DON'T ASSIGN"
        fun age(months: Int): String = "$months months old"

        /** Last row of the picker — routes into the Add-a-Baby flow instead of assigning. */
        const val AssignNewBaby = "Assign to new baby"
        const val AssignNewBabySubtitle = "create new baby profile"
    }
}
