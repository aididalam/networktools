package com.nexdevlab.networktools.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.PortResultFilter
import com.nexdevlab.networktools.data.PortScanConfig
import com.nexdevlab.networktools.data.PortScanEntry
import com.nexdevlab.networktools.data.PortScanHistoryEntry
import com.nexdevlab.networktools.data.PortScanResult
import com.nexdevlab.networktools.data.PortScanStatus
import com.nexdevlab.networktools.data.PortScannerUiState
import com.nexdevlab.networktools.data.PortScannerView
import com.nexdevlab.networktools.data.PortScannerViewModel
import com.nexdevlab.networktools.data.PortSelectionMode
import com.nexdevlab.networktools.data.formatLatency
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortScannerScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: PortScannerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedView by rememberSaveable { mutableStateOf(PortScannerView.SCANNER) }
    var target by rememberSaveable { mutableStateOf("") }
    var selectionMode by rememberSaveable { mutableStateOf(PortSelectionMode.SINGLE) }
    var singlePort by rememberSaveable { mutableStateOf(PortScanConfig.DefaultSinglePort.toString()) }
    var rangeStart by rememberSaveable { mutableStateOf(PortScanConfig.DefaultRangeStart.toString()) }
    var rangeEnd by rememberSaveable { mutableStateOf(PortScanConfig.DefaultRangeEnd.toString()) }
    var customPorts by rememberSaveable { mutableStateOf(PortScanConfig.CommonPorts.joinToString(",")) }
    var timeout by rememberSaveable { mutableStateOf(PortScanConfig.DefaultTimeoutMillis) }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var resultFilter by rememberSaveable { mutableStateOf(PortResultFilter.OPEN) }

    fun startScan() {
        val config = PortScanConfig(
            target = target,
            selectionMode = selectionMode,
            singlePort = singlePort,
            rangeStart = rangeStart,
            rangeEnd = rangeEnd,
            customPorts = customPorts,
            timeoutMillis = timeout,
            authorized = authorized,
        )
        runCatching(config::validate).onFailure {
            formError = it.message ?: "Check the scan settings"
            return
        }
        formError = null
        focusManager.clearFocus()
        viewModel.start(config)
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueAfterPermission(granted)
    }

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) viewModel.stop() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
            viewModel.dismissPermission()
        }
    }

    if (state.permissionRequired) {
        PortLocalPermissionExplanation(
            onContinue = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else {
                    viewModel.continueAfterPermission(true)
                }
            },
            onDismiss = viewModel::dismissPermission,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Port Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.result != null,
                        onClick = {
                            state.result?.let { result ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Port scan", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Port scan copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy port scan") }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { sharePortScan(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share port scan") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Scan TCP ports on one host that you own or are authorized to test. " +
                            "NetworkTools opens no application session, sends no payload, and performs no authentication.",
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
            item { PortViewPicker(selectedView) { selectedView = it } }
            when (selectedView) {
                PortScannerView.SCANNER -> portScannerContent(
                    state = state,
                    target = target,
                    onTargetChanged = { target = it.take(253); formError = null },
                    onUseLocal = { target = viewModel.localTarget(); formError = null },
                    selectionMode = selectionMode,
                    onSelectionModeChanged = { selectionMode = it; formError = null },
                    singlePort = singlePort,
                    onSinglePortChanged = { singlePort = it.numericPortInput(5); formError = null },
                    rangeStart = rangeStart,
                    onRangeStartChanged = { rangeStart = it.numericPortInput(5); formError = null },
                    rangeEnd = rangeEnd,
                    onRangeEndChanged = { rangeEnd = it.numericPortInput(5); formError = null },
                    customPorts = customPorts,
                    onCustomPortsChanged = { customPorts = it.take(2_048); formError = null },
                    onUseCommon = { customPorts = PortScanConfig.CommonPorts.joinToString(",") },
                    timeout = timeout,
                    onTimeoutChanged = { timeout = it },
                    authorized = authorized,
                    onAuthorizedChanged = { authorized = it },
                    formError = formError,
                    onStart = ::startScan,
                    onStop = viewModel::stop,
                    resultFilter = resultFilter,
                    onFilterChanged = { resultFilter = it },
                )
                PortScannerView.HISTORY -> portHistoryContent(
                    history = state.history,
                    onRemove = viewModel::removeHistory,
                    onClear = viewModel::clearHistory,
                )
            }
        }
    }
}

