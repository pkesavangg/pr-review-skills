package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.core.navigation.AppRoute
import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Maps a push payload `destination` (a [ProductType.id]) and optional month key to an
 * in-app navigation target for notification taps (MOB-434).
 *
 * When both a known product and a month key are present it deep-links straight to the
 * matching History detail; otherwise it falls back to the History list.
 */
object NotificationDestination {
  fun toRoute(
    destination: String?,
    monthKey: String?,
  ): AppRoute {
    val product = destination?.let { ProductType.fromId(it) }
    return if (product != null && !monthKey.isNullOrBlank()) {
      AppRoute.History.MonthDetails(monthKey, product)
    } else {
      AppRoute.Main.History
    }
  }
}
