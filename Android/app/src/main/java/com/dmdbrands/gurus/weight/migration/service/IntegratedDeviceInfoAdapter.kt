package com.dmdbrands.gurus.weight.migration.service

import com.dmdbrands.gurus.weight.migration.model.IntegratedDeviceInfo
import com.dmdbrands.gurus.weight.migration.model.IntegrationData
import com.dmdbrands.gurus.weight.migration.model.OperationType
import com.dmdbrands.gurus.weight.migration.model.Preferences
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import java.lang.reflect.Type

class IntegratedDeviceInfoAdapter : JsonDeserializer<IntegratedDeviceInfo> {
  override fun deserialize(
    json: JsonElement,
    typeOfT: Type,
    context: JsonDeserializationContext
  ): IntegratedDeviceInfo? {
    val obj = json.asJsonObject

    val operationType = context.deserialize<OperationType>(obj["operationType"], OperationType::class.java)
    val isCurrentDeviceDeleted = obj["isCurrentDeviceDeleted"]?.asBoolean ?: false

    val scopesEl = obj["scopes"]
      ?: throw JsonParseException("Missing 'scopes' object")

    // Enforce: parent 'scopes' must be an OBJECT (IntegrationData), not an array
    if (!scopesEl.isJsonObject) {
      throw JsonParseException("Expected 'scopes' to be an OBJECT but was: ${scopesEl.javaClass.simpleName}")
    }

    val integrationData = context.deserialize<IntegrationData>(scopesEl, IntegrationData::class.java)

    return IntegratedDeviceInfo(
      operationType = operationType,
      scopes = integrationData,
      isCurrentDeviceDeleted = isCurrentDeviceDeleted,
    )
  }
}

class PreferencesAdapter : JsonDeserializer<Preferences> {
  override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Preferences {
    if (json.isJsonNull) return Preferences()

    val obj = json.asJsonObject
    val scopesEl = obj.get("scopes") ?: return Preferences()

    // Case 1: proper JSON array => ["a","b"]
    if (scopesEl.isJsonArray) {
      val list = scopesEl.asJsonArray
        .mapNotNull { it.takeIf { p -> p.isJsonPrimitive && p.asJsonPrimitive.isString }?.asString }
      return Preferences(list)
    }

    // Case 2: stringified list => "[a, b]" or "a, b" or "[\"a\",\"b\"]"
    if (scopesEl.isJsonPrimitive && scopesEl.asJsonPrimitive.isString) {
      val raw = scopesEl.asString.trim()

      // Try strict JSON array string first: ["a","b"]
      try {
        val el = JsonParser.parseString(raw)
        if (el.isJsonArray) {
          val list = el.asJsonArray
            .mapNotNull { it.takeIf { p -> p.isJsonPrimitive && p.asJsonPrimitive.isString }?.asString }
          return Preferences(list)
        }
      } catch (_: Exception) { /* fall through */
      }

      // Fallback: strip brackets and split by comma
      val cleaned = raw.removePrefix("[").removeSuffix("]")
      val list = cleaned.split(',')
        .map { it.trim().trim('"') }
        .filter { it.isNotEmpty() }
      return Preferences(list)
    }

    return Preferences()
  }
}
