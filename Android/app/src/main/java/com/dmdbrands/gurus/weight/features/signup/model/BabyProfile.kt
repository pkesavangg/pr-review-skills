package com.dmdbrands.gurus.weight.features.signup.model

import com.dmdbrands.gurus.weight.domain.enums.Gender
import com.dmdbrands.gurus.weight.features.common.components.DateTimeValue
import java.util.UUID

data class BabyProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val birthday: DateTimeValue? = null,
    val biologicalSex: Gender? = null,
    val birthLength: String = "",
    val birthWeight: String = "",
    val birthWeightOz: String = "",
    val weightUnit: BabyWeightUnit = BabyWeightUnit.LBS,
)
