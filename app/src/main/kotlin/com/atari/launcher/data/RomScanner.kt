package com.atari.launcher.data

import android.util.Log
import java.io.File

private const val TAG = "RomScanner"

object RomScanner {

    val ROM_DIR = File("/storage/emulated/0/Documents/AtariRoms")

    private val SUPPORTED_EXTENSIONS = setOf("rom", "car", "atr", "xex", "bin")

    fun ensureRomDir(): Boolean {
        return try {
            ROM_DIR.mkdirs()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Could not create ROM dir: ${e.message}")
            false
        }
    }

    fun scanRoms(): List<RomItem> {
        if (!ROM_DIR.exists() || !ROM_DIR.isDirectory) {
            Log.w(TAG, "ROM dir does not exist or is not a directory: $ROM_DIR")
            return emptyList()
        }

        val files = ROM_DIR.listFiles() ?: return emptyList()
        val roms = mutableListOf<RomItem>()

        for (file in files) {
            if (!file.isFile) continue
            val ext = file.extension.lowercase()
            if (ext !in SUPPORTED_EXTENSIONS) continue

            val baseName = file.nameWithoutExtension
            // Check for sidecar art file
            val artFile = file.resolveSibling("$baseName.png")
                .takeIf { it.exists() }

            roms.add(
                RomItem(
                    file = file,
                    displayName = baseName,
                    artFile = artFile,
                )
            )
        }

        return roms.sortedBy { it.displayName.lowercase() }
    }
}
