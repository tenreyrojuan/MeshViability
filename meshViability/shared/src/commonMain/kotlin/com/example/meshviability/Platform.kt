package com.example.meshviability

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform