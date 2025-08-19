package com.dmdbrands.gurus.weight.migration.model

import com.dmdbrands.gurus.weight.domain.model.integrations.IntegrationType
import com.google.gson.annotations.SerializedName

data class IntegrationData(
  @SerializedName("deviceId")
  val deviceId: String,

  @SerializedName("type")
  val type: IntegrationType,

  @SerializedName("preferences")
  val preferences: Preferences? = null,

  @SerializedName("integratedAt")
  val integratedAt: String? = null,

  @SerializedName("updatedAt")
  val updatedAt: String? = null
)

data class IntegratedDeviceInfo(
  @SerializedName("operationType")
  val operationType: OperationType,

  // TS calls this "scopes" but it’s actually an IntegrationData object
  @SerializedName("scopes")
  val scopes: IntegrationData,

  @SerializedName("isCurrentDeviceDeleted")
  val isCurrentDeviceDeleted: Boolean
)

data class IonicHealthConnectData(
  val assignedTo: String,
  val integrated: String,
  val alertSeen: String,
  val open: String,
  val outOfSync: String,
  val modalState: String,
  val integrationStatus: IntegratedDeviceInfo?,
  val grantedPermission: String
)

enum class OperationType {
  @SerializedName("save")
  SAVE,

  @SerializedName("remove")
  REMOVE
}

data class Preferences(
  // TS: scopes: [] (untyped). Assuming list of strings.
  @SerializedName("scopes")
  val scopes: List<String> = emptyList()
)
