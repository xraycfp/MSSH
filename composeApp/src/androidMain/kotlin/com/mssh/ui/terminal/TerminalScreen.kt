package com.mssh.ui.terminal

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mssh.ssh.ConnectionState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private val TerminalBg = Color(0xFF1E1E2E)
private val HeaderBg = Color(0xFF16162A)
private val StatusConnected = Color(0xFF00CC99)
private val StatusError = Color(0xFFFF5555)
private val StatusPending = Color(0xFFE0A030)
private val StatusIdle = Color(0xFF666680)
private val AccentBlue = Color(0xFF4A9EFF)

@Composable
fun TerminalScreen(
    hostId: Long,
    onBack: () -> Unit,
    viewModel: TerminalViewModel = koinViewModel { parametersOf(hostId) }
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.connectionState) {
        Log.d(
            "TerminalScreen",
            "hostId=$hostId state=${uiState.connectionState::class.simpleName} title='${uiState.title}' host='${uiState.hostName}'"
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TerminalBg)
            .systemBarsPadding()
    ) {
        // Compact header bar
        TerminalHeaderBar(
            title = uiState.title.ifBlank { uiState.hostName },
            connectionState = uiState.connectionState,
            onBack = {
                viewModel.disconnect()
                onBack()
            },
            onReconnect = viewModel::reconnect
        )

        // Main content
        when (val state = uiState.connectionState) {
            is ConnectionState.Idle,
            is ConnectionState.Connecting,
            is ConnectionState.Authenticating -> {
                ConnectingContent(
                    state = state,
                    modifier = Modifier.weight(1f)
                )
            }
            is ConnectionState.Connected -> {
                TerminalCanvas(
                    emulator = viewModel.emulator,
                    onInput = viewModel::sendInput,
                    modifier = Modifier.weight(1f),
                    onSizeChanged = viewModel::resizeTerminal
                )
            }
            is ConnectionState.Error -> {
                ErrorContent(
                    message = state.message,
                    onReconnect = viewModel::reconnect,
                    modifier = Modifier.weight(1f)
                )
            }
            is ConnectionState.Disconnected -> {
                DisconnectedContent(
                    onBack = onBack,
                    onReconnect = viewModel::reconnect,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Extra keys always visible when connected
        AnimatedVisibility(
            visible = uiState.connectionState is ConnectionState.Connected,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            ExtraKeysBar(onInput = viewModel::sendInput)
        }
    }
}

@Composable
private fun TerminalHeaderBar(
    title: String,
    connectionState: ConnectionState,
    onBack: () -> Unit,
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeaderBg)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Back button
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color(0xFF9999AA),
                modifier = Modifier.size(18.dp)
            )
        }

        // Status dot
        StatusDot(connectionState)

        Spacer(modifier = Modifier.width(8.dp))

        // Title
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color(0xFFDDDDEE),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Reconnect button when disconnected/errored
        if (connectionState is ConnectionState.Disconnected ||
            connectionState is ConnectionState.Error
        ) {
            IconButton(
                onClick = onReconnect,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Reconnect",
                    tint = AccentBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusDot(connectionState: ConnectionState) {
    val color = when (connectionState) {
        is ConnectionState.Connected -> StatusConnected
        is ConnectionState.Error -> StatusError
        is ConnectionState.Disconnected -> StatusIdle
        else -> StatusPending
    }

    val isPending = connectionState is ConnectionState.Connecting ||
            connectionState is ConnectionState.Authenticating ||
            connectionState is ConnectionState.Idle

    if (isPending) {
        val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "statusAlpha"
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .clip(CircleShape)
                .background(color)
        )
    } else {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
private fun ConnectingContent(state: ConnectionState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.size(32.dp)
            )
            Text(
                text = when (state) {
                    is ConnectionState.Connecting -> "Connecting..."
                    is ConnectionState.Authenticating -> "Authenticating..."
                    else -> "Initializing..."
                },
                color = Color(0xFF8888AA),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StatusError.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = StatusError
                )
            }
            Text(
                text = message,
                color = Color(0xFFAA8888),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            FilledTonalButton(
                onClick = onReconnect,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = AccentBlue.copy(alpha = 0.15f),
                    contentColor = AccentBlue
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Reconnect", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun DisconnectedContent(
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(TerminalBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StatusIdle.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = StatusIdle
                )
            }
            Text(
                text = "Session ended",
                color = Color(0xFF8888AA),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF8888AA)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Back", fontSize = 13.sp)
                }
                FilledTonalButton(
                    onClick = onReconnect,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = AccentBlue.copy(alpha = 0.15f),
                        contentColor = AccentBlue
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reconnect", fontSize = 13.sp)
                }
            }
        }
    }
}
