package com.example.aerogcsclone.ui.components

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drone Camera Feed Overlay
 *
 * Shows a live camera feed from the drone in a resizable overlay window.
 * - Small mode: Picture-in-Picture style at the bottom-right corner
 * - Full mode: Expanded to fill the screen
 *
 * The video stream URL can be configured (RTSP, HTTP, or WebRTC).
 * When no stream is available, shows a placeholder with camera icon.
 */
@Composable
fun DroneCameraFeedOverlay(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    videoStreamUrl: String? = null,
    isConnected: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Reset offset when switching between expanded/collapsed
    LaunchedEffect(isExpanded) {
        offsetX = 0f
        offsetY = 0f
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // Animated sizes
    val targetWidth = if (isExpanded) screenWidthDp else 220.dp
    val targetHeight = if (isExpanded) screenHeightDp else 160.dp
    val animatedWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = tween(durationMillis = 300),
        label = "width"
    )
    val animatedHeight by animateDpAsState(
        targetValue = targetHeight,
        animationSpec = tween(durationMillis = 300),
        label = "height"
    )

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .zIndex(10f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = if (isExpanded) Alignment.Center else Alignment.BottomEnd
        ) {
            // Semi-transparent backdrop when expanded
            if (isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                )
            }

            Card(
                modifier = Modifier
                    .then(
                        if (!isExpanded) {
                            Modifier
                                .padding(end = 12.dp, bottom = 12.dp)
                                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                }
                        } else {
                            Modifier.padding(16.dp)
                        }
                    )
                    .width(animatedWidth)
                    .height(animatedHeight),
                shape = RoundedCornerShape(if (isExpanded) 16.dp else 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Video content area
                    if (videoStreamUrl != null && isConnected) {
                        // Live video stream via WebView
                        VideoStreamView(
                            url = videoStreamUrl,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Placeholder when no stream available
                        CameraPlaceholder(
                            isConnected = isConnected,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top control bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(topStart = if (isExpanded) 16.dp else 12.dp, topEnd = if (isExpanded) 16.dp else 12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .align(Alignment.TopCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Camera label with live indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Live indicator dot
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isConnected && videoStreamUrl != null) Color.Red
                                        else Color.Gray
                                    )
                            )
                            Text(
                                text = if (isConnected && videoStreamUrl != null) "LIVE" else "CAMERA",
                                color = Color.White,
                                fontSize = if (isExpanded) 14.sp else 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Control buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Expand/Collapse button
                            IconButton(
                                onClick = { isExpanded = !isExpanded },
                                modifier = Modifier.size(if (isExpanded) 36.dp else 28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isExpanded) "Minimize" else "Maximize",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isExpanded) 22.dp else 16.dp)
                                )
                            }

                            // Close button
                            IconButton(
                                onClick = {
                                    isExpanded = false
                                    onDismiss()
                                },
                                modifier = Modifier.size(if (isExpanded) 36.dp else 28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close Camera",
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isExpanded) 22.dp else 16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Placeholder shown when no camera stream is available
 */
@Composable
private fun CameraPlaceholder(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xFF0D0D1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.VideocamOff else Icons.Default.Videocam,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isConnected) "No Video Stream" else "Camera Offline",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isConnected)
                    "Waiting for drone camera feed..."
                else
                    "Connect to drone to view camera",
                color = Color.Gray.copy(alpha = 0.6f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * WebView-based video stream viewer
 * Supports HTTP video streams and RTSP-over-HTTP
 */
@android.annotation.SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoStreamView(
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = {
            WebView(context).apply {
                webViewClient = WebViewClient()
                settings.apply {
                    javaScriptEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    builtInZoomControls = false
                    domStorageEnabled = true
                }
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl(url)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        modifier = modifier
    )
}

