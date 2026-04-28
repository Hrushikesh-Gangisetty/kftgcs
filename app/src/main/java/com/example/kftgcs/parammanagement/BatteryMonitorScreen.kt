package com.example.kftgcs.parammanagement

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

// ─────────────────────────────────────────────────────────────────────
// Palette
// ─────────────────────────────────────────────────────────────────────
private val BmBg        = Color(0xFF0D1B4B)
private val BmTopBar    = Color(0xFF1A237E)
private val BmAccent    = Color(0xFF3A6BD5)
private val BmAccentLt  = Color(0xFF87CEEB)
private val BmTextW     = Color.White
private val BmTextMuted = Color.White.copy(alpha = 0.55f)
private val BmFieldBg   = Color.White.copy(alpha = 0.08f)

// ─────────────────────────────────────────────────────────────────────
// Screen
// ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryMonitorScreen(
    navController: NavController,
    viewModel: BatteryMonitorViewModel
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Battery", color = BmTextW, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BmTextW)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BmTopBar)
            )
        },
        containerColor = BmBg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LabeledNumberField(
                label = "Measured Battery Voltage",
                suffix = "V",
                value = state.measuredVoltageInput,
                onValueChange = viewModel::updateMeasuredVoltageInput,
                keyboardType = KeyboardType.Decimal,
                helper = "Read with a multimeter at the battery terminals"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Building blocks
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun LabeledNumberField(
    label: String,
    suffix: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType,
    helper: String? = null
) {
    Column {
        Text(label, color = BmAccentLt, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(BmFieldBg)
                .border(1.5.dp, BmAccent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(fontSize = 18.sp, color = BmTextW),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = BmTextW,
                    unfocusedTextColor = BmTextW,
                    cursorColor = BmAccentLt
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
            )
            Text(
                suffix, color = BmTextMuted, fontSize = 15.sp,
                modifier = Modifier.padding(end = 16.dp)
            )
        }
        if (helper != null) {
            Spacer(Modifier.height(6.dp))
            Text(helper, color = BmTextMuted, fontSize = 12.sp)
        }
    }
}
