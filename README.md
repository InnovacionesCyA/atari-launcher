# Atari 8-bit Launcher

## 📲 Download APK

**[Tap here to download app-debug.apk](https://github.com/InnovacionesCyA/atari-launcher/raw/main/app-debug.apk)** (55 MB)

Open this link on your Android device, then tap the downloaded file to install.

---

A personal Android launcher for Atari 8-bit (400/800) ROMs. Sideloadable debug APK, built for Pixel 10 XL Pro.

---

## Install Instructions

### Requirements
- Android 10+ (minSdk 29), arm64-v8a device (Pixel 10 XL Pro works perfectly)
- USB debugging or a file manager that can install APKs

### Install via ADB
```bash
adb install app-debug.apk
```

### Install via file manager
1. Copy `app-debug.apk` to your phone (USB, Google Drive, etc.)
2. Open a file manager, navigate to the APK, tap to install
3. If prompted "Install unknown apps" — enable for your file manager

### First Launch
- The app will ask for **All files access** permission (Android 11+). Tap **Allow** on the system screen.
- Without this permission, the app cannot read `/Documents/AtariRoms/`.

---

## Where to Drop ROMs

```
/storage/emulated/0/Documents/AtariRoms/
```

Connect your Pixel to your Mac via USB, select "File Transfer" mode, and copy files into:
```
Internal Storage > Documents > AtariRoms
```

**Supported formats:** `.rom` `.car` `.atr` `.xex` `.bin`

The app creates this folder automatically on first launch if it doesn't exist.

---

## BIOS Files (Important!)

The atari800 core may need Atari OS ROM files for disk images (`.atr`) and some cartridges. XEX files (executables) often work without BIOS.

Push BIOS files to the app's system directory via ADB:
```bash
adb push ATARIOSA.ROM  /data/data/com.atari.launcher/files/system/
adb push ATARIOSB.ROM  /data/data/com.atari.launcher/files/system/
adb push ATARIBAS.ROM  /data/data/com.atari.launcher/files/system/
```

You can find the official Atari BIOS files at: https://atari800.github.io/download.html  
(Download the atari800 source package — BIOS files are not included here as they are copyrighted.)

---

## How to Use

1. Drop your ROM files into `/Documents/AtariRoms/`
2. Open "Atari Launcher"
3. Grant "All files access" permission
4. Your games appear as a grid — tap any game to play
5. **BACK** (top-left BACK button, or Android back gesture) returns to the game list
6. Save states are auto-saved when you exit and auto-loaded when you return

---

## Controls

### Emulator Screen

| Control | Location | Action |
|---------|----------|--------|
| D-Pad | Bottom left | Joystick directions |
| FIRE | Bottom right (red circle) | Fire button / tap-and-hold |
| SEL | Top row | SELECT key |
| STR | Top row | START key |
| OPT | Top row | OPTION key |
| RST | Top row | RESET |
| BACK | Top row (red) | Exit emulator → game list |

The overlay is semi-transparent at ~55% opacity during gameplay.

### Save States
- Auto-save: triggered when you press BACK
- Auto-load: triggered 300ms after game starts (if a save exists)
- Save files location: `/data/data/com.atari.launcher/files/saves/<gamename>.state`

---

## Cover Art

**Local sidecar files** (highest priority): Place a PNG with the same base name as your ROM file in the same folder:
```
/Documents/AtariRoms/Pac-Man.xex
/Documents/AtariRoms/Pac-Man.png    ← this is used as cover art
```

**No art**: The app shows a colored placeholder with the first two letters of the game name.

**Auto-fetch from libretro-thumbnails**: Not implemented in v1.

---

## Long-Press Menu

Long-press any game in the list for:
- **Rename** — renames the ROM file (or saves a display name if rename fails)
- **Delete** — deletes the ROM file (with confirmation)

---

## Known Issues

1. **BIOS required for disk images (.ATR)** — Without BIOS files, `.atr` disk images may show a black screen or error. See BIOS section above.

2. **"All files access" permission** — Android will open the system settings screen. You need to manually toggle "Allow all file access" for "Atari Launcher". This is a one-time setup.

3. **Cold start is slow** — First launch takes a few seconds to initialize the emulation core and scan ROMs.

4. **No cover art auto-fetching** — Must use local sidecar PNG files or live with the colored placeholder.

5. **Per-game touch layout override** — Stubbed, not implemented. All games use the same control layout.

6. **Reset save state** — Not in the long-press menu (delete the .state file manually if needed via ADB).

7. **Landscape only** — The app is locked to landscape orientation.

---

## Technical Details

| Item | Value |
|------|-------|
| Emulation core | atari800 libretro (official nightly build, arm64-v8a) |
| Core source | https://github.com/libretro/libretro-atari800 |
| Core binary | https://buildbot.libretro.com/nightly/android/latest/arm64-v8a/ |
| UI framework | Kotlin + Jetpack Compose |
| Audio | AudioTrack (PCM 16-bit stereo) |
| Video | SurfaceView + ANativeWindow |
| Target | arm64-v8a, minSdk 29, targetSdk 34 |
| APK size | ~55MB (870KB is the core, rest is Compose runtime) |

---

## Project Structure

```
atari_launcher/
├── app-debug.apk                    ← Install this
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── cpp/
│       │   ├── CMakeLists.txt
│       │   └── libretro_jni.cpp     ← JNI bridge to libretro API
│       ├── jniLibs/arm64-v8a/
│       │   └── atari800_libretro_android.so
│       └── kotlin/com/atari/launcher/
│           ├── MainActivity.kt      ← Navigation + permissions
│           ├── data/                ← ROM scanning, ViewModel
│           ├── emulator/            ← EmulatorBridge (JNI), EmulatorManager
│           └── ui/                  ← Screens + TouchOverlay
├── README.md
└── STATUS_CHECKPOINT.md
```
