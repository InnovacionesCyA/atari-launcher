package com.atari.launcher

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.atari.launcher.data.GameListViewModel
import com.atari.launcher.data.RomItem
import com.atari.launcher.emulator.EmulatorManager
import com.atari.launcher.ui.screens.EmulatorScreen
import com.atari.launcher.ui.screens.GameListScreen
import com.atari.launcher.ui.theme.AtariLauncherTheme

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val gameListViewModel: GameListViewModel by viewModels()
    private lateinit var emulatorManager: EmulatorManager

    // Permission launchers
    private val storagePermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        gameListViewModel.setStoragePermission(granted)
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkAndGrantStoragePermission()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        emulatorManager = EmulatorManager(this)

        checkAndGrantStoragePermission()

        setContent {
            AtariLauncherTheme {
                AppNavigation(
                    gameListViewModel = gameListViewModel,
                    emulatorManager = emulatorManager
                )
            }
        }
    }

    private fun checkAndGrantStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ - request MANAGE_EXTERNAL_STORAGE
            if (Environment.isExternalStorageManager()) {
                gameListViewModel.setStoragePermission(true)
            } else {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        } else {
            // Android 10 - READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED) {
                gameListViewModel.setStoragePermission(true)
            } else {
                storagePermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        emulatorManager.pause()
    }

    override fun onResume() {
        super.onResume()
        emulatorManager.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        emulatorManager.deinit()
    }
}

@Composable
fun AppNavigation(
    gameListViewModel: GameListViewModel,
    emulatorManager: EmulatorManager
) {
    var currentRom by remember { mutableStateOf<RomItem?>(null) }

    if (currentRom != null) {
        val rom = currentRom!!

        // Immersive fullscreen during gameplay
        ImmersiveMode()

        BackHandler {
            // Auto-save state on exit
            emulatorManager.saveState(rom.key)
            emulatorManager.stop()
            currentRom = null
        }

        EmulatorScreen(
            romPath = rom.file.absolutePath,
            emulatorManager = emulatorManager,
            onBack = {
                emulatorManager.saveState(rom.key)
                emulatorManager.stop()
                currentRom = null
            }
        )
    } else {
        // Re-show system UI when back in game list
        ShowSystemUI()

        GameListScreen(
            viewModel = gameListViewModel,
            onLaunchRom = { rom ->
                currentRom = rom
                // Auto-load save state if it exists
                // (load happens after game starts - handled in EmulatorScreen)
            }
        )
    }
}

@Composable
fun ImmersiveMode() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity ?: return

    DisposableEffect(Unit) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { /* restored by ShowSystemUI */ }
    }
}

@Composable
fun ShowSystemUI() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? ComponentActivity ?: return

    DisposableEffect(Unit) {
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }
}
