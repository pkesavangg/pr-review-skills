package com.dmdbrands.gurus.weight.domain.model.api.metrics

data class BodyCompRequest(
    val height: Double,
    val activityLevel: String, // 'normal' or 'athlete'
    val weightUnit: String // 'lb' or 'kg'
)
