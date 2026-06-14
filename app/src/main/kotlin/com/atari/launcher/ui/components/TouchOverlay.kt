package com.atari.launcher.ui.components

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atari.launcher.emulator.EmulatorBridge
import com.atari.launcher.emulator.EmulatorManager
import androidx.core.content.ContextCompat
import android.content.Context
import kotlinx.coroutines.delay

private val OVERLAY_ALPHA = 0.55f
private val BTN_COLOR_ACTIVE = Color(0xFF00D4FF).copy(alpha = 0.8f)
private val BTN_COLOR_INACTIVE = Color(0xFFFFFFFF).copy(alpha = OVERLAY_ALPHA)
private val BTN_LABEL_COLOR = Color.White
private val DPADS_SIZE = 120.dp
private val FIRE_SIZE = 72.dp

@Composable
fun TouchOverlay(
    emulatorManager: EmulatorManager,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Bitmask of currently pressed buttons
    var inputMask by remember { mutableIntStateOf(0) }

    fun setButton(btn: Int, pressed: Boolean) {
        inputMask = if (pressed) inputMask or btn else inputMask and btn.inv()
        emulatorManager.setInputMask(inputMask)
    }

    fun vibrate() {
        val vib = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vib?.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    Box(modifier = modifier) {
        // ---- Top row: START / SELECT / OPTION / RESET / BACK ----
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SmallButton("BACK", Color(0xFFFF5555).copy(alpha = 0.7f)) {
                onBack()
            }
            SmallButton("SEL") {
                setButton(EmulatorBridge.BTN_SELECT, true)
                vibrate()
            }
            SmallButton("STR") {
                setButton(EmulatorBridge.BTN_START, true)
                vibrate()
            }
            SmallButton("OPT") {
                setButton(EmulatorBridge.BTN_L, true)
                vibrate()
            }
            SmallButton("RST") {
                setButton(EmulatorBridge.BTN_R, true)
                vibrate()
            }
        }

        // ---- D-Pad (bottom left) ----
        DPad(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 32.dp)
                .size(DPADS_SIZE),
            onDirectionChanged = { up, down, left, right ->
                var mask = inputMask
                mask = if (up) mask or EmulatorBridge.BTN_UP else mask and EmulatorBridge.BTN_UP.inv()
                mask = if (down) mask or EmulatorBridge.BTN_DOWN else mask and EmulatorBridge.BTN_DOWN.inv()
                mask = if (left) mask or EmulatorBridge.BTN_LEFT else mask and EmulatorBridge.BTN_LEFT.inv()
                mask = if (right) mask or EmulatorBridge.BTN_RIGHT else mask and EmulatorBridge.BTN_RIGHT.inv()
                inputMask = mask
                emulatorManager.setInputMask(mask)
            }
        )

        // ---- Fire button (bottom right) ----
        FireButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 36.dp, bottom = 48.dp)
                .size(FIRE_SIZE),
            onPressed = {
                setButton(EmulatorBridge.BTN_B, true)
                vibrate()
            },
            onReleased = {
                setButton(EmulatorBridge.BTN_B, false)
            }
        )
    }
}

// ---- D-Pad component ----
@Composable
fun DPad(
    modifier: Modifier = Modifier,
    onDirectionChanged: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit
) {
    var activeDir by remember { mutableStateOf(setOf<String>()) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val changes = event.changes
                        val dirs = mutableSetOf<String>()

                        for (change in changes) {
                            if (change.pressed) {
                                val pos = change.position
                                val cx = size.width / 2f
                                val cy = size.height / 2f
                                val dx = pos.x - cx
                                val dy = pos.y - cy
                                val deadzone = size.width * 0.2f

                                if (dy < -deadzone) dirs.add("UP")
                                if (dy > deadzone) dirs.add("DOWN")
                                if (dx < -deadzone) dirs.add("LEFT")
                                if (dx > deadzone) dirs.add("RIGHT")
                            }
                        }
                        if (dirs != activeDir) {
                            activeDir = dirs
                            onDirectionChanged(
                                "UP" in dirs,
                                "DOWN" in dirs,
                                "LEFT" in dirs,
                                "RIGHT" in dirs
                            )
                        }
                    }
                }
            }
    ) {
        // Cross shape
        val armFraction = 0.3f
        // Vertical arm
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(armFraction)
                .fillMaxHeight()
                .background(
                    if (activeDir.containsAny("UP", "DOWN")) BTN_COLOR_ACTIVE else BTN_COLOR_INACTIVE,
                    RoundedCornerShape(4.dp)
                )
        )
        // Horizontal arm
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .fillMaxHeight(armFraction)
                .background(
                    if (activeDir.containsAny("LEFT", "RIGHT")) BTN_COLOR_ACTIVE else BTN_COLOR_INACTIVE,
                    RoundedCornerShape(4.dp)
                )
        )
        // Direction labels
        Text("▲", color = BTN_LABEL_COLOR.copy(alpha = 0.8f), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
        Text("▼", color = BTN_LABEL_COLOR.copy(alpha = 0.8f), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
        Text("◀", color = BTN_LABEL_COLOR.copy(alpha = 0.8f), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp))
        Text("▶", color = BTN_LABEL_COLOR.copy(alpha = 0.8f), fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp))
    }
}

// ---- Fire button ----
@Composable
fun FireButton(
    modifier: Modifier = Modifier,
    onPressed: () -> Unit,
    onReleased: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                if (pressed) BTN_COLOR_ACTIVE else Color(0xFFCC2200).copy(alpha = OVERLAY_ALPHA),
                CircleShape
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed != pressed) {
                            pressed = anyPressed
                            if (anyPressed) onPressed() else onReleased()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "FIRE",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

// ---- Small top button ----
@Composable
fun SmallButton(
    label: String,
    color: Color = BTN_COLOR_INACTIVE,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(width = 52.dp, height = 32.dp)
            .background(
                if (pressed) BTN_COLOR_ACTIVE else color,
                RoundedCornerShape(6.dp)
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed && !pressed) {
                            pressed = true
                            onClick()
                        } else if (!anyPressed && pressed) {
                            pressed = false
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

private fun Set<String>.containsAny(vararg values: String) = values.any { it in this }
