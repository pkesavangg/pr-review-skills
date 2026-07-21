package com.dmdbrands.gurus.weight.features.myKids.strings

object MyKidsStrings {
    const val Title = "My Kids"
    const val EmptyDescription = "Add and manage baby profiles for use with the baby scale."
    const val AddABaby = "ADD A BABY"
    const val DeleteBaby = "Delete baby"
    const val EditBaby = "Edit baby"
    const val BabyFallbackPrefix = "Baby"
    const val AvatarFallback = "?"

    // Expanded baby-profile detail rows (see Figma node 34059-32734).
    const val Birthday = "Birthday"
    const val BiologicalSex = "Biological sex"
    const val BirthLength = "Birth length"
    const val BirthWeight = "Birth weight"

    /** Placeholder shown when a baby profile detail is not set. */
    const val ValueUnset = "--"

    // region Accessibility (TalkBack)
    /** Spoken label for the icon-only close button in the app bar. */
    const val accCloseLabel = "Close"

    /** Spoken label for the chevron that expands a baby's profile details. */
    const val accExpandLabel = "Show baby details"

    /** Spoken label for the chevron that collapses a baby's profile details. */
    const val accCollapseLabel = "Hide baby details"
    // endregion
}
