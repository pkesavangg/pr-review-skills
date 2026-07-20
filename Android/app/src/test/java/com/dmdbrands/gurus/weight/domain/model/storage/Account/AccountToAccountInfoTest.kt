package com.dmdbrands.gurus.weight.domain.model.storage.Account

import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.dmdbrands.gurus.weight.domain.model.common.MeasurementUnits
import com.dmdbrands.gurus.weight.domain.model.common.WeightUnit
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for [Account.toAccountInfo] (MOB-1499).
 *
 * The offline validity check re-syncs the local account through
 * `toAccountInfo() -> syncAccountSettingsWithServer -> upsertProductSettings`. If the mapping drops
 * `productTypes` / `measurementUnits`, that self-sync falls back to the "weight" / metric defaults
 * and clobbers a non-weight account (e.g. a BPM-only signup) on offline relaunch.
 */
class AccountToAccountInfoTest {

  private fun account(
    productTypes: List<String>,
    measurementUnits: MeasurementUnits,
  ) = Account(
    id = "acc-1",
    firstName = "John",
    lastName = "Doe",
    dob = "1990-01-01",
    email = "john@example.com",
    gender = "male",
    isActiveAccount = true,
    isLoggedIn = true,
    isExpired = false,
    zipcode = "12345",
    weightUnit = WeightUnit.LB,
    height = 1750,
    activityLevel = "normal",
    productTypes = productTypes,
    measurementUnits = measurementUnits,
  )

  @Test
  fun `toAccountInfo preserves a BPM-only product list`() {
    val info = account(
      productTypes = listOf(ProductType.BLOOD_PRESSURE.apiValue),
      measurementUnits = MeasurementUnits.IMPERIAL_LB_OZ,
    ).toAccountInfo()

    assertThat(info.productTypes).containsExactly(ProductType.BLOOD_PRESSURE.apiValue)
    assertThat(info.measurementUnits).isEqualTo(MeasurementUnits.IMPERIAL_LB_OZ.value)
  }

  @Test
  fun `toAccountInfo preserves a multi-product list`() {
    val products = listOf(ProductType.MY_WEIGHT.apiValue, ProductType.BABY.apiValue)

    val info = account(products, MeasurementUnits.METRIC).toAccountInfo()

    assertThat(info.productTypes).containsExactlyElementsIn(products).inOrder()
    assertThat(info.measurementUnits).isEqualTo(MeasurementUnits.METRIC.value)
  }
}
