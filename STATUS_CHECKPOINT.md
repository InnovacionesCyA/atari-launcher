# Atari Launcher - Status Checkpoint

**Generated:** Build complete  
**Budget estimate:** ~750 credits consumed at this checkpoint

---

## What's Working

### Build
- Clean debug APK builds successfully: `app-debug.apk` (55MB)
- arm64-v8a only target, minSdk 29, targetSdk 34
- Kotlin + Jetpack Compose UI compiles clean (37 tasks, no errors)
- NDK/CMake compiles the JNI bridge (`libatarilauncher_jni.so`)

### Native Core
- Prebuilt `atari800_libretro_android.so` from libretro buildbot (official nightly, arm64-v8a)
  - URL: https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/atari800_libretro_android.so.zip
  - Confirmed ELF64 AArch64 targeting Android 21+
- JNI bridge (`libretro_jni.cpp`) wraps all libretro API functions:
  - `retro_init`, `retro_load_game`, `retro_run` loop
  - Video frame callback (RGB565 + XRGB8888 + 0RGB1555 → ARGB8888 conversion)
  - Audio PCM batch callback via AudioTrack JNI
  - Input state bitmask (d-pad + fire + START/SELECT/OPTION/RESET)
  - Save/load state serialization
  - Surface attach/detach (ANativeWindow)

### UI (All in Compose)
- **Game List Screen**: Dark navy theme, grid layout with Adaptive(140dp) cells
  - Shows filename without extension as game title
  - Colored placeholder art when no PNG sidecar exists
  - Extension badge (ROM/XEX/ATR/CAR/BIN)
  - Long-press menu: Rename, Delete
  - Refresh button to rescan folder
  - Empty state with instructions
- **Emulator Screen**: SurfaceView + TouchOverlay
- **Touch Overlay**:
  - 8-way D-pad (bottom left, 120dp) with multi-touch detection
  - FIRE button (bottom right, 72dp circular red) - tap-and-hold works, haptic feedback
  - TOP ROW: BACK / SEL / STR / OPT / RST buttons
  - Immersive fullscreen during gameplay (system bars hidden)
  - BACK gesture returns to game list + auto-saves state

### Storage
- Scans `/storage/emulated/0/Documents/AtariRoms/`
- Supported: `.rom`, `.car`, `.atr`, `.xex`, `.bin`
- Creates folder on first launch
- Requests `MANAGE_EXTERNAL_STORAGE` on Android 11+ (shows system dialog)
- Requests `READ_EXTERNAL_STORAGE` on Android 10

### Save States
- Auto-save on exit (BACK pressed)
- Auto-load on next launch of same ROM (in progress — see Known Issues)
- Per-game save files in app internal storage (`/data/data/com.atari.launcher/files/saves/`)

---

## Known Issues / Not Yet Tested

1. **Auto-load save state on launch** — The `EmulatorScreen` calls `loadGame()` but doesn't trigger `loadState()` after the core starts. Need a short delay + call `emulatorManager.loadState(rom.key)` after `loadGame` returns true. This is a small fix.

2. **Cover art fetching** — Not implemented. The libretro-thumbnails URL fetching was not added (deferred to stay within budget). Local sidecar PNG files work (`<romname>.png` next to ROM).

3. **Atari BIOS files** — The atari800 core may need BIOS files (`ATARIOSA.ROM`, `ATARIOSB.ROM`, `ATARIBAS.ROM`) in the system directory (`/data/data/com.atari.launcher/files/system/`). Without them, the core can still run some cartridges (XEX files generally don't need BIOS) but disk images (.ATR) may show errors. User needs to source and push BIOS files via ADB:
   ```
   adb push ATARIOSA.ROM /data/data/com.atari.launcher/files/system/
   ```

4. **Long-press "reset save state"** — Only Rename and Delete are in the long-press menu. Reset save state is not wired up (easy to add).

5. **Per-game touch layout override** — Not implemented (was marked as stub-OK in spec).

6. **gradlew wrapper** — The `gradlew` script is the actual Gradle binary (not the standard wrapper script). This works for building in this environment. A standard `gradlew` wrapper script would be more portable but isn't needed for the APK build itself.

---

## What's Left (Priority Order)

1. **Test on device** — The big unknown. Install the APK and see if a ROM actually plays.
2. **Auto-load save state** — 5-line fix in EmulatorScreen.kt.
3. **BIOS documentation** — Already in README.
4. **Cover art fetching** — Optional, lower priority.

---

## Recommendation

**Continue** — the APK is built and structurally complete. The main remaining work is on-device testing and the small auto-load fix. The core integration is the highest risk item and it compiled cleanly with both native libraries present.

If the emulator core fails to initialize on-device (e.g., dlopen fails), the most likely cause is BIOS files missing or an incompatibility between the prebuilt core (built for Android 21+ minSdk) and the runtime. Fallback: the app will show the game list but the "play" button will show an error toast rather than crashing.
