package com.dmdbrands.gurus.weight.core.service.pushNotification

import com.dmdbrands.gurus.weight.domain.enums.ProductType

/**
 * Parsed FCM push payload for entry-sync notifications (MOB-434).
 *
 * Assumed **data-only** contract — the backend should send these as `message.data`
 * keys with NO `notification` block, so [com.google.firebase.messaging.FirebaseMessagingService.onMessageReceived]
 * always fires (even when the app is backgrounded or killed) and the app can fully
 * brand/format the notification:
 *
 * | key           | meaning                                                              |
 * |---------------|----------------------------------------------------------------------|
 * | `accountId`   | account the entry synced to — used to resolve the name locally       |
 * | `destination` | [ProductType.id] (`weight_scale`/`blood_pressure`/`baby_scale`)      |
 * | `measurement` | server-formatted reading (e.g. `28.6 lb`) — only "with measurement"  |
 * | `monthKey`    | optional month/timestamp key to open the matching History detail     |
 * | `babyId`      | optional baby profile id (baby deep links)                           |
 *
 * When the data keys are absent it falls back to the legacy `notification` title/body
 * so the current foreground push still renders.
 */
data class NotificationPayload(
  val accountId: String?,
  val destination: String?,
  val measurement: String?,
  val monthKey: String?,
  val babyId: String?,
  val fallbackTitle: String?,
  val fallbackBody: String?,
) {
  /** Resolves [destination] to a [ProductType], or null when unknown. */
  val productType: ProductType?
    get() = destination?.let { ProductType.fromId(it) }

  companion object {
    fun from(
      data: Map<String, String>,
      notificationTitle: String?,
      notificationBody: String?,
    ): NotificationPayload =
      NotificationPayload(
        accountId = data["accountId"]?.takeIf { it.isNotBlank() },
        destination = data["destination"]?.takeIf { it.isNotBlank() },
        measurement = data["measurement"]?.takeIf { it.isNotBlank() },
        monthKey = data["monthKey"]?.takeIf { it.isNotBlank() },
        babyId = data["babyId"]?.takeIf { it.isNotBlank() },
        fallbackTitle = notificationTitle,
        fallbackBody = notificationBody,
      )
  }
}
