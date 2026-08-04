package com.nexdevlab.networktools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SignalCellularAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.ConnectionEventType
import com.nexdevlab.networktools.data.ConnectionKind
import com.nexdevlab.networktools.data.ConnectionLogEntry
import com.nexdevlab.networktools.data.ConnectionLogFilter
import com.nexdevlab.networktools.data.ConnectionStateSnapshot
import com.nexdevlab.networktools.data.ConnectionsLogViewModel
import com.nexdevlab.networktools.data.asConnectionLogText
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsLogScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: ConnectionsLogViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf(ConnectionLogFilter.ALL) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val visibleEntries = state.filtered(filter)

    DisposableEffect(viewModel) {
        viewModel.startMonitoring()
        onDispose(viewModel::stopMonitoring)
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Connections Log?") },
            text = { Text("All locally stored connection transitions will be removed.") },
            confirmButton = { Button(onClick = { viewModel.clear(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Connections Log") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = visibleEntries.isNotEmpty(),
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("Connections Log", visibleEntries.asConnectionLogText()),
                            )
                            scope.launch { snackbar.showSnackbar("Visible log entries copied") }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy visible log entries") }
                    IconButton(
                        enabled = visibleEntries.isNotEmpty(),
                        onClick = { shareConnectionsLog(context, visibleEntries) },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share visible log entries") }
                    IconButton(enabled = state.entries.isNotEmpty(), onClick = { confirmClear = true }) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear Connections Log")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Connections Log records normalized connection transitions only while this screen is " +
                            "visible. It does not store Wi-Fi names, addresses, SIM details, or background activity.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ConnectionCurrentCard(state.current, state.monitoring) }
            item { ConnectionFilterSegments(filter) { filter = it } }
            state.error?.let { item { ConnectionLogNotice(it, error = true) } }
            if (visibleEntries.isEmpty()) {
                item { ConnectionEmptyState(filter) }
            } else {
                items(visibleEntries, key = ConnectionLogEntry::id) { entry -> ConnectionEntryCard(entry) }
            }
            item {
                ConnectionLogNotice(
                    "Stored locally: ${state.entries.size}/100 entries. Monitoring stops when this screen closes; " +
                        "the log is excluded from backup and device transfer.",
                )
            }
        }
    }
}

@Composable
private fun ConnectionCurrentCard(current: ConnectionStateSnapshot?, monitoring: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(connectionIcon(current?.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(if (monitoring) "Monitoring this screen" else "Monitoring stopped", fontWeight = FontWeight.Bold)
                Text(current?.status ?: "Reading active connection", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(shape = RoundedCornerShape(8.dp), color = if (monitoring) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface) {
                Text(if (monitoring) "Live" else "Stopped", Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ConnectionFilterSegments(selected: ConnectionLogFilter, onSelected: (ConnectionLogFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ConnectionLogFilter.entries.forEach { filter ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(filter) },
                shape = RoundedCornerShape(8.dp),
                color = if (filter == selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) { Text(filter.label, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

@Composable
private fun ConnectionEntryCard(entry: ConnectionLogEntry) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(connectionEventIcon(entry.event), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(entry.current.status, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatConnectionTime(entry.timestampMillis), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ConnectionEmptyState(filter: ConnectionLogFilter) {
    Surface(Modifier.fillMaxWidth().height(160.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(38.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(if (filter == ConnectionLogFilter.ALL) "No connection changes yet" else "No ${filter.label} changes",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ConnectionLogNotice(message: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun connectionIcon(kind: ConnectionKind?): ImageVector = when (kind) {
    ConnectionKind.WIFI -> Icons.Outlined.Wifi
    ConnectionKind.MOBILE -> Icons.Outlined.SignalCellularAlt
    ConnectionKind.OFFLINE -> Icons.Outlined.WifiOff
    else -> Icons.Outlined.SwapHoriz
}

private fun connectionEventIcon(event: ConnectionEventType): ImageVector = when (event) {
    ConnectionEventType.CONNECTED -> Icons.Outlined.Wifi
    ConnectionEventType.DISCONNECTED -> Icons.Outlined.WifiOff
    else -> Icons.Outlined.SwapHoriz
}

private val ConnectionTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun formatConnectionTime(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(ConnectionTimeFormatter)

private fun shareConnectionsLog(context: Context, entries: List<ConnectionLogEntry>) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Connections Log")
                putExtra(Intent.EXTRA_TEXT, entries.asConnectionLogText())
            },
            "Share Connections Log",
        ),
    )
}
