package com.dmdbrands.gurus.weight.data.storage.datastore

import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.dmdbrands.gurus.weight.proto.BabyWeightUnit

internal fun BabyWeightUnit?.toWeightUnit(): WeightUnit = when (this) {
  BabyWeightUnit.BABY_WEIGHT_UNIT_LB -> WeightUnit.LB
  BabyWeightUnit.BABY_WEIGHT_UNIT_KG -> WeightUnit.KG
  // UNSPECIFIED is the proto default for unmigrated accounts; treat as
  // LB_OZ so existing users see the canonical baby unit on first read.
  BabyWeightUnit.BABY_WEIGHT_UNIT_LB_OZ,
  BabyWeightUnit.BABY_WEIGHT_UNIT_UNSPECIFIED,
  BabyWeightUnit.UNRECOGNIZED,
  null,
  -> WeightUnit.LB_OZ
}

internal fun WeightUnit.toProto(): BabyWeightUnit = when (this) {
  WeightUnit.LB -> BabyWeightUnit.BABY_WEIGHT_UNIT_LB
  WeightUnit.KG -> BabyWeightUnit.BABY_WEIGHT_UNIT_KG
  WeightUnit.LB_OZ -> BabyWeightUnit.BABY_WEIGHT_UNIT_LB_OZ
}
