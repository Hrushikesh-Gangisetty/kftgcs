package com.example.kftgcs.parammanagement

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
private val BmCard      = Color(0xFF1E2D6B)
private val BmCardAlt   = Color(0xFF162356)
private val BmAccent    = Color(0xFF3A6BD5)
private val BmAccentLt  = Color(0xFF87CEEB)
private val BmGreen     = Color(0xFF38A169)
private val BmRed       = Color(0xFFE53935)
private val BmAmber     = Color(0xFFED8936)
private val BmTextW     = Color.White
private val BmTextMuted = Color.White.copy(alpha = 0.55f)
private val BmDivider   = Color.White.copy(alpha = 0.12f)
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
    val ctx = LocalContext.current

    // ── Local pending edits (initialised when loaded values arrive) ───
    var pendingMonitor    by remember(state.loadedMonitor)     { mutableStateOf(state.loadedMonitor) }
    var pendingCapacity   by remember(state.loadedCapacityMah) { mutableStateOf(state.loadedCapacityMah?.toString() ?: "") }
    var pendingVoltMult   by remember(state.loadedVoltMult)    { mutableStateOf(state.loadedVoltMult) }
    var pendingAmpPerVolt by remember(state.loadedAmpPerVolt)  { mutableStateOf(state.loadedAmpPerVolt) }
    var pendingVoltPin    by remember(state.loadedVoltPin)     { mutableStateOf(state.loadedVoltPin) }
    var pendingCurrPin    by remember(state.loadedCurrPin)     { mutableStateOf(state.loadedCurrPin) }

    // Text mirrors so the user can type partial decimals like "12." without losing the field
    var pendingVoltMultText by remember(state.loadedVoltMult) {
        mutableStateOf(state.loadedVoltMult?.let { "%.6f".format(it) } ?: "")
    }
    var pendingAmpPerVoltText by remember(state.loadedAmpPerVolt) {
        mutableStateOf(state.loadedAmpPerVolt?.let { "%.6f".format(it) } ?: "")
    }

    // Dropdowns auto-derive from pending values, so loading from drone or typing into the
    // editable mult/amp-per-volt fields keeps the Sensor / HW Ver labels in sync.
    val selectedSensor by remember(pendingVoltMult, pendingAmpPerVolt) {
        derivedStateOf { findSensorPresetFor(pendingVoltMult, pendingAmpPerVolt) }
    }
    val selectedHwVer by remember(pendingVoltPin, pendingCurrPin) {
        derivedStateOf { findHwVerPresetFor(pendingVoltPin, pendingCurrPin) }
    }

    // ── Auto-load on connect ─────────────────────────────────────────
    LaunchedEffect(state.isDroneConnected) {
        if (state.isDroneConnected && state.loadedMonitor == null && !state.isLoading) {
            viewModel.loadFromDrone()
        }
    }

    // ── Toast feedback ───────────────────────────────────────────────
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            Toast.makeText(ctx, "✅ $it", Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(ctx, "⚠ $it", Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Battery Monitor Settings", color = BmTextW, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("BATT_MONITOR / VOLT_MULT / AMP_PERVLT calibration",
                            color = BmAccentLt, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = BmTextW)
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadFromDrone() },
                        enabled = state.isDroneConnected && !state.isLoading
                    ) {
                        Icon(
                            Icons.Filled.Download, "Read from drone",
                            tint = if (state.isDroneConnected && !state.isLoading) BmTextW
                            else BmTextW.copy(alpha = 0.35f)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoBanner()

            if (!state.isDroneConnected) {
                WarningCard(
                    "Drone not connected. Connect first to read or write battery monitor parameters."
                )
            }

            if (state.isLoading) {
                LoadingRow("Reading BATT_* parameters from drone…")
            }

            // ── Section 1: Configuration dropdowns ─────────────────────
            SectionCard(title = "Configuration", color = BmCard) {
                ConfigDropdownRow(
                    label = "Monitor",
                    selectedLabel = BATT_MONITOR_OPTIONS.find { it.key == pendingMonitor }?.label
                        ?: pendingMonitor?.let { "$it: (custom)" }
                        ?: "—",
                    items = BATT_MONITOR_OPTIONS.map { it.label },
                    onSelectIndex = { idx -> pendingMonitor = BATT_MONITOR_OPTIONS[idx].key }
                )

                Spacer(Modifier.height(10.dp))

                ConfigDropdownRow(
                    label = "Sensor",
                    selectedLabel = selectedSensor.label,
                    items = SENSOR_PRESETS.map { it.label },
                    onSelectIndex = { idx ->
                        val p = SENSOR_PRESETS[idx]
                        p.voltMult?.let {
                            pendingVoltMult = it
                            pendingVoltMultText = "%.6f".format(it)
                        }
                        p.ampPerVolt?.let {
                            pendingAmpPerVolt = it
                            pendingAmpPerVoltText = "%.6f".format(it)
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                ConfigDropdownRow(
                    label = "HW Ver",
                    selectedLabel = selectedHwVer.label,
                    items = HW_VER_PRESETS.map { it.label },
                    onSelectIndex = { idx ->
                        val p = HW_VER_PRESETS[idx]
                        p.voltPin?.let { pendingVoltPin = it }
                        p.currPin?.let { pendingCurrPin = it }
                    }
                )

                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = BmDivider)
                Spacer(Modifier.height(12.dp))

                LabeledNumberField(
                    label = "Battery Capacity",
                    suffix = "mAh",
                    value = pendingCapacity,
                    onValueChange = { pendingCapacity = it.filter(Char::isDigit) },
                    keyboardType = KeyboardType.Number
                )

                Spacer(Modifier.height(8.dp))

                // ADC pins stay read-only (set by HW Ver preset)
                ResolvedRow("BATT_VOLT_PIN", pendingVoltPin?.toString() ?: "—")
                ResolvedRow("BATT_CURR_PIN", pendingCurrPin?.toString() ?: "—")

                Spacer(Modifier.height(10.dp))

                MultiplierField(
                    label = "BATT_VOLT_MULT",
                    value = pendingVoltMultText,
                    onValueChange = { raw ->
                        val sanitized = sanitizeDecimalString(raw)
                        pendingVoltMultText = sanitized
                        pendingVoltMult = sanitized.toFloatOrNull()
                    }
                )

                Spacer(Modifier.height(10.dp))

                MultiplierField(
                    label = "BATT_AMP_PERVLT",
                    value = pendingAmpPerVoltText,
                    onValueChange = { raw ->
                        val sanitized = sanitizeDecimalString(raw)
                        pendingAmpPerVoltText = sanitized
                        pendingAmpPerVolt = sanitized.toFloatOrNull()
                    }
                )
            }

            // ── Section 2: Voltage calibration ─────────────────────────
            SectionCard(title = "Voltage Calibration", color = BmCardAlt, icon = Icons.Filled.Bolt) {
                LiveValueRow(
                    label = "Battery Voltage Calced",
                    value = state.measuredVoltageLive?.let { "%.2f V".format(it) } ?: "— V",
                    helper = "Live from telemetry (BATT)"
                )

                Spacer(Modifier.height(10.dp))

                MultiplierField(
                    label = "BATT_VOLT_MULT",
                    value = pendingVoltMultText,
                    onValueChange = { raw ->
                        val sanitized = sanitizeDecimalString(raw)
                        pendingVoltMultText = sanitized
                        pendingVoltMult = sanitized.toFloatOrNull()
                    }
                )

                Spacer(Modifier.height(10.dp))

                LabeledNumberField(
                    label = "Measured Battery Voltage",
                    suffix = "V",
                    value = state.measuredVoltageInput,
                    onValueChange = viewModel::updateMeasuredVoltageInput,
                    keyboardType = KeyboardType.Decimal,
                    helper = "Read with a multimeter at the battery terminals"
                )

                Spacer(Modifier.height(10.dp))

                CalculatedRow(
                    label = "Voltage Divider Calced",
                    value = state.calculatedVoltageDivider?.let { "%.6f".format(it) } ?: "—",
                    onApply = state.calculatedVoltageDivider?.let { calc ->
                        {
                            pendingVoltMult = calc
                            pendingVoltMultText = "%.6f".format(calc)
                        }
                    }
                )
            }

            // ── Section 3: Current calibration ─────────────────────────
            SectionCard(title = "Current Calibration", color = BmCard, icon = Icons.Filled.BatteryFull) {
                LiveValueRow(
                    label = "Current Calced",
                    value = state.measuredCurrentLive?.let { "%.2f A".format(it) } ?: "— A",
                    helper = "Live from telemetry (BATT)"
                )

                Spacer(Modifier.height(10.dp))

                MultiplierField(
                    label = "BATT_AMP_PERVLT",
                    value = pendingAmpPerVoltText,
                    onValueChange = { raw ->
                        val sanitized = sanitizeDecimalString(raw)
                        pendingAmpPerVoltText = sanitized
                        pendingAmpPerVolt = sanitized.toFloatOrNull()
                    }
                )
            }

            // ── Save button ────────────────────────────────────────────
            val isDirty =
                pendingMonitor != state.loadedMonitor ||
                pendingCapacity.toIntOrNull() != state.loadedCapacityMah ||
                !floatsClose(pendingVoltMult,   state.loadedVoltMult) ||
                !floatsClose(pendingAmpPerVolt, state.loadedAmpPerVolt) ||
                pendingVoltPin != state.loadedVoltPin ||
                pendingCurrPin != state.loadedCurrPin

            Button(
                onClick = {
                    viewModel.saveToDrone(
                        monitor     = pendingMonitor,
                        capacityMah = pendingCapacity.toIntOrNull(),
                        voltMult    = pendingVoltMult,
                        ampPerVolt  = pendingAmpPerVolt,
                        voltPin     = pendingVoltPin,
                        currPin     = pendingCurrPin
                    )
                },
                enabled = state.isDroneConnected && !state.isSaving && !state.isLoading && isDirty,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDirty) BmGreen else BmAccent.copy(alpha = 0.5f),
                    disabledContainerColor = BmAccent.copy(alpha = 0.25f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Saving…", fontWeight = FontWeight.SemiBold)
                } else {
                    Icon(Icons.Filled.Save, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isDirty) "Save & Write to Drone" else "No changes",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Building blocks
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun InfoBanner() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BmAccent.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Battery Monitor (BATT_*)", color = BmAccentLt,
                fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                "Set the monitor type and pick a sensor / HW preset to fill ADC pins and " +
                "default multipliers. To calibrate, enter the voltage/current you measure with " +
                "a meter — the new BATT_VOLT_MULT and BATT_AMP_PERVLT are computed live. " +
                "Tap Save to write all changes to the drone.",
                color = BmTextW.copy(alpha = 0.75f), fontSize = 11.sp, lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun WarningCard(text: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BmRed.copy(alpha = 0.15f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⚠", fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(text, color = BmRed, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = BmCard),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = BmAccentLt,
                strokeWidth = 2.5.dp
            )
            Text(text, color = BmTextW, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = BmAccentLt, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(title, color = BmTextW, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = BmDivider)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun ConfigDropdownRow(
    label: String,
    selectedLabel: String,
    items: List<String>,
    onSelectIndex: (Int) -> Unit
) {
    Column {
        Text(label, color = BmAccentLt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        DropdownTrigger(
            selectedLabel = selectedLabel,
            items = items,
            onSelectIndex = onSelectIndex
        )
    }
}

@Composable
private fun DropdownTrigger(
    selectedLabel: String,
    items: List<String>,
    onSelectIndex: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BmFieldBg)
                .border(1.dp, BmAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                selectedLabel,
                color = BmTextW, fontWeight = FontWeight.Medium, fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ArrowDropDown, null, tint = BmAccentLt)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color(0xFF1A237E), RoundedCornerShape(12.dp))
                .border(0.5.dp, BmAccent.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
        ) {
            items.forEachIndexed { idx, label ->
                val isSel = label == selectedLabel
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontSize = 13.sp,
                            color = if (isSel) BmAccentLt else BmTextW,
                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    onClick = {
                        onSelectIndex(idx)
                        expanded = false
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) BmAccent.copy(alpha = 0.18f) else Color.Transparent)
                )
            }
        }
    }
}

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
        Text(label, color = BmAccentLt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(BmFieldBg)
                .border(1.dp, BmAccent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
                modifier = Modifier.weight(1f)
            )
            Text(
                suffix, color = BmTextMuted, fontSize = 12.sp,
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        if (helper != null) {
            Spacer(Modifier.height(4.dp))
            Text(helper, color = BmTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LiveValueRow(label: String, value: String, helper: String? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = BmAccentLt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f))
            Text(
                value, color = BmTextW, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
        }
        if (helper != null) {
            Spacer(Modifier.height(2.dp))
            Text(helper, color = BmTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CalculatedRow(
    label: String,
    value: String,
    onApply: (() -> Unit)?
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BmAccent.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Calculate, null, tint = BmAccentLt, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(label, color = BmAccentLt, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                value, color = BmTextW, fontSize = 14.sp,
                fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
            )
        }
        if (onApply != null) {
            TextButton(
                onClick = onApply,
                colors = ButtonDefaults.textButtonColors(contentColor = BmAccentLt)
            ) {
                Text("Apply", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ResolvedRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label, color = BmTextMuted, fontSize = 11.sp,
            fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)
        )
        Text(
            value, color = BmTextW, fontSize = 12.sp,
            fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun MultiplierField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    LabeledNumberField(
        label = label,
        suffix = "",
        value = value,
        onValueChange = onValueChange,
        keyboardType = KeyboardType.Decimal,
        helper = "Up to 6 decimal places"
    )
}

// ─────────────────────────────────────────────────────────────────────
private fun floatsClose(a: Float?, b: Float?, eps: Float = 0.0001f): Boolean {
    if (a == null && b == null) return true
    if (a == null || b == null) return false
    return kotlin.math.abs(a - b) < eps
}
