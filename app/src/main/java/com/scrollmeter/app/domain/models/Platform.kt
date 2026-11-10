package com.scrollmeter.app.domain.models

/**
 * Supported short-video platforms monitored by ScrollMeter.
 * No other application packages are ever inspected or recorded.
 */
enum class Platform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val packageName: String
) {
    YOUTUBE_SHORTS(
        id = "YOUTUBE_SHORTS",
        displayName = "YouTube Shorts",
        shortName = "YouTube",
        packageName = "com.google.android.youtube"
    ),
    INSTAGRAM_REELS(
        id = "INSTAGRAM_REELS",
        displayName = "Instagram Reels",
        shortName = "Instagram",
        packageName = "com.instagram.android"
    );

    companion object {
        fun fromPackageName(packageName: CharSequence?): Platform? {
            val pkg = packageName?.toString() ?: return null
            return entries.firstOrNull { it.packageName == pkg }
        }

        fun fromId(id: String): Platform {
            return entries.firstOrNull { it.id == id } ?: YOUTUBE_SHORTS
        }
    }
}

enum class ThemePreference(val id: String, val label: String) {
    SYSTEM("SYSTEM", "Follow System"),
    LIGHT("LIGHT", "Light Mode"),
    DARK("DARK", "Dark Mode");

    companion object {
        fun fromId(id: String): ThemePreference =
            entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}
