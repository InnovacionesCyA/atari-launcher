package com.atari.launcher.ui.screens

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.atari.launcher.emulator.EmulatorManager
import com.atari.launcher.ui.components.TouchOverlay
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun EmulatorScreen(
    romPath: String,
    emulatorManager: EmulatorManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var overlayVisible by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // SurfaceView for emulator rendering
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            emulatorManager.setSurface(holder.surface)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            // Surface size changed - the core will adapt
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            emulatorManager.setSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Touch overlay (d-pad + buttons)
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            TouchOverlay(
                emulatorManager = emulatorManager,
                onBack = onBack,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    // Load game once on first composition, then auto-load save state
    LaunchedEffect(romPath) {
        val ok = emulatorManager.loadGame(romPath)
        if (ok) {
            // Give the core a moment to fully start before loading state
            delay(300)
            val gameKey = File(romPath).nameWithoutExtension
                .replace(Regex("[^a-zA-Z0-9_-]"), "_")
            emulatorManager.loadState(gameKey)
        }
    }
}
