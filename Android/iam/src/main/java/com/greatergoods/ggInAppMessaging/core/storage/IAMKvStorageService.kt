package com.greatergoods.ggInAppMessaging.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key-Value storage service for IAM data
 * Android equivalent of iOS IAMKvStorageService
 */
@Singleton
class IAMKvStorageService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val PREFS_NAME = "gg_iam_storage"
    }
    
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Sets a string value for the given key
     */
    fun setValue(value: String, forKey: String) {
        sharedPreferences.edit().putString(forKey, value).apply()
    }
    
    /**
     * Gets a string value for the given key
     */
    fun getValue(forKey: String): String? {
        return sharedPreferences.getString(forKey, null)
    }
    
    /**
     * Clears the value for the given key
     */
    fun clearValue(forKey: String) {
        sharedPreferences.edit().remove(forKey).apply()
    }
    
    /**
     * Sets a codable object as JSON string
     */
    fun <T> setCodable(value: T, forKey: String, gson: com.google.gson.Gson) {
        val json = gson.toJson(value)
        setValue(json, forKey)
    }
    
    /**
     * Gets a codable object from JSON string
     */
    fun <T> getCodable(forKey: String, type: java.lang.reflect.Type, gson: com.google.gson.Gson): T? {
        val json = getValue(forKey) ?: return null
        return try {
            gson.fromJson<T>(json, type)
        } catch (e: Exception) {
            null
        }
    }
}