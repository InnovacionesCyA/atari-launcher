package com.atari.launcher.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "GameListViewModel"

class GameListViewModel(application: Application) : AndroidViewModel(application) {

    private val _roms = MutableStateFlow<List<RomItem>>(emptyList())
    val roms: StateFlow<List<RomItem>> = _roms

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasStoragePermission = MutableStateFlow(false)
    val hasStoragePermission: StateFlow<Boolean> = _hasStoragePermission

    private val prefs = application.getSharedPreferences("game_prefs", 0)

    init {
        RomScanner.ensureRomDir()
    }

    fun setStoragePermission(granted: Boolean) {
        _hasStoragePermission.value = granted
        if (granted) scanRoms()
    }

    fun scanRoms() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val roms = RomScanner.scanRoms()
                // Apply saved display names
                val enriched = roms.map { rom ->
                    val savedName = prefs.getString("name_${rom.key}", null)
                    if (savedName != null) rom.copy(displayName = savedName) else rom
                }
                _roms.value = enriched
                Log.i(TAG, "Scanned ${enriched.size} ROMs")
            } catch (e: Exception) {
                Log.e(TAG, "Scan failed: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameGame(rom: RomItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val newFile = File(rom.file.parent, "$newName.${rom.file.extension}")
                if (rom.file.renameTo(newFile)) {
                    prefs.edit().remove("name_${rom.key}").apply()
                    scanRoms()
                } else {
                    // Fallback: save display name override
                    prefs.edit().putString("name_${rom.key}", newName).apply()
                    _roms.value = _roms.value.map {
                        if (it.key == rom.key) it.copy(displayName = newName) else it
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Rename failed: ${e.message}")
            }
        }
    }

    fun deleteGame(rom: RomItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                rom.file.delete()
                prefs.edit().remove("name_${rom.key}").apply()
                _roms.value = _roms.value.filter { it.key != rom.key }
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed: ${e.message}")
            }
        }
    }
}
