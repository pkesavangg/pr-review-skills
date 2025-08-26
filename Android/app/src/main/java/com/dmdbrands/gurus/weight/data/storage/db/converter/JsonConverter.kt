package com.dmdbrands.gurus.weight.data.storage.db.converter

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Type converter for handling JSON data in Room database.
 */
class JsonConverter {
  private val gson = Gson()

  /**
   * Convert a JSON string to a List of objects.
   *
   * @param value The JSON string to convert
   * @return The List of objects, or null if the string is null
   */
  @TypeConverter
  fun fromString(value: String?): List<String>? {
    if (value == null) {
      return null
    }
    val listType = object : TypeToken<List<String>>() {}.type
    return gson.fromJson(value, listType)
  }

  /**
   * Convert a List of objects to a JSON string.
   *
   * @param list The List to convert
   * @return The JSON string, or null if the list is null
   */
  @TypeConverter
  fun fromList(list: List<String>?): String? {
    if (list == null) {
      return null
    }
    return gson.toJson(list)
  }

  @TypeConverter
  fun toList(json: String): List<String> {
    val type = object : TypeToken<List<String>>() {}.type
    return gson.fromJson(json, type)
  }

  /**
   * Convert a JSON string to a Map of objects.
   *
   * @param value The JSON string to convert
   * @return The Map of objects, or null if the string is null
   */
  @TypeConverter
  fun fromMapString(value: String?): Map<String, Any>? {
    if (value == null) {
      return null
    }
    val mapType = object : TypeToken<Map<String, Any>>() {}.type
    return gson.fromJson(value, mapType)
  }

  /**
   * Convert a Map of objects to a JSON string.
   *
   * @param map The Map to convert
   * @return The JSON string, or null if the map is null
   */
  @TypeConverter
  fun fromMap(map: Map<String, Any>?): String? {
    if (map == null) {
      return null
    }
    return gson.toJson(map)
  }
}
