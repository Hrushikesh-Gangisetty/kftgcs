package com.example.kftgcs.videorelay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable control panel for the Video Relay pipeline.
 *
 * Displays configuration fields (listen port, destination IP, destination port),
 * a start/stop toggle, and live relay statistics.
 *
 * This is a fully self-contained, new UI component that does NOT modify any existing screens.
 * It can be embedded into any existing Compose layout by simply calling VideoRelayControlPanel().
 */
@Composable
fun VideoRelayControlPanel(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Load saved config or defaults
    val savedConfig = remember { VideoRelayConfig.fromPreferences(context) }

    // Editable fields
    var listenPort by remember { mutableStateOf(savedConfig.listenPort.toString()) }
    var destIp by remember { mutableStateOf(savedConfig.destIp) }
    var destPort by remember { mutableStateOf(savedConfig.destPort.toString()) }

    // Observe relay state
    val isRunning by VideoRelayManager.isRunning.collectAsState()
    val stats by VideoRelayManager.relayStats.collectAsState()
    val forwarderState by VideoRelayManager.forwarderState.collectAsState()

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1A2E)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Title
            Text(
                text = "📡 Video Relay",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val statusColor = when (forwarderState) {
                    VideoForwarder.ForwarderState.RUNNING -> Color(0xFF4CAF50) // Green
                    VideoForwarder.ForwarderState.STARTING -> Color(0xFFFFC107) // Yellow
                    VideoForwarder.ForwarderState.ERROR -> Color(0xFFF44336) // Red
                    VideoForwarder.ForwarderState.STOPPING -> Color(0xFFFFC107) // Yellow
                    VideoForwarder.ForwarderState.STOPPED -> Color(0xFF9E9E9E) // Gray
                }
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(statusColor, RoundedCornerShape(6.dp))
                )
                Text(
                    text = forwarderState.name,
                    fontSize = 14.sp,
                    color = statusColor
                )
            }

            // Configuration fields (disabled when running)
            OutlinedTextField(
                value = listenPort,
                onValueChange = { listenPort = it.filter { c -> c.isDigit() } },
                label = { Text("Listen Port (UDP)", color = Color.Gray) },
                enabled = !isRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFF444444),
                    disabledBorderColor = Color(0xFF333333)
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = destIp,
                onValueChange = { destIp = it },
                label = { Text("Destination IP (GCS)", color = Color.Gray) },
                enabled = !isRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFF444444),
                    disabledBorderColor = Color(0xFF333333)
                ),
                singleLine = true
            )

            OutlinedTextField(
                value = destPort,
                onValueChange = { destPort = it.filter { c -> c.isDigit() } },
                label = { Text("Destination Port (UDP)", color = Color.Gray) },
                enabled = !isRunning,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = Color.Gray,
                    focusedBorderColor = Color(0xFF1E88E5),
                    unfocusedBorderColor = Color(0xFF444444),
                    disabledBorderColor = Color(0xFF333333)
                ),
                singleLine = true
            )

            // Start / Stop button
            Button(
                onClick = {
                    if (isRunning) {
                        VideoRelayManager.stop(context)
                    } else {
                        val config = VideoRelayConfig(
                            listenPort = listenPort.toIntOrNull()
                                ?: VideoRelayConfig.DEFAULT_LISTEN_PORT,
                            destIp = destIp.ifBlank { VideoRelayConfig.DEFAULT_DEST_IP },
                            destPort = destPort.toIntOrNull()
                                ?: VideoRelayConfig.DEFAULT_DEST_PORT
                        )
                        VideoRelayManager.start(context, config)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFF44336) else Color(0xFF4CAF50)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = if (isRunning) "⏹ Stop Relay" else "▶ Start Relay",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Live statistics (only shown when running or recently stopped with data)
            if (stats.packetsForwarded > 0 || isRunning) {
                HorizontalDivider(color = Color(0xFF333333))

                Text(
                    text = "Relay Statistics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFBBBBBB)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StatItem("Packets", stats.packetsForwarded.toString())
                    StatItem("Bytes", formatBytes(stats.bytesForwarded))
                    StatItem("Errors", stats.errorCount.toString())
                }

                if (stats.lastPacketTimestamp > 0) {
                    val elapsed = (System.currentTimeMillis() - stats.lastPacketTimestamp) / 1000
                    Text(
                        text = "Last packet: ${elapsed}s ago",
                        fontSize = 12.sp,
                        color = Color(0xFF888888)
                    )
                }

                stats.lastError?.let { error ->
                    Text(
                        text = "Last error: $error",
                        fontSize = 12.sp,
                        color = Color(0xFFF44336)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color(0xFF888888)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "${bytes}B"
        bytes < 1024 * 1024 -> "${bytes / 1024}KB"
        bytes < 1024 * 1024 * 1024 -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2fGB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

