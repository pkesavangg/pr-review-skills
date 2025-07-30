package com.dmdbrands.gurus.weight.features.integration.model

enum class HealthConnectSetup(val code: Int) {
  NONE(-1),
  START_CONNECT(0),
  FINISH_CONNECT(1),
  CANCEL_CONNECT(2),
  PERMISSION_LIMIT(3),
  COMPLETE_RECONNECTION(4),
  INCOMPLETE_RECONNECTION(5),
  FINISH_INCOMPLETE_RECONNECTION(6),
  USER_CONFLICT(7),
  FROM_HEALTH_CONNECT(8),
  OUT_OF_SYNC(9),
  MULTIPLE_DEVICE_CONNECTION(10);

  companion object {
    /** Quick lookup when you only have the raw Int */
    fun fromCode(code: Int): HealthConnectSetup? =
      entries.firstOrNull { it.code == code }
  }
}

data class ActionButtons(
  val primary: HealthConnectAction? = null,
  val secondary: HealthConnectAction? = null
)

data class PageLoadFrom(
  val dashBoard: Boolean = false,
  val integrationFailed: Boolean = false
)


