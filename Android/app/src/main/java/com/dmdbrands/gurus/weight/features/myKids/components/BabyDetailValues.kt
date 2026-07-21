package com.dmdbrands.gurus.weight.features.myKids.components

import com.dmdbrands.gurus.weight.core.shared.utilities.ConversionTools
import com.dmdbrands.gurus.weight.domain.model.common.BabyProfile
import com.dmdbrands.gurus.weight.features.common.helper.DateFormatHelper
import com.dmdbrands.gurus.weight.features.myKids.strings.MyKidsStrings

/** Display-ready values for a baby's expanded detail rows (see [ExpandableBabyList]). */
data class BabyDetailValues(
    val birthday: String,
    val biologicalSex: String,
    val birthLength: String,
    val birthWeight: String,
)

/**
 * Formats a [baby]'s profile for the expanded detail rows, using the shared [ConversionTools]
 * baby helpers so length/weight match the account unit (metric vs imperial) exactly as the
 * History/Entry surfaces render them. Unset or non-positive fields fall back to
 * [MyKidsStrings.ValueUnset].
 */
internal fun babyDetailValues(baby: BabyProfile, isMetric: Boolean): BabyDetailValues =
    BabyDetailValues(
        birthday = baby.birthdate?.takeIf { it.isNotBlank() }
            ?.let { DateFormatHelper.formatDisplayDate(it) }
            ?: MyKidsStrings.ValueUnset,
        biologicalSex = baby.sex?.takeIf { it.isNotBlank() }
            ?.replaceFirstChar { it.uppercase() }
            ?: MyKidsStrings.ValueUnset,
        birthLength = baby.birthLengthMillimeters?.takeIf { it > 0 }
            ?.let { ConversionTools.convertBabyLengthToDisplay(it, isMetric) }
            ?: MyKidsStrings.ValueUnset,
        birthWeight = baby.birthWeightDecigrams?.takeIf { it > 0 }
            ?.let { ConversionTools.convertBabyWeightToDisplay(it, source = null, isMetric = isMetric) }
            ?: MyKidsStrings.ValueUnset,
    )
