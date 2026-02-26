package com.mssh.ui.terminal

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mssh.ssh.ConnectionState
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.title.ifBlank { uiState.hostName },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = when (uiState.connectionState) {
                                is ConnectionState.Idle -> "Idle"
                                is ConnectionState.Connecting -> "Connecting..."
                                is ConnectionState.Authenticating -> "Authenticating..."
                                is ConnectionState.Connected -> "Connected"
                                is ConnectionState.Error -> "Error"
                                is ConnectionState.Disconnected -> "Disconnected"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = when (uiState.connectionState) {
                                is ConnectionState.Connected -> Color(0xFF00CC99)
                                is ConnectionState.Error -> MaterialTheme.colorScheme.error
                                is ConnectionState.Disconnected -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> Color(0xFFCCCC00)
                            }
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.disconnect()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.connectionState is ConnectionState.Disconnected ||
                        uiState.connectionState is ConnectionState.Error
                    ) {
                        IconButton(onClick = viewModel::reconnect) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState.connectionState) {
                is ConnectionState.Idle,
                is ConnectionState.Connecting,
                is ConnectionState.Authenticating -> {
                    // Loading state
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = when (state) {
                                    is ConnectionState.Connecting -> "Connecting..."
                                    is ConnectionState.Authenticating -> "Authenticating..."
                                    else -> "Initializing..."
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is ConnectionState.Connected -> {
                    // Terminal view
                    TerminalCanvas(
                        emulator = viewModel.emulator,
                        onInput = viewModel::sendInput,
                        modifier = Modifier.weight(1f),
                        onSizeChanged = viewModel::resizeTerminal
                    )

                    // Extra keys bar
                    ExtraKeysBar(
                        onInput = viewModel::sendInput
                    )
                }
                is ConnectionState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = state.message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = viewModel::reconnect) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reconnect")
                            }
                        }
                    }
                }
                is ConnectionState.Disconnected -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E1E2E)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Icon(
                                Icons.Default.LinkOff,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Disconnected",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(onClick = onBack) {
                                    Text("Back")
                                }
                                Button(onClick = viewModel::reconnect) {
                                    Text("Reconnect")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
