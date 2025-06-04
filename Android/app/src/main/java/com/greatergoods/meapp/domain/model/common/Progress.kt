package com.greatergoods.meapp.domain.model.common

import com.greatergoods.meapp.data.storage.db.entity.EntryEntity

data class Progress(
    val latest: EntryEntity? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val count: Int = 0,
    val initWt: Double = 0.0,
    val week: Double = 0.0,
    val month: Double = 0.0,
    val year: Double = 0.0,
    val total: Double = 0.0,
    val initWeek: EntryEntity? = null,
    val initMonth: EntryEntity? = null,
    val initYear: EntryEntity? = null
)
