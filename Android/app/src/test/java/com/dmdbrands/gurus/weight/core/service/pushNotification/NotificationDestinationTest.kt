package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.enums.ProductType
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class NotificationDestinationTest {

  @Test
  fun `maps product plus month key to History detail`() {
    val route = NotificationDestination.toRoute("baby_scale", "2026-06")
    assertThat(route).isEqualTo(AppRoute.History.MonthDetails("2026-06", ProductType.BABY))
  }

  @Test
  fun `falls back to History list when month key missing`() {
    val route = NotificationDestination.toRoute("weight_scale", null)
    assertThat(route).isEqualTo(AppRoute.Main.History)
  }

  @Test
  fun `falls back to History list when month key blank`() {
    val route = NotificationDestination.toRoute("weight_scale", "  ")
    assertThat(route).isEqualTo(AppRoute.Main.History)
  }

  @Test
  fun `falls back to History list when destination unknown`() {
    val route = NotificationDestination.toRoute("unknown", "2026-06")
    assertThat(route).isEqualTo(AppRoute.Main.History)
  }

  @Test
  fun `falls back to History list when destination null`() {
    val route = NotificationDestination.toRoute(null, "2026-06")
    assertThat(route).isEqualTo(AppRoute.Main.History)
  }
}
