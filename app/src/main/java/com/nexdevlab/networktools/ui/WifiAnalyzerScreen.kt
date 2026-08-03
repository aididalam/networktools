package com.nexdevlab.networktools.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.SavedWifiNetwork
import com.nexdevlab.networktools.data.WifiAccessPoint
import com.nexdevlab.networktools.data.WifiAnalyzerRepository
import com.nexdevlab.networktools.data.WifiAnalyzerUiState
import com.nexdevlab.networktools.data.WifiAnalyzerView
import com.nexdevlab.networktools.data.WifiAnalyzerViewModel
import com.nexdevlab.networktools.data.WifiBand
import com.nexdevlab.networktools.data.WifiChannelScore
import com.nexdevlab.networktools.data.WifiScanSnapshot
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WifiAnalyzerScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: WifiAnalyzerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedView by rememberSaveable { mutableStateOf(WifiAnalyzerView.NETWORKS) }
    var showPermissionExplanation by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && WifiAnalyzerRepository.hasScanPermission(context)) {
            viewModel.scan()
        } else {
            scope.launch { snackbar.showSnackbar("Wi-Fi scan permission was not granted") }
        }
    }

    fun requestScan() {
        if (WifiAnalyzerRepository.hasScanPermission(context)) viewModel.scan()
        else showPermissionExplanation = true
    }

    DisposableEffect(viewModel, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.cancelScan()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.cancelScan()
        }
    }

    if (showPermissionExplanation) {
        WifiPermissionExplanation(
            onContinue = {
                showPermissionExplanation = false
                permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            },
            onDismiss = { showPermissionExplanation = false },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Wi-Fi Analyzer") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(enabled = !state.scanning, onClick = ::requestScan) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Scan nearby Wi-Fi networks")
                    }
                    IconButton(
                        enabled = state.snapshot != null,
                        onClick = {
                            state.snapshot?.let { snapshot ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Wi-Fi scan", snapshot.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Wi-Fi scan copied") }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Wi-Fi scan")
                    }
                    IconButton(
                        enabled = state.snapshot != null,
                        onClick = { state.snapshot?.let { shareWifiScan(context, it) } },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share Wi-Fi scan")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Start a foreground scan to inspect nearby access points. Android may return cached " +
                            "results when scans are throttled. Saved entries contain network identity only, never passwords.",
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
            item {
                WifiAnalyzerViewPicker(selectedView, onSelected = { selectedView = it })
            }
            if (state.scanning) {
                item { WifiScanningPanel() }
            }
            state.error?.let { error ->
                item {
                    WifiAnalyzerErrorPanel(
                        message = error,
                        onOpenSettings = { openWifiRecoverySettings(context, error) },
                    )
                }
            }
            when (selectedView) {
                WifiAnalyzerView.NETWORKS -> wifiNetworksContent(
                    state = state,
                    onScan = ::requestScan,
                    onToggleSaved = viewModel::toggleSaved,
                    onToggleComparison = viewModel::toggleComparison,
                )
                WifiAnalyzerView.CHANNELS -> wifiChannelsContent(state.snapshot, onScan = ::requestScan)
                WifiAnalyzerView.SAVED -> wifiSavedContent(
                    saved = state.savedNetworks,
                    onRemove = viewModel::removeSaved,
                    onClear = viewModel::clearSaved,
                )
            }
        }
    }
}

