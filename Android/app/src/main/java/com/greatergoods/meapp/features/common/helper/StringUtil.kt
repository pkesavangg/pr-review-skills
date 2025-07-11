package com.greatergoods.meapp.features.common.helper

object StringUtil {
  fun String.displayName(): String {
    return this.replace("_", " ")
  }
}
