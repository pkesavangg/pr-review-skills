package com.greatergoods.meapp.features.integration.model

enum class HealthConnectAction(val value: String) {
  CONNECT("connect"),
  FINISH("finish"),
  OPEN_HEALTH_CONNECT("open health connect"),
  EXIT("exit"),
  UPDATE_PERMISSIONS("update permissions"),
  SKIP("skip"),
  REMOVE("remove");

  companion object {
    /** Convert a raw String (e.g. from network / DataStore) back to the enum */
    fun fromValue(value: String): HealthConnectAction? =
      entries.firstOrNull { it.value == value }
  }
}