@Composable
private fun WifiAnalyzerViewPicker(selected: WifiAnalyzerView, onSelected: (WifiAnalyzerView) -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        WifiAnalyzerView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(view) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == view) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected == view) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(view.label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.wifiNetworksContent(
    state: WifiAnalyzerUiState,
    onScan: () -> Unit,
    onToggleSaved: (WifiAccessPoint) -> Unit,
    onToggleComparison: (String) -> Unit,
) {
    val snapshot = state.snapshot
    if (snapshot == null && !state.scanning) {
        item { WifiAnalyzerEmptyState(onScan) }
        return
    }
    snapshot ?: return
    item { WifiScanSummary(snapshot) }
    val comparison = snapshot.accessPoints.filter { it.bssid in state.comparisonBssids }
    if (comparison.isNotEmpty()) {
        item { WifiComparisonPanel(comparison) }
    }
    items(snapshot.accessPoints, key = WifiAccessPoint::bssid) { accessPoint ->
        WifiAccessPointCard(
            accessPoint = accessPoint,
            comparisonSelected = accessPoint.bssid in state.comparisonBssids,
            comparisonEnabled = accessPoint.bssid in state.comparisonBssids || state.comparisonBssids.size < 2,
            onToggleSaved = { onToggleSaved(accessPoint) },
            onToggleComparison = { onToggleComparison(accessPoint.bssid) },
        )
    }
    if (snapshot.accessPoints.isEmpty() && !state.scanning) item { WifiNoResultsState(onScan) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.wifiChannelsContent(
    snapshot: WifiScanSnapshot?,
    onScan: () -> Unit,
) {
    if (snapshot == null) {
        item { WifiAnalyzerEmptyState(onScan) }
        return
    }
    if (snapshot.accessPoints.isEmpty()) {
        item { WifiNoResultsState(onScan) }
        return
    }
    item { WifiChannelAnalysis(snapshot.accessPoints) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.wifiSavedContent(
    saved: List<SavedWifiNetwork>,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
) {
    item { WifiSavedHeader(saved.isNotEmpty(), onClear) }
    if (saved.isEmpty()) {
        item { WifiSavedEmpty() }
    } else {
        items(saved, key = SavedWifiNetwork::bssid) { network ->
            WifiSavedRow(network, onRemove = { onRemove(network.bssid) })
        }
    }
}

@Composable
private fun WifiPermissionExplanation(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Wifi, contentDescription = null) },
        title = { Text("Allow Wi-Fi scanning") },
        text = {
            Text(
                "Android requires precise location permission and enabled Location services to return nearby " +
                    "Wi-Fi scan results. Network names and BSSIDs stay on this device and are not used to determine your location.",
            )
        },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun WifiScanningPanel() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Column {
                Text("Scanning nearby networks", fontWeight = FontWeight.Medium)
                Text("Waiting for Android scan results", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WifiAnalyzerEmptyState(onScan: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(
            Modifier.fillMaxWidth().padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("Ready to scan", style = MaterialTheme.typography.titleLarge)
            Text(
                "No scan runs until you press the button below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Refresh, contentDescription = null)
                Text("Scan nearby Wi-Fi", modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun WifiNoResultsState(onScan: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("No access points found", style = MaterialTheme.typography.titleMedium)
            Text("Move closer to a Wi-Fi network or try again after Android's scan interval.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onScan) { Text("Scan again") }
        }
    }
}

@Composable
private fun WifiScanSummary(snapshot: WifiScanSnapshot) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${snapshot.accessPoints.size} access points", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f))
                Text(
                    if (snapshot.stale) "Cached" else "Current",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (snapshot.stale) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(snapshot.scannedAtMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            snapshot.notice?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun WifiAccessPointCard(
    accessPoint: WifiAccessPoint,
    comparisonSelected: Boolean,
    comparisonEnabled: Boolean,
    onToggleSaved: () -> Unit,
    onToggleComparison: () -> Unit,
) {
    var expanded by rememberSaveable(accessPoint.bssid) { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = 14.dp, top = 12.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Wifi, contentDescription = null, modifier = Modifier.size(27.dp),
                    tint = signalColor(accessPoint.levelDbm))
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(accessPoint.ssid, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (accessPoint.connected) {
                            Text("Connected", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                    Text(
                        "${accessPoint.levelDbm} dBm • ${accessPoint.signalLabel} • Ch ${accessPoint.channel} • ${accessPoint.band.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onToggleSaved) {
                    Icon(
                        if (accessPoint.saved) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (accessPoint.saved) "Remove saved network" else "Save network",
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (expanded) "Hide network details" else "Show network details",
                    )
                }
            }
            LinearProgressIndicator(
                progress = { accessPoint.signalQuality / 100f },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = signalColor(accessPoint.levelDbm),
            )
            if (expanded) {
                HorizontalDivider()
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    WifiDetailRow("BSSID", accessPoint.bssid)
                    WifiDetailRow("Frequency", "${accessPoint.frequencyMhz} MHz")
                    WifiDetailRow("Channel width", accessPoint.channelWidth)
                    WifiDetailRow("Security", accessPoint.security)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = comparisonSelected,
                            onCheckedChange = { onToggleComparison() },
                            enabled = comparisonEnabled,
                        )
                        Text("Compare this access point", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WifiDetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(0.62f))
    }
}

@Composable
private fun WifiComparisonPanel(accessPoints: List<WifiAccessPoint>) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Access point comparison", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                accessPoints.forEach { accessPoint ->
                    Column(Modifier.weight(1f)) {
                        Text(accessPoint.ssid, fontWeight = FontWeight.Medium, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                        Text("${accessPoint.levelDbm} dBm", fontFamily = FontFamily.Monospace)
                        Text("Ch ${accessPoint.channel} • ${accessPoint.band.label}", style = MaterialTheme.typography.bodySmall)
                        Text(accessPoint.security, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (accessPoints.size == 1) {
                    Text("Select one more access point", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun WifiChannelAnalysis(accessPoints: List<WifiAccessPoint>) {
    val bands = accessPoints.map(WifiAccessPoint::band).filter { it != WifiBand.UNKNOWN }.distinct()
    var band by rememberSaveable(bands) { mutableStateOf(bands.firstOrNull() ?: WifiBand.BAND_2_4_GHZ) }
    val selectedBand = band.takeIf { it in bands } ?: bands.firstOrNull() ?: WifiBand.BAND_2_4_GHZ
    val scores = WifiAnalyzerRepository.channelScores(accessPoints, selectedBand)
    val mostCongested = scores.maxWithOrNull(compareBy<WifiChannelScore> { it.score }.thenBy { -it.channel })
    val leastCongested = scores.minWithOrNull(compareBy<WifiChannelScore> { it.score }.thenBy(WifiChannelScore::channel))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            bands.forEach { availableBand ->
                Surface(
                    modifier = Modifier.weight(1f).height(42.dp).clickable { band = availableBand },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedBand == availableBand) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (selectedBand == availableBand) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                ) { Box(contentAlignment = Alignment.Center) { Text(availableBand.label, style = MaterialTheme.typography.labelMedium) } }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WifiChannelSummary("Most congested", mostCongested?.let { "Channel ${it.channel}" } ?: "Unavailable",
                mostCongested?.let { "${it.accessPointCount} overlapping APs" }.orEmpty(), Modifier.weight(1f))
            WifiChannelSummary("Lower congestion", leastCongested?.let { "Channel ${it.channel}" } ?: "Unavailable",
                leastCongested?.let { "Score ${String.format(Locale.US, "%.2f", it.score)}" }.orEmpty(), Modifier.weight(1f))
        }
        WifiChannelGraph(scores)
        Text("Channel load", style = MaterialTheme.typography.titleMedium)
        scores.sortedByDescending(WifiChannelScore::score).forEach { score ->
            WifiChannelScoreRow(score, maximumScore = scores.maxOfOrNull(WifiChannelScore::score) ?: 1.0)
        }
    }
}

@Composable
private fun WifiChannelSummary(label: String, value: String, detail: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WifiChannelGraph(scores: List<WifiChannelScore>) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val maximum = scores.maxOfOrNull(WifiChannelScore::score)?.takeIf { it > 0 } ?: 1.0
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(top = 14.dp, bottom = 10.dp)) {
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(2.5f).padding(horizontal = 14.dp).semantics {
                    contentDescription = "Channel usage graph for ${scores.size} channels"
                },
            ) {
                drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                if (scores.isEmpty()) return@Canvas
                val slot = size.width / scores.size
                scores.forEachIndexed { index, score ->
                    val height = (score.score / maximum).toFloat().coerceIn(0.03f, 1f) * size.height
                    val barWidth = (slot * 0.54f).coerceAtLeast(3.dp.toPx())
                    drawRoundRect(
                        color = primary,
                        topLeft = Offset(slot * index + (slot - barWidth) / 2, size.height - height),
                        size = Size(barWidth, height),
                        cornerRadius = CornerRadius(minOf(barWidth / 2, height / 2)),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 5.dp)) {
                scores.forEach { score ->
                    Text(
                        score.channel.toString(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun WifiChannelScoreRow(score: WifiChannelScore, maximumScore: Double) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text("Channel ${score.channel}", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Text("${score.accessPointCount} overlaps", style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(
                progress = { (score.score / maximumScore.coerceAtLeast(0.01)).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WifiSavedHeader(hasEntries: Boolean, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Star, contentDescription = null, modifier = Modifier.size(21.dp))
            Text("Saved in NetworkTools", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp).weight(1f))
            TextButton(onClick = onClear, enabled = hasEntries) {
                Icon(Icons.Outlined.ClearAll, contentDescription = null)
                Text("Clear", modifier = Modifier.padding(start = 5.dp))
            }
        }
        Text("Network identity only; Wi-Fi passwords are never accessed or stored.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WifiSavedEmpty() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Text("Use the star beside an access point to save it here.", modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WifiSavedRow(network: SavedWifiNetwork, onRemove: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(network.ssid, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(network.bssid, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text("${network.band.label} • Ch ${network.channel} • ${network.security}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Last seen ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(network.lastSeenMillis))}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove saved Wi-Fi network")
            }
        }
    }
}

@Composable
private fun WifiAnalyzerErrorPanel(message: String, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (message.contains("Turn on")) {
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Open system settings")
                }
            }
        }
    }
}

@Composable
private fun signalColor(levelDbm: Int) = when {
    levelDbm >= -55 -> MaterialTheme.colorScheme.primary
    levelDbm >= -70 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

private fun openWifiRecoverySettings(context: Context, error: String) {
    val action = if (error.contains("Location")) {
        Settings.ACTION_LOCATION_SOURCE_SETTINGS
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Settings.Panel.ACTION_WIFI
    } else {
        Settings.ACTION_WIFI_SETTINGS
    }
    runCatching { context.startActivity(Intent(action)) }
}

private fun shareWifiScan(context: Context, snapshot: WifiScanSnapshot) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Wi-Fi Analyzer result")
        putExtra(Intent.EXTRA_TEXT, snapshot.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share Wi-Fi scan"))
}
