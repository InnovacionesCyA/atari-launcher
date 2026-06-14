package com.atari.launcher.ui.screens

import android.app.AlertDialog
import android.content.Context
import android.widget.EditText
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.atari.launcher.data.GameListViewModel
import com.atari.launcher.data.RomItem

@Composable
fun GameListScreen(
    viewModel: GameListViewModel,
    onLaunchRom: (RomItem) -> Unit
) {
    val roms by viewModel.roms.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var longPressedRom by remember { mutableStateOf<RomItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<RomItem?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A12))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Gamepad,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "ATARI 8-BIT",
                            color = Color(0xFF00D4FF),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        Text(
                            "${roms.size} games",
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                }
                IconButton(onClick = { viewModel.scanRoms() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Rescan",
                        tint = Color(0xFF888888)
                    )
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFF00D4FF))
                }
            } else if (roms.isEmpty()) {
                EmptyState()
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(roms, key = { it.key }) { rom ->
                        RomCard(
                            rom = rom,
                            onClick = { onLaunchRom(rom) },
                            onLongClick = { longPressedRom = rom }
                        )
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF00D4FF)
            )
        }
    }

    // Long-press menu
    longPressedRom?.let { rom ->
        LongPressMenu(
            rom = rom,
            onDismiss = { longPressedRom = null },
            onRename = {
                longPressedRom = null
                showRenameDialog(context, rom) { newName ->
                    viewModel.renameGame(rom, newName)
                }
            },
            onDelete = {
                longPressedRom = null
                showDeleteConfirm = rom
            }
        )
    }

    // Delete confirmation
    showDeleteConfirm?.let { rom ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete ${rom.displayName}?", color = Color.White) },
            text = { Text("This will delete the ROM file permanently.", color = Color(0xFFAAAAAA)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGame(rom)
                    showDeleteConfirm = null
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Delete", color = Color(0xFFFF5555))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("Cancel", color = Color(0xFF888888))
                }
            },
            containerColor = Color(0xFF1A1A2E)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RomCard(
    rom: RomItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.75f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Cover art area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(Color(0xFF0D0D1A)),
                contentAlignment = Alignment.Center
            ) {
                val artSource = rom.artFile ?: rom.artUrl?.let { it }
                if (artSource != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(artSource)
                            .crossfade(true)
                            .build(),
                        contentDescription = rom.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Placeholder - Atari-style colored block
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(atariColorForName(rom.displayName)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = rom.displayName.take(2).uppercase(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Extension badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .background(Color(0x99000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = rom.extension.uppercase(),
                        color = Color(0xFF00D4FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A2E))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = rom.displayName,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LongPressMenu(
    rom: RomItem,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(rom.displayName, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                TextButton(
                    onClick = onRename,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Rename", color = Color(0xFF00D4FF))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete", color = Color(0xFFFF5555))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF888888))
            }
        },
        containerColor = Color(0xFF1A1A2E)
    )
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Gamepad,
                contentDescription = null,
                tint = Color(0xFF333355),
                modifier = Modifier.size(80.dp)
            )
            Text(
                "No ROMs found",
                color = Color(0xFF888888),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "Drop .rom, .atr, .xex, .car, or .bin files\ninto /Documents/AtariRoms/",
                color = Color(0xFF555577),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        }
    }
}

private fun atariColorForName(name: String): Color {
    val colors = listOf(
        Color(0xFF1A237E), Color(0xFF4A148C), Color(0xFF880E4F),
        Color(0xFF1B5E20), Color(0xFF0D47A1), Color(0xFF004D40),
        Color(0xFF3E2723), Color(0xFF01579B), Color(0xFF006064)
    )
    return colors[name.hashCode().and(0x7FFFFFFF) % colors.size]
}

private fun showRenameDialog(context: Context, rom: RomItem, onConfirm: (String) -> Unit) {
    val editText = EditText(context).apply {
        setText(rom.displayName)
        selectAll()
        setPadding(48, 24, 48, 24)
    }
    AlertDialog.Builder(context)
        .setTitle("Rename Game")
        .setView(editText)
        .setPositiveButton("Rename") { _, _ ->
            val newName = editText.text.toString().trim()
            if (newName.isNotEmpty()) onConfirm(newName)
        }
        .setNegativeButton("Cancel", null)
        .show()
}
