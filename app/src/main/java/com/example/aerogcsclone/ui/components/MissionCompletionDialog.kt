package com.example.aerogcsclone.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.aerogcsclone.utils.AppStrings

/**
 * Dialog shown when a mission is completed.
 * Displays mission statistics and requires project/plot name input before dismissing.
 */
@Suppress("UNUSED_PARAMETER") // onDismiss kept for API consistency but dialog can't be dismissed without saving
@Composable
fun MissionCompletionDialog(
    totalTime: String,
    totalDistance: String,
    consumedLitres: String,
    initialProjectName: String = "",
    initialPlotName: String = "",
    onDismiss: () -> Unit,
    onSave: (projectName: String, plotName: String) -> Unit
) {
    var projectName by remember { mutableStateOf(initialProjectName) }
    var plotName by remember { mutableStateOf(initialPlotName) }
    var projectNameError by remember { mutableStateOf<String?>(null) }
    var plotNameError by remember { mutableStateOf<String?>(null) }

    // Update values when initial values change
    LaunchedEffect(initialProjectName, initialPlotName) {
        projectName = initialProjectName
        plotName = initialPlotName
    }

    // Check if both fields are filled
    val isOkEnabled = projectName.isNotBlank() && plotName.isNotBlank()

    Dialog(
        onDismissRequest = { /* Don't allow dismissing without saving */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Success Icon
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Mission Completed",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(64.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = AppStrings.missionCompleted,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Mission Statistics
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Total Time Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${AppStrings.totalTime}:",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = totalTime,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Total Distance Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${AppStrings.totalDistance}:",
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = totalDistance,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Consumed Litres Row (if available)
                        if (consumedLitres.isNotEmpty() && consumedLitres != "N/A") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${AppStrings.consumed}:",
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = consumedLitres,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Divider
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Input Section Title
                Text(
                    text = "Save Mission Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Project Name (Field Name) Input
                OutlinedTextField(
                    value = projectName,
                    onValueChange = {
                        projectName = it
                        projectNameError = null
                    },
                    label = { Text(AppStrings.projectName) },
                    placeholder = { Text("Enter project/field name") },
                    isError = projectNameError != null,
                    supportingText = projectNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Plot Name Input
                OutlinedTextField(
                    value = plotName,
                    onValueChange = {
                        plotName = it
                        plotNameError = null
                    },
                    label = { Text(AppStrings.plotName) },
                    placeholder = { Text("Enter plot name") },
                    isError = plotNameError != null,
                    supportingText = plotNameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Show helper text if fields are empty
                if (!isOkEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "* Both fields are required to save",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Start)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // OK Button
                Button(
                    onClick = {
                        // Validate inputs
                        var hasError = false

                        if (projectName.isBlank()) {
                            projectNameError = "Project name is required"
                            hasError = true
                        }

                        if (plotName.isBlank()) {
                            plotNameError = "Plot name is required"
                            hasError = true
                        }

                        if (!hasError) {
                            onSave(projectName.trim(), plotName.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    enabled = isOkEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isOkEnabled) Color(0xFF4CAF50) else Color.Gray,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = AppStrings.ok,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

