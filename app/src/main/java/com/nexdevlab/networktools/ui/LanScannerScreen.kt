package com.nexdevlab.networktools.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DevicesOther
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.LocationSearching
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.LanDevice
import com.nexdevlab.networktools.data.LanDiscoveryProtocol
import com.nexdevlab.networktools.data.LanScanConfig
import com.nexdevlab.networktools.data.LanScanHistoryEntry
import com.nexdevlab.networktools.data.LanScanMode
import com.nexdevlab.networktools.data.LanScanResult
import com.nexdevlab.networktools.data.LanScannerRepository
import com.nexdevlab.networktools.data.LanScannerUiState
import com.nexdevlab.networktools.data.LanScannerView
import com.nexdevlab.networktools.data.LanScannerViewModel
import com.nexdevlab.networktools.data.LanSubnet
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScannerScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: LanScannerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedView by rememberSaveable { mutableStateOf(LanScannerView.SCANNER) }
    var subnetText by rememberSaveable { mutableStateOf("") }
    var subnetEdited by rememberSaveable { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(LanScanMode.FAST) }
    var authorized by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingConfig by remember { mutableStateOf<LanScanConfig?>(null) }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.detectedSubnet) {
        if (!subnetEdited && subnetText.isBlank()) state.detectedSubnet?.let { subnetText = it }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val config = pendingConfig
        pendingConfig = null
        if (granted && config != null && LanScannerRepository.hasLocalNetworkPermission(context)) {
            viewModel.scan(config)
        } else {
            scope.launch { snackbar.showSnackbar("Nearby devices permission was not granted") }
        }
    }

    fun startScan() {
        val config = runCatching {
            LanScanConfig(LanSubnet.parse(subnetText), mode, authorized).also(LanScanConfig::validate)
        }.getOrElse {
            validationError = it.message ?: "Check the scan settings"
            return
        }
        validationError = null
        if (LanScannerRepository.hasLocalNetworkPermission(context)) {
            viewModel.scan(config)
        } else {
            pendingConfig = config
            showPermissionExplanation = true
        }
    }

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.stop()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    if (showPermissionExplanation) {
        LanPermissionExplanation(
            onContinue = {
                showPermissionExplanation = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else {
                    pendingConfig?.let(viewModel::scan)
                    pendingConfig = null
                }
            },
            onDismiss = {
                showPermissionExplanation = false
                pendingConfig = null
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("LAN Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = !state.scanning,
                        onClick = {
                            subnetEdited = false
                            subnetText = ""
                            viewModel.detectSubnet()
                        },
                    ) {
                        Icon(Icons.Outlined.LocationSearching, contentDescription = "Detect active subnet")
                    }
                    IconButton(
                        enabled = state.result != null,
                        onClick = {
                            state.result?.let { result ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("LAN scan", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("LAN scan copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy LAN scan") }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { shareLanScan(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share LAN scan") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Scan only a private LAN that you own or are authorized to test. " +
                            "Fast mode checks reachability and neighbors; Deep mode also listens for local service advertisements.",
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
            item { LanViewPicker(selectedView) { selectedView = it } }
            when (selectedView) {
                LanScannerView.SCANNER -> lanScannerContent(
                    state = state,
                    subnetText = subnetText,
                    onSubnetChanged = {
                        subnetEdited = true
                        subnetText = it.take(32)
                        validationError = null
                    },
                    mode = mode,
                    onModeChanged = { mode = it },
                    authorized = authorized,
                    onAuthorizedChanged = { authorized = it },
                    validationError = validationError,
                    onStart = ::startScan,
                    onStop = viewModel::stop,
                    onOpenSettings = { openLanSettings(context) },
                )
                LanScannerView.HISTORY -> lanHistoryContent(
                    history = state.history,
                    onOpen = { entry ->
                        viewModel.showHistory(entry)
                        selectedView = LanScannerView.SCANNER
                    },
                    onRemove = viewModel::removeHistory,
                    onClear = viewModel::clearHistory,
                )
            }
        }
    }
}

@Composable
private fun LanViewPicker(selected: LanScannerView, onSelected: (LanScannerView) -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        LanScannerView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(view) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == view) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected == view) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) { Box(contentAlignment = Alignment.Center) { Text(view.label, fontWeight = FontWeight.Medium) } }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lanScannerContent(
    state: LanScannerUiState,
    subnetText: String,
    onSubnetChanged: (String) -> Unit,
    mode: LanScanMode,
    onModeChanged: (LanScanMode) -> Unit,
    authorized: Boolean,
    onAuthorizedChanged: (Boolean) -> Unit,
    validationError: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    item {
        LanConfigurationPanel(
            subnetText,
            onSubnetChanged,
            mode,
            onModeChanged,
            authorized,
            onAuthorizedChanged,
            validationError,
            state.scanning,
            onStart,
            onStop,
        )
    }
    if (state.scanning) item { LanProgressPanel(state) }
    state.error?.let { error -> item { LanErrorPanel(error, onOpenSettings) } }
    state.result?.let { result -> item { LanResultSummary(result) } }
    if (state.devices.isNotEmpty()) {
        items(state.devices, key = LanDevice::ipAddress) { device -> LanDeviceCard(device) }
    } else if (!state.scanning) {
        item { LanEmptyState() }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.lanHistoryContent(
    history: List<LanScanHistoryEntry>,
    onOpen: (LanScanHistoryEntry) -> Unit,
    onRemove: (Long) -> Unit,
    onClear: () -> Unit,
) {
    item { LanHistoryHeader(history.isNotEmpty(), onClear) }
    if (history.isEmpty()) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Text("Completed and stopped scans will appear here.", Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        items(history, key = LanScanHistoryEntry::id) { entry ->
            LanHistoryRow(entry, onOpen = { onOpen(entry) }, onRemove = { onRemove(entry.id) })
        }
    }
}

@Composable
private fun LanConfigurationPanel(
    subnetText: String,
    onSubnetChanged: (String) -> Unit,
    mode: LanScanMode,
    onModeChanged: (LanScanMode) -> Unit,
    authorized: Boolean,
    onAuthorizedChanged: (Boolean) -> Unit,
    error: String?,
    scanning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = subnetText,
                onValueChange = onSubnetChanged,
                label = { Text("Private IPv4 subnet") },
                placeholder = { Text("192.168.1.0/24") },
                supportingText = { Text(error ?: "Maximum 254 hosts (/24)") },
                isError = error != null,
                enabled = !scanning,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LanScanMode.entries.forEach { option ->
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !scanning) { onModeChanged(option) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (mode == option) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    ) { Box(contentAlignment = Alignment.Center) { Text(option.label, fontWeight = FontWeight.Medium) } }
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !scanning) { onAuthorizedChanged(!authorized) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = authorized, onCheckedChange = onAuthorizedChanged, enabled = !scanning)
                Text("I own or am authorized to scan this local network", modifier = Modifier.weight(1f))
            }
            Button(
                onClick = if (scanning) onStop else onStart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(if (scanning) Icons.Outlined.StopCircle else Icons.Outlined.Lan, contentDescription = null)
                Text(if (scanning) "Stop scan" else "Start ${mode.label.lowercase()} scan", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun LanPermissionExplanation(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Lan, contentDescription = null) },
        title = { Text("Allow local network access") },
        text = {
            Text(
                "Android groups local network access with Nearby devices. NetworkTools uses it only while this " +
                    "screen is visibly scanning the private subnet shown. Device identities stay on this device.",
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun LanProgressPanel(state: LanScannerUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                Column(Modifier.weight(1f)) {
                    Text(state.phase, fontWeight = FontWeight.Medium)
                    Text("${state.scannedHosts} of ${state.totalHosts} hosts • ${state.devices.size} devices",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            LinearProgressIndicator(
                progress = { if (state.totalHosts == 0) 0f else state.scannedHosts.toFloat() / state.totalHosts },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun LanErrorPanel(message: String, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            if (message.contains("permission", ignoreCase = true) || message.contains("Connect", ignoreCase = true)) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Open app or network settings")
                }
            }
        }
    }
}

@Composable
private fun LanResultSummary(result: LanScanResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${result.devices.size} devices", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Text(if (result.stopped) "Stopped" else "Completed", color = if (result.stopped)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
            Text("${result.subnet} • ${result.mode.label} • ${result.scannedHosts} hosts",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.completedAtMillis)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LanEmptyState() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Lan, contentDescription = null, modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No LAN scan result", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun LanDeviceCard(device: LanDevice) {
    var expanded by rememberSaveable(device.ipAddress) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(deviceIcon(device), contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(device.displayName, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (device.gateway || device.thisDevice) {
                            Text(if (device.gateway) "Gateway" else "Local", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Text(device.ipAddress, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(device.protocols.joinToString { it.label }.ifBlank { "Active network identity" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Hide device details" else "Show device details")
                }
            }
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    LanDetailRow("IP address", device.ipAddress)
                    LanDetailRow("Hostname", device.hostname ?: "Unavailable")
                    LanDetailRow("MAC address", device.macAddress ?: "Unavailable")
                    LanDetailRow("Manufacturer", device.manufacturer ?: "Unknown")
                    LanDetailRow("Device type", device.deviceType)
                    LanDetailRow("Discovered by", device.protocols.joinToString { it.label }.ifBlank { "Active link" })
                    if (device.services.isNotEmpty()) LanDetailRow("Services", device.services.joinToString())
                    LanDetailRow("Last seen", DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(device.lastSeenMillis)))
                }
            }
        }
    }
}

@Composable
private fun LanDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(0.36f), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, modifier = Modifier.weight(0.64f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LanHistoryHeader(hasEntries: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.History, contentDescription = null)
        Text("LAN scan history", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp).weight(1f))
        TextButton(enabled = hasEntries, onClick = onClear) {
            Icon(Icons.Outlined.ClearAll, contentDescription = null)
            Text("Clear", Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun LanHistoryRow(entry: LanScanHistoryEntry, onOpen: () -> Unit, onRemove: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${entry.devices.size} devices • ${entry.mode.label}", fontWeight = FontWeight.Medium)
                Text(entry.subnet, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.completedAtMillis)) +
                    if (entry.stopped) " • Stopped" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove LAN scan history entry")
            }
        }
    }
}

private fun deviceIcon(device: LanDevice): ImageVector = when {
    device.gateway -> Icons.Outlined.Router
    device.thisDevice || device.deviceType.contains("Phone") -> Icons.Outlined.Smartphone
    device.deviceType.contains("Printer") -> Icons.Outlined.Print
    device.deviceType.contains("Computer") -> Icons.Outlined.Computer
    else -> Icons.Outlined.DevicesOther
}

private fun openLanSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    runCatching { context.startActivity(intent) }
}

private fun shareLanScan(context: Context, result: LanScanResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "LAN scan result")
        putExtra(Intent.EXTRA_TEXT, result.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share LAN scan"))
}
