package com.atari.launcher.emulator

import android.media.AudioTrack
import android.view.Surface

/**
 * JNI bridge to the atari800 libretro core.
 * All methods map to native implementations in libretro_jni.cpp.
 */
object EmulatorBridge {

    init {
        System.loadLibrary("atarilauncher_jni")
    }

    /** Initialize the libretro core. Call once before anything else. */
    @JvmStatic
    external fun nativeInit(saveDir: String, systemDir: String, contentDir: String): Boolean

    /** Pass the AudioTrack object so native audio callbacks can write PCM data. */
    @JvmStatic
    external fun nativeSetAudioTrack(audioTrack: AudioTrack?)

    /** Load and start running a ROM. Starts emulation thread. */
    @JvmStatic
    external fun nativeLoadGame(romPath: String): Boolean

    /** Stop emulation and unload the game. */
    @JvmStatic
    external fun nativeStop()

    /** Pause the emulation loop. */
    @JvmStatic
    external fun nativePause()

    /** Resume the emulation loop. */
    @JvmStatic
    external fun nativeResume()

    /** Give the emulator a Surface to render into. Pass null to detach. */
    @JvmStatic
    external fun nativeSetSurface(surface: Surface?)

    /**
     * Set controller input bitmask.
     * Bit layout matches RETRO_DEVICE_ID_JOYPAD_* constants:
     *   bit 0 = B (fire)
     *   bit 2 = SELECT
     *   bit 3 = START
     *   bit 4 = UP
     *   bit 5 = DOWN
     *   bit 6 = LEFT
     *   bit 7 = RIGHT
     *   bit 8 = A (fire alt)
     */
    @JvmStatic
    external fun nativeSetInput(buttonMask: Int)

    /** Save emulator state to file. Returns true on success. */
    @JvmStatic
    external fun nativeSaveState(path: String): Boolean

    /** Load emulator state from file. Returns true on success. */
    @JvmStatic
    external fun nativeLoadState(path: String): Boolean

    /** Soft reset the current game. */
    @JvmStatic
    external fun nativeReset()

    /** Deinit the core entirely. Call on app destroy. */
    @JvmStatic
    external fun nativeDeinit()

    @JvmStatic
    external fun nativeGetFrameWidth(): Int

    @JvmStatic
    external fun nativeGetFrameHeight(): Int

    @JvmStatic
    external fun nativeGetFps(): Double

    @JvmStatic
    external fun nativeGetSampleRate(): Double

    // ---- Input button constants ----
    const val BTN_B        = 1 shl 0  // Fire / B
    const val BTN_Y        = 1 shl 1
    const val BTN_SELECT   = 1 shl 2
    const val BTN_START    = 1 shl 3
    const val BTN_UP       = 1 shl 4
    const val BTN_DOWN     = 1 shl 5
    const val BTN_LEFT     = 1 shl 6
    const val BTN_RIGHT    = 1 shl 7
    const val BTN_A        = 1 shl 8  // Fire alt / A
    const val BTN_X        = 1 shl 9
    const val BTN_L        = 1 shl 10 // OPTION
    const val BTN_R        = 1 shl 11 // RESET
}
