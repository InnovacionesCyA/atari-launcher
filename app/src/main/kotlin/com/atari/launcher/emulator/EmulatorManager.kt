package com.atari.launcher.emulator

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

private const val TAG = "EmulatorManager"

enum class EmulatorState {
    IDLE, LOADING, RUNNING, PAUSED, ERROR
}

class EmulatorManager(private val context: Context) {

    private val _state = MutableStateFlow(EmulatorState.IDLE)
    val state: StateFlow<EmulatorState> = _state

    private val _currentRom = MutableStateFlow<String?>(null)
    val currentRom: StateFlow<String?> = _currentRom

    private var audioTrack: AudioTrack? = null
    private var initialized = false

    private val saveDir: String get() =
        File(context.filesDir, "saves").also { it.mkdirs() }.absolutePath

    private val systemDir: String get() =
        File(context.filesDir, "system").also { it.mkdirs() }.absolutePath

    private val contentDir: String get() =
        File(context.filesDir, "content").also { it.mkdirs() }.absolutePath

    fun ensureInitialized(): Boolean {
        if (initialized) return true
        val ok = EmulatorBridge.nativeInit(saveDir, systemDir, contentDir)
        if (ok) {
            initialized = true
            Log.i(TAG, "Core initialized")
        } else {
            Log.e(TAG, "Core init failed")
            _state.value = EmulatorState.ERROR
        }
        return ok
    }

    fun setSurface(surface: Surface?) {
        EmulatorBridge.nativeSetSurface(surface)
    }

    fun loadGame(romPath: String): Boolean {
        if (!ensureInitialized()) return false

        _state.value = EmulatorState.LOADING
        _currentRom.value = romPath

        val ok = EmulatorBridge.nativeLoadGame(romPath)
        if (ok) {
            // Wait a tiny bit for the core to start and report AV info
            Thread.sleep(100)
            setupAudio()
            _state.value = EmulatorState.RUNNING
            Log.i(TAG, "Game loaded: $romPath")
        } else {
            Log.e(TAG, "Failed to load: $romPath")
            _state.value = EmulatorState.ERROR
        }
        return ok
    }

    private fun setupAudio() {
        audioTrack?.release()
        audioTrack = null

        val sampleRate = EmulatorBridge.nativeGetSampleRate().toInt().let {
            if (it > 0) it else 22050
        }

        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
            EmulatorBridge.nativeSetAudioTrack(track)
            Log.i(TAG, "AudioTrack created: sampleRate=$sampleRate bufSize=$bufferSize")
        } catch (e: Exception) {
            Log.e(TAG, "AudioTrack setup failed: ${e.message}")
        }
    }

    fun pause() {
        if (_state.value == EmulatorState.RUNNING) {
            EmulatorBridge.nativePause()
            audioTrack?.pause()
            _state.value = EmulatorState.PAUSED
        }
    }

    fun resume() {
        if (_state.value == EmulatorState.PAUSED) {
            audioTrack?.play()
            EmulatorBridge.nativeResume()
            _state.value = EmulatorState.RUNNING
        }
    }

    fun stop() {
        EmulatorBridge.nativeStop()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        EmulatorBridge.nativeSetAudioTrack(null)
        _state.value = EmulatorState.IDLE
        _currentRom.value = null
    }

    fun reset() {
        EmulatorBridge.nativeReset()
    }

    fun saveState(gameKey: String): Boolean {
        val path = File(saveDir, "$gameKey.state").absolutePath
        return EmulatorBridge.nativeSaveState(path)
    }

    fun loadState(gameKey: String): Boolean {
        val path = File(saveDir, "$gameKey.state")
        if (!path.exists()) return false
        return EmulatorBridge.nativeLoadState(path.absolutePath)
    }

    fun hasSaveState(gameKey: String): Boolean {
        return File(saveDir, "$gameKey.state").exists()
    }

    fun deleteSaveState(gameKey: String): Boolean {
        return File(saveDir, "$gameKey.state").delete()
    }

    fun setInputMask(mask: Int) {
        EmulatorBridge.nativeSetInput(mask)
    }

    fun deinit() {
        stop()
        if (initialized) {
            EmulatorBridge.nativeDeinit()
            initialized = false
        }
    }
}
