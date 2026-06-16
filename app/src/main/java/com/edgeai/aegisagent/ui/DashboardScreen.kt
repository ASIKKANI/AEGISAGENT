package com.edgeai.aegisagent.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.edgeai.aegisagent.ui.theme.*
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AegisViewModel,
    modifier: Modifier = Modifier
) {
    // Collecting StateFlow states
    val isWarmingUp by viewModel.isWarmingUp.collectAsState()
    val volume by viewModel.volumeLevel.collectAsState()
    val brightness by viewModel.brightnessLevel.collectAsState()
    val flashlight by viewModel.flashlightState.collectAsState()
    val wifi by viewModel.wifiState.collectAsState()
    val bluetooth by viewModel.bluetoothState.collectAsState()
    val rotation by viewModel.rotationState.collectAsState()
    val mediaPlaying by viewModel.mediaPlaying.collectAsState()
    val mediaTrack by viewModel.mediaTrack.collectAsState()
    val meetings by viewModel.meetingsList.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()

    var inputQuery by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. SYSTEM HEADER STATUS CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Diagnostic Pulsing indicator
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                        val alpha by infiniteTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_alpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background((if (isWarmingUp) WarningAmber else if (isSimulationMode) WarningAmber else ActiveGreen).copy(alpha = alpha))
                        )
                        Text(
                            text = "⚡ AEGIS COGNITIVE CORE",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    
                    // Warming Up / Mode indicator
                    if (isWarmingUp) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = WarningAmber.copy(alpha = 0.2f)),
                            border = BorderStroke(1.dp, WarningAmber)
                        ) {
                            Text(
                                text = "WARMING UP",
                                color = WarningAmber,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Card(
                            onClick = { viewModel.toggleSimulationMode() },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSimulationMode) WarningAmber.copy(alpha = 0.15f) else ActiveGreen.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, if (isSimulationMode) WarningAmber else ActiveGreen)
                        ) {
                            Text(
                                text = if (isSimulationMode) "OFFLINE SIM" else "LOCAL OLLAMA",
                                color = if (isSimulationMode) WarningAmber else ActiveGreen,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Divider(color = GlassBorder.copy(alpha = 0.2f))

                // Network Toggles row (now fully clickable!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        StatusToggle(
                            label = "Wi-Fi",
                            active = wifi,
                            activeColor = CyberCyan,
                            icon = Icons.Default.Wifi,
                            onClick = { viewModel.toggleWifi() }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StatusToggle(
                            label = "Bluetooth",
                            active = bluetooth,
                            activeColor = ElectricPurple,
                            icon = Icons.Default.Bluetooth,
                            onClick = { viewModel.toggleBluetooth() }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StatusToggle(
                            label = "Flash",
                            active = flashlight,
                            activeColor = WarningAmber,
                            icon = Icons.Default.Lightbulb,
                            onClick = { viewModel.toggleFlashlight() }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        StatusToggle(
                            label = "Rotate",
                            active = rotation,
                            activeColor = ActiveGreen,
                            icon = Icons.Default.Refresh,
                            onClick = { viewModel.toggleRotation() }
                        )
                    }
                }
            }
        }

        // 2. HARDWARE SLIDERS (VOLUME & BRIGHTNESS)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "⚙️ SYSTEM REGISTRY (INTERACTIVE)",
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )

                // Volume Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Speaker Volume", fontSize = 12.sp, color = TextPrimary)
                        Text(text = "$volume%", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = volume.toFloat(),
                        onValueChange = { viewModel.setVolume(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = CyberCyan,
                            activeTrackColor = CyberCyan,
                            inactiveTrackColor = GlassBorder.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Brightness Slider
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Screen Brightness", fontSize = 12.sp, color = TextPrimary)
                        Text(text = "$brightness%", color = ElectricPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Slider(
                        value = brightness.toFloat(),
                        onValueChange = { viewModel.setBrightness(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricPurple,
                            activeTrackColor = ElectricPurple,
                            inactiveTrackColor = GlassBorder.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 3. MEDIA VISUALIZER CANVAS CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎵 ATMOSPHERE COMPANION",
                        color = TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (mediaPlaying) "PLAYING" else "PAUSED",
                        color = if (mediaPlaying) ActiveGreen else TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = mediaTrack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // The Canvas Waveform Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                ) {
                    AnimatedWaveform(isPlaying = mediaPlaying)
                }
            }
        }

        // 4. ALERTS / MEETINGS CARD
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(105.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassSurface)
                .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "📅 PRODUCTIVITY CALENDAR",
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
                if (meetings.isEmpty()) {
                    Text(
                        text = "No scheduled appointments or active timers.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(meetings) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(text = "•", color = CyberCyan)
                                Text(text = item, fontSize = 12.sp, color = TextPrimary)
                            }
                        }
                    }
                }
            }
        }

        // 5. NATURAL LANGUAGE PROMPT INPUT BAR
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = inputQuery,
                onValueChange = { inputQuery = it },
                placeholder = { Text("Mute volume & take vintage photo...", color = TextSecondary) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = GlassSurface,
                    unfocusedContainerColor = GlassSurface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Button(
                onClick = {
                    if (inputQuery.isNotBlank()) {
                        viewModel.submitQuery(inputQuery)
                        inputQuery = ""
                    }
                },
                enabled = !isWarmingUp,
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Submit query",
                    tint = Color.Black
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusToggle(
    label: String,
    active: Boolean,
    activeColor: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (active) activeColor.copy(alpha = 0.15f) else GlassSurface.copy(alpha = 0.4f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (active) activeColor else GlassBorder.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (active) activeColor else TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) TextPrimary else TextSecondary
            )
        }
    }
}

@Composable
fun AnimatedWaveform(isPlaying: Boolean) {
    val transition = rememberInfiniteTransition(label = "visualizer")
    
    // Wave animation offset phase
    val phase by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * Math.PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "phase"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    // Wave amplitude modifier
    val amplitudeMultiplier by if (isPlaying) {
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "amplitude"
        )
    } else {
        remember { mutableStateOf(0.05f) }
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val centerY = height / 2

        val path = Path()
        path.moveTo(0f, centerY)

        val steps = 150
        val stepX = width / steps

        for (i in 0..steps) {
            val x = i * stepX
            // Combine multiple sine functions for a organic, premium waveform look
            val sine1 = sin((i * 0.08f) - phase) * 45f * amplitudeMultiplier
            val sine2 = sin((i * 0.2f) + phase * 1.5f) * 15f * amplitudeMultiplier
            val y = centerY + sine1 + sine2
            path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = CyberCyan,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw a second glowing purple background wave
        val bgPath = Path()
        bgPath.moveTo(0f, centerY)
        for (i in 0..steps) {
            val x = i * stepX
            val sine1 = sin((i * 0.07f) + phase * 0.8f) * 35f * amplitudeMultiplier
            val sine2 = sin((i * 0.15f) - phase * 1.2f) * 20f * amplitudeMultiplier
            val y = centerY + sine1 + sine2
            bgPath.lineTo(x, y)
        }
        drawPath(
            path = bgPath,
            color = ElectricPurple.copy(alpha = 0.5f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
