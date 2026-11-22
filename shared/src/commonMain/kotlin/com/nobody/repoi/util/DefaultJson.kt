package com.nobody.repoi.util

import kotlinx.serialization.json.Json

internal val DefaultJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    isLenient = true
}
