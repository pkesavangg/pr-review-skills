package com.greatergoods.meapp.domain.model.common

import com.greatergoods.meapp.domain.model.storage.entry.Entry

data class Progress(
    val latest: Entry? = null,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val count: Int = 0,
    val initWt: Double = 0.0,
    val week: Double = 0.0,
    val month: Double = 0.0,
    val year: Double = 0.0,
    val total: Double = 0.0,
    val initWeek: Entry? = null,
    val initMonth: Entry? = null,
    val initYear: Entry? = null
)
