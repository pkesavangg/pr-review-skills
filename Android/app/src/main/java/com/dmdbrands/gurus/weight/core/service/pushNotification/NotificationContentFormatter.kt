package com.dmdbrands.gurus.weight.core.service.pushNotification

/**
 * Builds the constant-branded notification title and the account-aware body for
 * entry-sync notifications (MOB-434).
 *
 * Branding is constant: the title is always [TITLE] and the OS groups every
 * notification under it. The body carries the reading value (when present) plus the
 * account context, e.g. `New entry of 28.6 lb has been synced to John's account`.
 * Long names are capped at [MAX_NAME_LENGTH] characters + ellipsis.
 */
object NotificationContentFormatter {
  const val TITLE = "me.App"
  const val MAX_NAME_LENGTH = 20
  private const val ELLIPSIS = "…"

  /** Constant brand title shown for every notification. */
  fun title(): String = TITLE

  /** Caps [name] at [MAX_NAME_LENGTH] characters, appending an ellipsis when truncated. */
  fun capName(name: String): String =
    if (name.length > MAX_NAME_LENGTH) name.take(MAX_NAME_LENGTH) + ELLIPSIS else name

  /**
   * Builds the notification body from the [payload] and the locally-resolved [firstName].
   *
   * Falls back gracefully: when the name is unknown the body uses "your account"; when
   * neither measurement nor name is available it uses the server-supplied body text.
   */
  fun body(
    payload: NotificationPayload,
    firstName: String?,
  ): String {
    val name = firstName?.takeIf { it.isNotBlank() }?.let { capName(it) }
    val measurement = payload.measurement
    return when {
      measurement != null && name != null ->
        "New entry of $measurement has been synced to $name's account"
      measurement != null ->
        "New entry of $measurement has been synced to your account"
      name != null ->
        "New entry has been synced to $name's account"
      else ->
        payload.fallbackBody ?: "New entry has been synced to your account"
    }
  }
}
