package com.example.repoi

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform