package com.haris.guard

// Feature: web page to view the photos from any browser.
object Web {
    const val URL = "https://majedalejil-bit.github.io/haris-v7/"

    fun available(): Boolean = URL.startsWith("https://")
}
