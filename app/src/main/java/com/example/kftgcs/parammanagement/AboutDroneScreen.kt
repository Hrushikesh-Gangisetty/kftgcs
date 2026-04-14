package com.example.kftgcs.parammanagement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.launch

// ── Colours ──────────────────────────────────────────────────────────────────
private val BgDark     = Color(0xFF0D1B4B)
private val NavyBlue   = Color(0xFF1A237E)
private val CardBg     = Color(0xFF1E2D6B)
private val LabelGray  = Color(0xFFB0BEC5)
private val ValueWhite = Color.White
private val AccentBlue = Color(0xFF64B5F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutDroneScreen(
    navController: NavController,
    aboutDroneViewModel: AboutDroneViewModel
) {
    val droneInfo by aboutDroneViewModel.droneInfo.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-fetch frame params once when drone connects
    LaunchedEffect(droneInfo.isDroneConnected) {
        if (droneInfo.isDroneConnected) {
            aboutDroneViewModel.fetchFrameParams()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(text = "About Drone", color = Color.White, fontWeight = FontWeight.Bold)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { coroutineScope.launch { aboutDroneViewModel.fetchFrameParams() } },
                        enabled = droneInfo.isDroneConnected && !droneInfo.isLoadingParams
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            tint = if (droneInfo.isDroneConnected && !droneInfo.isLoadingParams)
                                Color.White else Color.White.copy(alpha = 0.3f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NavyBlue)
            )
        },
        containerColor = BgDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Connection banner ─────────────────────────────────────────
            if (!droneInfo.isDroneConnected) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A1010))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null,
                            tint = Color(0xFFEF9A9A), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Drone not connected. Connect via the main app to fetch live data.",
                            color = Color(0xFFEF9A9A), fontSize = 13.sp
                        )
                    }
                }
            }

            // ── Identification ────────────────────────────────────────────
            SectionHeader("Identification")
            DroneInfoCard {
                InfoRow(label = "FC ID",            value = droneInfo.fcId)
                InfoDivider()
                InfoRow(label = "Firmware Version", value = droneInfo.firmwareVersion)
            }

            // ── Frame Configuration ───────────────────────────────────────
            SectionHeader("Frame Configuration")
            DroneInfoCard {
                if (droneInfo.isLoadingParams) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = AccentBlue,
                                strokeWidth = 2.dp
                            )
                            Text("Fetching parameters…", color = LabelGray, fontSize = 14.sp)
                        }
                    }
                } else {
                    InfoRow(label = "Frame Class", value = droneInfo.frameClass)
                    InfoDivider()
                    InfoRow(label = "Frame Type",  value = droneInfo.frameType)
                }
            }

            // ── Error banner ──────────────────────────────────────────────
            droneInfo.paramsError?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF4A2800))
                ) {
                    Text(
                        text = "⚠ $error",
                        color = Color(0xFFFFCC80),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Private composable helpers ────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = AccentBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun DroneInfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            content = content
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = LabelGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        Text(text = value, color = ValueWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun InfoDivider() {
    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 0.5.dp)
}
