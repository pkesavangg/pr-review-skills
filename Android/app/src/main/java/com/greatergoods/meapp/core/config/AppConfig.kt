package com.greatergoods.meapp.core.config

object AppConfig {
    const val AppName = "MeApp"
    const val BASE_URL = "https://api.weightgurus.com/v3/"
    const val BASE_URL_HEADER = "Base-url"
    const val AUTHORIZATION_HEADER = "Authorization"
    const val PRODUCT_URL = "https://greatergoods.com"

    object Integrations {
        fun fitbit(accountId: String) =
            "https://www.fitbit.com/oauth2/authorize?response_type=code&client_id=22B2QV&redirect_uri=https%3A%2F%2Fapi.weightgurus.com%2Fv2%2Fauth%2Ffitbit&scope=profile%20weight&state=v3-$accountId"

        fun mfPal(accountId: String) =
            "https://api.myfitnesspal.com/v2/oauth2/auth?client_id=weightguru&scope=measurements&response_type=code&redirect_uri=https%3a%2f%2fapi.weightgurus.com%2fv2%2fauth%2fmyfitnesspal?&state=v3-$accountId"
    }
}
