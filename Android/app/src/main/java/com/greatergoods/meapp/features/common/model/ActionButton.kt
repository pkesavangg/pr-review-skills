package com.greatergoods.meapp.features.common.model

data class ActionButton(
    val text: String,
    val icon: Int? = null,
    val enabled: Boolean = true,
    val action: (() -> Unit) = {}
)
