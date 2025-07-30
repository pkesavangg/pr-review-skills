package com.dmdbrands.gurus.weight.domain.model.api.metrics

data class WeightlessRequest(
    val isWeightlessOn: Boolean,
    val weightlessTimestamp: String?,
    val weightlessWeight: Double?,
)
