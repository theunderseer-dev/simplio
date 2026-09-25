package com.theundeerseer.simplio

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform