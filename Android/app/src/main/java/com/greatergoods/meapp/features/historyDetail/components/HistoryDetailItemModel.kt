package com.greatergoods.meapp.features.historyDetail.components

/**
 * Data class for a history detail item (Figma node 7657-211196).
 */
data class HistoryDetailItemModel(
    val date: String, // e.g. "Dec 16"
    val time: String, // e.g. "2:10 PM"
    val weight: String, // e.g. "149.2"
    val unit: String = "lbs",
)
