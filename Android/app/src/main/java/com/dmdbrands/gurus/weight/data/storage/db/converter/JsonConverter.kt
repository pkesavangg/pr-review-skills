package com.dmdbrands.gurus.weight.data.storage.db.converter

import androidx.room.TypeConverter
import com.dmdbrands.gurus.weight.core.shared.utilities.logging.AppLog
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
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

  // Decode with a nullable element serializer so legacy Gson-written rows that contain
  // null elements (e.g. ["a", null, "b"]) parse instead of throwing — we then drop the
  // nulls via filterNotNull() rather than collapsing the whole list to empty. See MOB-394.
  // A List<String> is a valid List<String?> for encoding, and the output format is
  // unchanged (["a","b"]), so the same serializer covers both read and write paths.
  private val stringListSerializer = ListSerializer(String.serializer().nullable)

  @TypeConverter
  fun fromString(value: String?): List<String>? {
    if (value == null) return null
    return decode(value, "fromString")
  }

  @TypeConverter
  fun fromList(list: List<String>?): String? {
    if (list == null) return null
    return json.encodeToString(stringListSerializer, list)
  }

  @TypeConverter
  fun toList(jsonString: String): List<String> = decode(jsonString, "toList")

  private fun decode(value: String, caller: String): List<String> =
    runCatching { json.decodeFromString(stringListSerializer, value).filterNotNull() }
      .getOrElse {
        AppLog.e(TAG, "$caller deserialize failed; returning empty list", it)
        emptyList()
      }

  private companion object {
    const val TAG = "JsonConverter"
  }
}
