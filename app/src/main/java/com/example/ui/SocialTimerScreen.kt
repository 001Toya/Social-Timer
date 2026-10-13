package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.BeepIntensity
import com.example.model.TimerState
import com.example.model.TimerStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SocialTimerScreen(
    viewModel: TimerViewModel,
    hasNotificationPermission: Boolean,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val timerState by viewModel.timerState.collectAsStateWithLifecycle()
    val customInput by viewModel.customInput.collectAsStateWithLifecycle()
    val isOverlaySetupCompleted by viewModel.isOverlaySetupCompleted.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initAudio(context)
        viewModel.initOverlaySetupState(context)
    }

    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.checkOverlayPermission(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header Bar
                    HeaderSection()

                    Spacer(modifier = Modifier.height(16.dp))

                    // Permission Alerts (Overlay & Notification)
                    PermissionCards(
                        isOverlaySetupCompleted = isOverlaySetupCompleted,
                        hasNotificationPermission = hasNotificationPermission,
                        onGrantOverlay = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            try {
                                overlayPermissionLauncher.launch(intent)
                            } catch (_: Exception) {
                                requestOverlayPermission(context)
                            }
                        },
                        onGrantNotification = onRequestNotificationPermission
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Dismissed Overlay Restore Banner
                    AnimatedVisibility(visible = (timerState.isRunning || timerState.isPaused) && !timerState.isOverlayEnabled) {
                        Column {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Layers,
                                        contentDescription = "Restore Floating Widget",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Floating Widget Dismissed",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Countdown is active in background. Tap to show the floating widget again.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.restoreOverlay() },
                                        modifier = Modifier.testTag("restore_overlay_button"),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Show", fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }

                    // Main Timer Display / Interactive Card
                    MainTimerCard(
                        timerState = timerState,
                        customInput = customInput,
                        viewModel = viewModel,
                        context = context
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Duration Setup (visible when IDLE)
                    AnimatedVisibility(visible = timerState.isIdle) {
                        Column {
                            DurationSelectionCard(
                                customInput = customInput,
                                onPresetSelected = { viewModel.selectPreset(it) },
                                onUpdateHours = { viewModel.updateHours(it) },
                                onUpdateMinutes = { viewModel.updateMinutes(it) },
                                onUpdateSeconds = { viewModel.updateSeconds(it) }
                            )

                            Spacer(modifier = Modifier.height(20.dp))
                        }
                    }

                    // Settings & Audio Alert Configuration
                    AudioSettingsCard(
                        soundEnabled = timerState.soundEnabled,
                        beepIntensity = timerState.beepIntensity,
                        onSoundToggle = { viewModel.setSoundEnabled(it) },
                        onIntensitySelected = { viewModel.setBeepIntensity(it) },
                        onTestSound = { viewModel.testAlarmSound(context) }
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Educational Awareness Card
                    MindfulUsageCard()

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF38BDF8),
                            Color(0xFF6366F1)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Alarm,
                contentDescription = "Social Timer Logo",
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column {
            Text(
                text = "Social Timer",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            )
            Text(
                text = "Mindful Social Media Session Control",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PermissionCards(
    isOverlaySetupCompleted: Boolean,
    hasNotificationPermission: Boolean,
    onGrantOverlay: () -> Unit,
    onGrantNotification: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (!isOverlaySetupCompleted) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("overlay_setup_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Overlay Permission",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Overlay Timer",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Show the timer over apps to view countdown while in TikTok, Instagram, or YouTube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onGrantOverlay,
                        modifier = Modifier.testTag("overlay_permission_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Enable", fontSize = 12.sp)
                    }
                }
            }
        }

        if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notification Permission",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Notifications",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Get notified and receive background timer updates.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = onGrantNotification,
                        modifier = Modifier.testTag("notification_permission_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Allow", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTimerCard(
    timerState: TimerState,
    customInput: CustomTimeInput,
    viewModel: TimerViewModel,
    context: Context
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (timerState.isCompleted) 1.05f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val cardBorderColor by animateColorAsState(
        targetValue = when (timerState.status) {
            TimerStatus.COMPLETED -> Color(0xFFEF4444)
            TimerStatus.RUNNING -> Color(0xFF38BDF8)
            TimerStatus.PAUSED -> Color(0xFFFBBF24)
            TimerStatus.IDLE -> MaterialTheme.colorScheme.outlineVariant
        },
        label = "borderColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, cardBorderColor, RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Badge
            StatusPill(timerState.status)

            Spacer(modifier = Modifier.height(24.dp))

            // Circular progress & digital clock
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(240.dp)
                    .scale(pulseScale)
            ) {
                // Background track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 14.dp,
                    strokeCap = StrokeCap.Round
                )

                // Active countdown progress
                val activeProgress = if (timerState.isIdle) 1f else (1f - timerState.progress)
                val progressColor = when (timerState.status) {
                    TimerStatus.COMPLETED -> Color(0xFFEF4444)
                    TimerStatus.PAUSED -> Color(0xFFFBBF24)
                    else -> Color(0xFF38BDF8)
                }

                CircularProgressIndicator(
                    progress = { activeProgress },
                    modifier = Modifier.fillMaxSize(),
                    color = progressColor,
                    strokeWidth = 14.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val displayText = when (timerState.status) {
                        TimerStatus.COMPLETED -> "00:00"
                        TimerStatus.IDLE -> formatDurationInput(customInput)
                        else -> timerState.remainingFormatted
                    }
                    val isLongDuration = displayText.length > 5

                    Text(
                        text = displayText,
                        style = if (isLongDuration) {
                            MaterialTheme.typography.headlineLarge.copy(
                                fontSize = 36.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.5.sp
                            )
                        } else {
                            MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        },
                        maxLines = 1,
                        color = when (timerState.status) {
                            TimerStatus.COMPLETED -> Color(0xFFEF4444)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = when (timerState.status) {
                            TimerStatus.COMPLETED -> "TIME'S UP!"
                            TimerStatus.PAUSED -> "PAUSED"
                            TimerStatus.RUNNING -> "SESSION ACTIVE"
                            TimerStatus.IDLE -> "READY TO START"
                        },
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.5.sp
                        ),
                        color = when (timerState.status) {
                            TimerStatus.COMPLETED -> Color(0xFFEF4444)
                            TimerStatus.PAUSED -> Color(0xFFFBBF24)
                            TimerStatus.RUNNING -> Color(0xFF38BDF8)
                            TimerStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Action Buttons
            when (timerState.status) {
                TimerStatus.IDLE -> {
                    Button(
                        onClick = { viewModel.startTimer(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("start_button"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Start",
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START TIMER",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }

                TimerStatus.RUNNING -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Balanced Primary Row: Pause & Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { viewModel.pauseTimer(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("pause_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            ) {
                                Icon(
                                    Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Pause",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetTimer(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("reset_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Reset",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Distinct Secondary Stop Button
                        OutlinedButton(
                            onClick = { viewModel.stopTimer(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("stop_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Stop Session",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                TimerStatus.PAUSED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Balanced Primary Row: Resume & Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { viewModel.resumeTimer(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("resume_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF10B981),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Resume",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetTimer(context) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                                    .testTag("reset_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Reset",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Reset",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    maxLines = 1
                                )
                            }
                        }

                        // Distinct Secondary Stop Button
                        OutlinedButton(
                            onClick = { viewModel.stopTimer(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("stop_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                        ) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "Stop Session",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                TimerStatus.COMPLETED -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFEE2E2)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = "Alert",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Your allocated social media session is up! Disconnect and take a mindful breath.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF991B1B)
                                )
                            }
                        }

                        Button(
                            onClick = { viewModel.stopTimer(context) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("dismiss_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFDC2626),
                                contentColor = Color.White
                            )
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "Dismiss",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Dismiss & Stop Alert",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(status: TimerStatus) {
    val (bgColor, textColor, text) = when (status) {
        TimerStatus.IDLE -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "STANDBY")
        TimerStatus.RUNNING -> Triple(Color(0xFF0284C7).copy(alpha = 0.15f), Color(0xFF0284C7), "COUNTING DOWN")
        TimerStatus.PAUSED -> Triple(Color(0xFFD97706).copy(alpha = 0.15f), Color(0xFFD97706), "PAUSED")
        TimerStatus.COMPLETED -> Triple(Color(0xFFDC2626).copy(alpha = 0.15f), Color(0xFFDC2626), "TIME EXPIRED")
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DurationSelectionCard(
    customInput: CustomTimeInput,
    onPresetSelected: (Int) -> Unit,
    onUpdateHours: (Int) -> Unit,
    onUpdateMinutes: (Int) -> Unit,
    onUpdateSeconds: (Int) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Text(
                text = "Set Session Duration",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Choose a preset or adjust custom hours, minutes, and seconds",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Presets
            val presets = listOf(5, 10, 15, 20, 30, 45, 60)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { minutes ->
                    val label = if (minutes == 60) "1h" else "${minutes}m"
                    val isSelected = (customInput.hours * 60 + customInput.minutes) == minutes && customInput.seconds == 0
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPresetSelected(minutes) },
                        label = { Text(label, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.testTag("preset_${label}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom Steppers: Hours, Minutes, Seconds
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TimePickerUnit(
                    label = "HOURS",
                    value = customInput.hours,
                    onIncrement = { onUpdateHours(customInput.hours + 1) },
                    onDecrement = { onUpdateHours(customInput.hours - 1) },
                    testTagPrefix = "hours"
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.outline
                )

                TimePickerUnit(
                    label = "MINUTES",
                    value = customInput.minutes,
                    onIncrement = { onUpdateMinutes(customInput.minutes + 1) },
                    onDecrement = { onUpdateMinutes(customInput.minutes - 1) },
                    testTagPrefix = "minutes"
                )

                Text(
                    text = ":",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.outline
                )

                TimePickerUnit(
                    label = "SECONDS",
                    value = customInput.seconds,
                    onIncrement = { onUpdateSeconds(customInput.seconds + 5) },
                    onDecrement = { onUpdateSeconds(customInput.seconds - 5) },
                    testTagPrefix = "seconds"
                )
            }
        }
    }
}

@Composable
private fun TimePickerUnit(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    testTagPrefix: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(
            onClick = onIncrement,
            modifier = Modifier
                .size(40.dp)
                .testTag("${testTagPrefix}_increment")
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }

        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = String.format("%02d", value),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            )
        }

        IconButton(
            onClick = onDecrement,
            modifier = Modifier
                .size(40.dp)
                .testTag("${testTagPrefix}_decrement")
        ) {
            Text("-", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AudioSettingsCard(
    soundEnabled: Boolean,
    beepIntensity: BeepIntensity,
    onSoundToggle: (Boolean) -> Unit,
    onIntensitySelected: (BeepIntensity) -> Unit,
    onTestSound: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Audio Alert Settings",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Attention Alarm Sound",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Sharp, high-pitched digital timer beep upon completion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = soundEnabled,
                    onCheckedChange = onSoundToggle,
                    modifier = Modifier.testTag("sound_toggle")
                )
            }

            AnimatedVisibility(visible = soundEnabled) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Beep Intensity",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = beepIntensity == BeepIntensity.STANDARD,
                            onClick = { onIntensitySelected(BeepIntensity.STANDARD) },
                            label = { Text("Standard (2800 Hz)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("intensity_standard")
                        )
                        FilterChip(
                            selected = beepIntensity == BeepIntensity.STRONG,
                            onClick = { onIntensitySelected(BeepIntensity.STRONG) },
                            label = { Text("Strong (3000 Hz)") },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("intensity_strong")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedButton(
                        onClick = onTestSound,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("test_sound_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.GraphicEq,
                            contentDescription = "Test Audio",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Preview Alarm Beep", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun MindfulUsageCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "About Awareness",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Pure Awareness, Not a Blocker",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Social Timer is designed to foster conscious habit building. It never closes, locks, or intercepts your social apps. When your set time expires, an unmistakable digital alarm reminds you that your allocated time is up so you can choose to put your device down.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

private fun formatDurationInput(input: CustomTimeInput): String {
    val totalSeconds = (input.totalMillis / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

private fun requestOverlayPermission(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    ).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