@Composable
private fun PortViewPicker(selected: PortScannerView, onSelected: (PortScannerView) -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PortScannerView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(view) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == view) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected == view) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) { Box(contentAlignment = Alignment.Center) { Text(view.label, fontWeight = FontWeight.Medium) } }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.portScannerContent(
    state: PortScannerUiState,
    target: String,
    onTargetChanged: (String) -> Unit,
    onUseLocal: () -> Unit,
    selectionMode: PortSelectionMode,
    onSelectionModeChanged: (PortSelectionMode) -> Unit,
    singlePort: String,
    onSinglePortChanged: (String) -> Unit,
    rangeStart: String,
    onRangeStartChanged: (String) -> Unit,
    rangeEnd: String,
    onRangeEndChanged: (String) -> Unit,
    customPorts: String,
    onCustomPortsChanged: (String) -> Unit,
    onUseCommon: () -> Unit,
    timeout: Int,
    onTimeoutChanged: (Int) -> Unit,
    authorized: Boolean,
    onAuthorizedChanged: (Boolean) -> Unit,
    formError: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    resultFilter: PortResultFilter,
    onFilterChanged: (PortResultFilter) -> Unit,
) {
    val busy = state.preparing || state.running
    item {
        PortConfigurationPanel(
            target, onTargetChanged, onUseLocal, selectionMode, onSelectionModeChanged,
            singlePort, onSinglePortChanged, rangeStart, onRangeStartChanged, rangeEnd, onRangeEndChanged,
            customPorts, onCustomPortsChanged, onUseCommon, timeout, onTimeoutChanged,
            authorized, onAuthorizedChanged, formError, busy, onStart, onStop,
        )
    }
    if (busy) item { PortProgressPanel(state) }
    state.error?.let { item { PortErrorPanel(it) } }
    val result = state.result
    if (result != null || state.entries.isNotEmpty()) {
        item { PortResultSummary(state) }
        item { PortFilterPicker(resultFilter, onFilterChanged) }
        val visible = if (resultFilter == PortResultFilter.OPEN) {
            state.entries.filter { it.status == PortScanStatus.OPEN }
        } else state.entries
        if (visible.isEmpty()) {
            item { PortNoResults(resultFilter, busy) }
        } else {
            items(visible, key = PortScanEntry::port) { entry -> PortResultRow(entry) }
        }
    } else if (!busy) {
        item { PortEmptyState() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.portHistoryContent(
    history: List<PortScanHistoryEntry>,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    item { PortHistoryHeader(history.isNotEmpty(), onClear) }
    if (history.isEmpty()) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Text("Completed and stopped port scans will appear here.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        items(history, key = PortScanHistoryEntry::id) { entry ->
            PortHistoryRow(entry, onRemove = { onRemove(entry.id) })
        }
    }
}

@Composable
private fun PortConfigurationPanel(
    target: String,
    onTargetChanged: (String) -> Unit,
    onUseLocal: () -> Unit,
    selectionMode: PortSelectionMode,
    onSelectionModeChanged: (PortSelectionMode) -> Unit,
    singlePort: String,
    onSinglePortChanged: (String) -> Unit,
    rangeStart: String,
    onRangeStartChanged: (String) -> Unit,
    rangeEnd: String,
    onRangeEndChanged: (String) -> Unit,
    customPorts: String,
    onCustomPortsChanged: (String) -> Unit,
    onUseCommon: () -> Unit,
    timeout: Int,
    onTimeoutChanged: (Int) -> Unit,
    authorized: Boolean,
    onAuthorizedChanged: (Boolean) -> Unit,
    error: String?,
    busy: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChanged,
                label = { Text("Hostname or IP address") },
                singleLine = true,
                enabled = !busy,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
                trailingIcon = {
                    IconButton(onClick = onUseLocal, enabled = !busy) {
                        Icon(Icons.Outlined.Smartphone, contentDescription = "Use this device's LAN address")
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                PortSelectionMode.entries.forEach { mode ->
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !busy) { onSelectionModeChanged(mode) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectionMode == mode) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) { Box(contentAlignment = Alignment.Center) { Text(mode.label, fontWeight = FontWeight.Medium) } }
                }
            }
            when (selectionMode) {
                PortSelectionMode.SINGLE -> PortNumberField("TCP port", singlePort, onSinglePortChanged, busy,
                    Modifier.fillMaxWidth())
                PortSelectionMode.RANGE -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PortNumberField("Start port", rangeStart, onRangeStartChanged, busy, Modifier.weight(1f))
                    PortNumberField("End port", rangeEnd, onRangeEndChanged, busy, Modifier.weight(1f))
                }
                PortSelectionMode.CUSTOM -> {
                    OutlinedTextField(
                        value = customPorts,
                        onValueChange = onCustomPortsChanged,
                        label = { Text("Comma-separated TCP ports") },
                        enabled = !busy,
                        minLines = 2,
                        maxLines = 3,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = onUseCommon, enabled = !busy, modifier = Modifier.align(Alignment.End)) {
                        Icon(Icons.AutoMirrored.Outlined.PlaylistAddCheck, contentDescription = null)
                        Text("Common ports", Modifier.padding(start = 6.dp))
                    }
                }
            }
            Text("Connection timeout", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(500, 1000, 2000).forEach { value ->
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !busy) { onTimeoutChanged(value) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (timeout == value) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) { Box(contentAlignment = Alignment.Center) { Text(if (value < 1000) "$value ms" else "${value / 1000} s") } }
                }
            }
            Row(Modifier.fillMaxWidth().clickable(enabled = !busy) { onAuthorizedChanged(!authorized) },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = authorized, onCheckedChange = onAuthorizedChanged, enabled = !busy)
                Text("I own or am authorized to scan this host", modifier = Modifier.weight(1f))
            }
            Button(onClick = if (busy) onStop else onStart, modifier = Modifier.fillMaxWidth()) {
                Icon(if (busy) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow, contentDescription = null)
                Text(if (busy) "Stop scan" else "Start port scan", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PortNumberField(
    label: String,
    value: String,
    onValueChanged: (String) -> Unit,
    busy: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        label = { Text(label) },
        singleLine = true,
        enabled = !busy,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun PortLocalPermissionExplanation(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        title = { Text("Allow local network access") },
        text = {
            Text("Android requires Nearby devices permission for this local-network target. " +
                "NetworkTools connects only to the displayed host and selected TCP ports while this screen is visible.")
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun PortProgressPanel(state: PortScannerUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Column(Modifier.weight(1f)) {
                    Text(if (state.preparing) "Resolving target" else "Scanning TCP ports", fontWeight = FontWeight.Medium)
                    Text(if (state.preparing) state.activeConfig?.target.orEmpty()
                    else "${state.entries.size} of ${state.totalPorts} ports • ${state.entries.count { it.status == PortScanStatus.OPEN }} open",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!state.preparing && state.totalPorts > 0) {
                LinearProgressIndicator(progress = { state.entries.size.toFloat() / state.totalPorts },
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun PortResultSummary(state: PortScannerUiState) {
    val entries = state.entries
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${entries.count { it.status == PortScanStatus.OPEN }} open ports",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                state.result?.let { result ->
                    Text(if (result.stopped) "Stopped" else "Completed",
                        color = if (result.stopped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            Text("${state.normalizedTarget.orEmpty()} • ${state.resolvedAddress.orEmpty()}",
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${entries.size} scanned • ${entries.count { it.status == PortScanStatus.CLOSED }} closed • " +
                "${entries.count { it.status == PortScanStatus.TIMED_OUT }} timed out",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PortFilterPicker(selected: PortResultFilter, onSelected: (PortResultFilter) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PortResultFilter.entries.forEach { filter ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(filter) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == filter) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) { Box(contentAlignment = Alignment.Center) { Text(filter.label, fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
private fun PortResultRow(entry: PortScanEntry) {
    val color = statusColor(entry.status)
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.16f), contentColor = color) {
                Text(entry.port.toString(), Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(entry.service, fontWeight = FontWeight.Medium)
                Text("TCP • ${entry.status.label}" + entry.latencyMillis?.let { " • ${formatLatency(it)} ms" }.orEmpty(),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(entry.status.label, color = color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PortNoResults(filter: PortResultFilter, running: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(if (filter == PortResultFilter.OPEN) {
            if (running) "No open ports found yet." else "No open ports were confirmed."
        } else "No port results are available.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PortEmptyState() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No port scan result", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun PortErrorPanel(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Text(message, Modifier.padding(14.dp))
    }
}

@Composable
private fun PortHistoryHeader(hasEntries: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.History, contentDescription = null)
        Text("Port scan history", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f))
        TextButton(onClick = onClear, enabled = hasEntries) {
            Icon(Icons.Outlined.ClearAll, contentDescription = null)
            Text("Clear", Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun PortHistoryRow(entry: PortScanHistoryEntry, onRemove: () -> Unit) {
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${entry.target} • ${entry.openPorts.size} open", fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${entry.scannedCount} scanned • ${entry.resolvedAddress}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.completedAtMillis)) +
                        if (entry.stopped) " • Stopped" else "", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove port scan history entry")
                }
            }
            if (expanded) {
                entry.openPorts.forEach { port ->
                    Text("${port.port}/tcp • ${port.service}", Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        fontFamily = FontFamily.Monospace)
                }
                if (entry.openPorts.isEmpty()) {
                    Text("No open ports confirmed", Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: PortScanStatus): Color = when (status) {
    PortScanStatus.OPEN -> MaterialTheme.colorScheme.primary
    PortScanStatus.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    PortScanStatus.TIMED_OUT -> MaterialTheme.colorScheme.tertiary
    PortScanStatus.UNREACHABLE, PortScanStatus.ERROR -> MaterialTheme.colorScheme.error
}

private fun String.numericPortInput(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private fun sharePortScan(context: Context, result: PortScanResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Port scan result")
        putExtra(Intent.EXTRA_TEXT, result.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share port scan"))
}
