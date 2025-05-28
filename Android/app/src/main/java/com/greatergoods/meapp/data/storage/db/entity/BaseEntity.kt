package com.greatergoods.meapp.data.storage.db.entity

import androidx.room.ColumnInfo
import androidx.room.TypeConverters
import com.greatergoods.meapp.data.storage.db.converter.DateConverter
import com.greatergoods.meapp.data.storage.db.converter.JsonConverter

/**
 * Base entity class that contains common annotations and fields shared across entities.
 */
@TypeConverters(DateConverter::class, JsonConverter::class)
abstract class BaseEntity {
    @ColumnInfo(name = "createdAt")
    val createdAt: String? = null

    @ColumnInfo(name = "updatedAt")
    val updatedAt: String? = null

    @ColumnInfo(name = "isSynced")
    val isSynced: Boolean = false
} 