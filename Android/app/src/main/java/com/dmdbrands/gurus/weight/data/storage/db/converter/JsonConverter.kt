package com.dmdbrands.gurus.weight.data.storage.db.converter

import androidx.room.TypeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Type converter for handling JSON data in Room database.
 *
 * Uses kotlinx.serialization (compile-time codegen) instead of Gson to avoid
 * reflective TypeVariable resolution, which NPEs on Android 16 (SDK 36) ART. See MOB-394.
 */
class JsonConverter {
  private val json = Json { ignoreUnknownKeys = true }
  private val stringListSerializer = ListSerializer(String.serializer())

  @TypeConverter
  fun fromString(value: String?): List<String>? {
    if (value == null) return null
    return runCatching { json.decodeFromString(stringListSerializer, value) }
      .getOrElse {
        AppLog.e(TAG, "fromString deserialize failed; returning empty list", it)
        emptyList()
      }
  }

  @TypeConverter
  fun fromList(list: List<String>?): String? {
    if (list == null) return null
    return json.encodeToString(stringListSerializer, list)
  }

  @TypeConverter
  fun toList(jsonString: String): List<String> {
    return runCatching { json.decodeFromString(stringListSerializer, jsonString) }
      .getOrElse {
        AppLog.e(TAG, "toList deserialize failed; returning empty list", it)
        emptyList()
      }
  }

  private companion object {
    const val TAG = "JsonConverter"
  }
}
