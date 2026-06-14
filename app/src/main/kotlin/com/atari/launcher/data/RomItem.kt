package com.atari.launcher.data

import java.io.File

data class RomItem(
    val file: File,
    val displayName: String,          // Name shown in UI (can be renamed)
    val artFile: File?,               // Local cover art file (null = use fetched or placeholder)
    val artUrl: String? = null,       // Remote URL to fetch if artFile is null
    val saveStateEnabled: Boolean = true
) {
    val key: String get() = file.nameWithoutExtension.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val extension: String get() = file.extension.lowercase()
}
