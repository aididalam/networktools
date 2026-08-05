package com.nexdevlab.networktools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.SpeedServerChoice
import com.nexdevlab.networktools.data.SpeedServerMode
import com.nexdevlab.networktools.data.SpeedEndpointChoice
import com.nexdevlab.networktools.data.SpeedTestPhase
import com.nexdevlab.networktools.data.SpeedTestResult
import com.nexdevlab.networktools.data.SpeedTestUiState
import com.nexdevlab.networktools.data.SpeedTestViewModel
import com.nexdevlab.networktools.data.formatMilliseconds
import com.nexdevlab.networktools.data.formatSpeed
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SpeedView(val label: String) { TEST("Test"), HISTORY("History") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternetSpeedMeterScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: SpeedTestViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var selectedView by rememberSaveable { mutableStateOf(SpeedView.TEST) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    DisposableEffect(viewModel) {
        viewModel.refreshNetwork()
        onDispose(viewModel::onScreenClosed)
    }
    if (state.consentRequired) SpeedConsentDialog(state, viewModel)
    if (state.meteredConfirmationRequired) MeteredNetworkDialog(viewModel)
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear Speed Test History?") },
            text = { Text("All locally stored speed-test summaries will be removed.") },
            confirmButton = {
                Button(onClick = { viewModel.clearHistory(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Internet Speed Meter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.result != null,
                        onClick = {
                            copySpeedResult(context, requireNotNull(state.result))
                            scope.launch { snackbar.showSnackbar("Speed-test result copied") }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy result") }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { shareSpeedResult(context, requireNotNull(state.result)) },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share result") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Choose a country or region, then keep Server on Automatic for the lowest-latency candidate or select a specific available server. " +
                            "Local / nearest uses the closest network edge. Stop ends the active test without saving a partial result.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SpeedViewSegments(selectedView) { selectedView = it } }
            if (selectedView == SpeedView.TEST) {
                item { SpeedMeterPanel(state) }
                item { SpeedCountrySelector(state, viewModel::selectServer) }
                item { SpeedEndpointSelector(state, viewModel::selectEndpoint) }
                item { SpeedTestControls(state, viewModel::requestStart, viewModel::stop, viewModel::refreshNetwork) }
                state.error?.let { item { SpeedNotice(it, error = true) } }
                state.result?.let { item { SpeedResultGrid(it) } }
                if (state.releaseBlocked) item { SpeedReleaseNotice() }
            } else {
                if (state.history.isNotEmpty()) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { confirmClear = true }) {
                                Icon(Icons.Outlined.DeleteSweep, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Clear history")
                            }
                        }
                    }
                }
                if (state.history.isEmpty()) {
                    item { SpeedHistoryEmpty() }
                } else {
                    items(state.history, key = SpeedTestResult::completedAtMillis) { result ->
                        SpeedHistoryCard(result, onDelete = { viewModel.removeHistory(result.completedAtMillis) })
                    }
                }
                item { SpeedNotice("Up to 20 summaries are stored only on this device and excluded from backup and transfer.") }
            }
        }
    }
}

@Composable
private fun SpeedViewSegments(selected: SpeedView, onSelected: (SpeedView) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        SpeedView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(view) },
                shape = RoundedCornerShape(8.dp),
                color = if (view == selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) { Box(contentAlignment = Alignment.Center) { Text(view.label, fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
private fun SpeedMeterPanel(state: SpeedTestUiState) {
    val value = if (state.running) state.liveMbps else state.downloadMbps ?: 0.0
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0.0, 100_000.0).toFloat(),
        animationSpec = tween(
            durationMillis = if (state.running) 220 else 360,
            easing = FastOutSlowInEasing,
        ),
        label = "Live speed",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(state.phase.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        state.activeServer?.displayLocation ?: "Secure speed test",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    if (state.running) "${state.elapsedMillis / 1_000}s" else state.networkType,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(34.dp))
            Text(
                text = if (state.phase == SpeedTestPhase.UPLOAD) "Your upload speed is" else "Your internet speed is",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                modifier = Modifier.fillMaxWidth().height(98.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = formatSpeed(animatedValue.toDouble()),
                    fontSize = 72.sp,
                    lineHeight = 80.sp,
                    fontWeight = FontWeight.Light,
                    maxLines = 1,
                )
                Text(
                    text = "Mbps",
                    modifier = Modifier.padding(start = 10.dp, bottom = 13.dp),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when (state.phase) {
                    SpeedTestPhase.DISCOVERING -> "Selecting the best server"
                    SpeedTestPhase.DOWNLOAD -> "Measuring download"
                    SpeedTestPhase.UPLOAD -> "Measuring upload"
                    SpeedTestPhase.COMPLETE -> "Test complete"
                    SpeedTestPhase.STOPPED -> "Test stopped"
                    else -> "Ready when you are"
                },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(22.dp))
            if (state.running) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun SpeedInlineMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedCountrySelector(state: SpeedTestUiState, onSelected: (SpeedServerChoice) -> Unit) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    val choices = remember { SpeedServerChoice.all() }
    val keyboard = LocalSoftwareKeyboardController.current
    val localCountryName = remember(state.localCountryCode) {
        state.localCountryCode.takeIf { it.length == 2 }?.let { code ->
            Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
        }.orEmpty()
    }
    val filteredChoices = remember(search, choices, localCountryName, state.localCountryCode, state.localEdgeName) {
        val query = search.trim()
        if (query.isEmpty()) choices else choices.filter { choice ->
            choice.label.contains(query, ignoreCase = true) ||
                choice.countryCode?.contains(query, ignoreCase = true) == true ||
                (choice.mode == SpeedServerMode.LOCAL && listOf(
                    localCountryName,
                    state.localCountryCode,
                    state.localEdgeName,
                    "local nearest nearby",
                ).any { it.contains(query, ignoreCase = true) })
        }
    }
    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false; search = ""; keyboard?.hide() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 420.dp, max = 680.dp)
                    .imePadding(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Choose country or region", style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold)
                        Text("All countries · servers load live", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { sheetOpen = false; search = ""; keyboard?.hide() }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close countries")
                    }
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(60) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .testTag("speed_country_search"),
                    singleLine = true,
                    label = { Text("Search countries or codes") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = if (search.isNotEmpty()) {
                        {
                            IconButton(onClick = { search = "" }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear country search")
                            }
                        }
                    } else null,
                )
                Text(
                    "${filteredChoices.size} ${if (filteredChoices.size == 1) "result" else "results"}",
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                LazyColumn(Modifier.fillMaxWidth().weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
                    items(filteredChoices, key = { "${it.mode}:${it.countryCode.orEmpty()}" }) { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("speed_country_choice_${choice.mode.name.lowercase(Locale.US)}")
                                .clickable {
                                    onSelected(choice)
                                    sheetOpen = false
                                    search = ""
                                    keyboard?.hide()
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(speedChoiceTitle(choice, localCountryName), style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium)
                                Text(
                                    speedChoiceSubtitle(choice, state),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (choice == state.selectedServer) {
                                Icon(Icons.Outlined.Check, contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (filteredChoices.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth().height(160.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(Icons.Outlined.Search, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("No countries found", Modifier.padding(top = 8.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.running) { sheetOpen = true }
            .testTag("speed_country_selector"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Country / region", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(speedChoiceTitle(state.selectedServer, localCountryName), fontWeight = FontWeight.Medium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose country or region")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedEndpointSelector(state: SpeedTestUiState, onSelected: (SpeedEndpointChoice) -> Unit) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val localCountryName = remember(state.localCountryCode) {
        state.localCountryCode.takeIf { it.length == 2 }?.let { code ->
            Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault())
        }.orEmpty()
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 620.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Choose test server", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            speedChoiceTitle(state.selectedServer, localCountryName),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { sheetOpen = false }) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close servers")
                    }
                }
                HorizontalDivider()
                if (state.serversLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Finding available servers", Modifier.padding(start = 12.dp))
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        items(state.serverOptions, key = { it.machine ?: "automatic" }) { choice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(if (choice.machine == null) "speed_endpoint_automatic" else "speed_endpoint_server")
                                    .clickable {
                                        onSelected(choice)
                                        sheetOpen = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(choice.label, style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium)
                                    Text(
                                        endpointSubtitle(choice, state),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (choice == state.selectedEndpoint) {
                                    Icon(Icons.Outlined.Check, contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.running) { sheetOpen = true }
            .testTag("speed_endpoint_selector"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Server", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    if (state.serversLoading) "Finding available servers" else state.selectedEndpoint.label,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val status = state.serverOptionsError ?: endpointSubtitle(state.selectedEndpoint, state)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.serverOptionsError == null) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.serversLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = "Choose test server")
            }
        }
    }
}

private fun endpointSubtitle(choice: SpeedEndpointChoice, state: SpeedTestUiState): String = when {
    choice.machine != null -> listOf(choice.city, choice.countryCode).filter(String::isNotBlank).joinToString(", ")
        .ifBlank { "Specific server" }
    state.selectedServer.mode == SpeedServerMode.LOCAL -> listOf(state.localEdgeName, state.localCountryCode)
        .filter(String::isNotBlank).joinToString(" · ").ifBlank { "Nearest edge selected automatically" }
    state.serversLoading -> "Checking current availability"
    state.regionalServerFallback -> "No in-country node · selects the lowest-latency regional server"
    state.serverOptions.size > 1 -> "Tests ${state.serverOptions.size - 1} candidates and selects the fastest"
    else -> "Server candidates load for the selected country"
}

private fun speedChoiceTitle(choice: SpeedServerChoice, localCountryName: String): String = when {
    choice.mode == SpeedServerMode.LOCAL && localCountryName.isNotBlank() -> "$localCountryName · Local / nearest"
    else -> choice.label
}

private fun speedChoiceSubtitle(choice: SpeedServerChoice, state: SpeedTestUiState): String = when (choice.mode) {
    SpeedServerMode.LOCAL -> listOf(state.localEdgeName, state.localCountryCode)
        .filter(String::isNotBlank)
        .joinToString(" · ")
        .ifBlank { "Detects the closest edge automatically" }
    SpeedServerMode.AUTOMATIC -> "Lowest-latency global research server"
    SpeedServerMode.COUNTRY -> "${choice.countryCode} · Live provider discovery"
}

@Composable
private fun SpeedTestControls(state: SpeedTestUiState, onStart: () -> Unit, onStop: () -> Unit, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = if (state.running) onStop else onStart,
            enabled = !state.releaseBlocked,
            modifier = Modifier.weight(1f).height(52.dp).testTag("speed_test_action"),
        ) {
            Icon(if (state.running) Icons.Outlined.Speed else Icons.Outlined.Speed, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (state.running) "Stop test" else if (state.phase == SpeedTestPhase.COMPLETE) "Test again" else "Start test")
        }
        OutlinedButton(onClick = onRefresh, enabled = !state.running, modifier = Modifier.height(52.dp)) {
            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh network")
        }
    }
}

@Composable
private fun SpeedResultGrid(result: SpeedTestResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SpeedInlineMetric("Download", "${formatSpeed(result.downloadMbps)} Mbps")
            SpeedInlineMetric("Upload", "${formatSpeed(result.uploadMbps)} Mbps")
            SpeedInlineMetric("Latency", result.latencyMillis?.let(::formatMilliseconds) ?: "Unavailable")
            SpeedInlineMetric("Jitter", result.jitterMillis?.let(::formatMilliseconds) ?: "Unavailable")
        }
    }
}

@Composable
private fun SpeedReleaseNotice() {
    SpeedNotice(
        "Release locked until the app privacy-policy URL is configured.",
        error = true,
    )
}

@Composable
private fun SpeedNotice(message: String, error: Boolean = false) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp),
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeedHistoryEmpty() {
    Surface(Modifier.fillMaxWidth().height(170.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No speed tests yet", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SpeedHistoryCard(result: SpeedTestResult, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Speed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${formatSpeed(result.downloadMbps)} down  ·  ${formatSpeed(result.uploadMbps)} up",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOf(result.serverCity, result.serverCountryCode).filter(String::isNotBlank).joinToString(", ")
                        .ifBlank { "Test server" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatSpeedTime(result.completedAtMillis), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, contentDescription = "Delete history entry") }
        }
    }
}

@Composable
private fun SpeedConsentDialog(state: SpeedTestUiState, viewModel: SpeedTestViewModel) {
    val uriHandler = LocalUriHandler.current
    var accepted by rememberSaveable { mutableStateOf(false) }
    val local = state.selectedServer.mode == SpeedServerMode.LOCAL
    AlertDialog(
        onDismissRequest = viewModel::dismissConsent,
        title = { Text("Before Your Speed Test") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This foreground test sends and receives synthetic data for about 20 seconds. It can use substantial data.")
                Text(
                    if (local) {
                        "The nearest-edge service receives your public IP address and request metadata. " +
                            "This app does not upload your result summary or local history."
                    } else {
                        "The third-party measurement service receives and publicly publishes measurement data, " +
                            "including your public IP address, test time, and network performance."
                    },
                )
                Row(Modifier.fillMaxWidth().clickable { accepted = !accepted }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = accepted, onCheckedChange = { accepted = it })
                    Text("I understand and consent to this test", Modifier.padding(start = 6.dp))
                }
                Row {
                    TextButton(onClick = {
                        uriHandler.openUri(
                            if (local) "https://www.cloudflare.com/privacypolicy/"
                            else "https://www.measurementlab.net/privacy/",
                        )
                    }) {
                        Text("Provider privacy")
                    }
                    TextButton(onClick = {
                        uriHandler.openUri(
                            if (local) "https://www.cloudflare.com/website-terms/"
                            else "https://www.measurementlab.net/aup/",
                        )
                    }) {
                        Text("Acceptable use")
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = accepted, onClick = viewModel::acceptConsentAndContinue) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = viewModel::dismissConsent) { Text("Cancel") } },
    )
}

@Composable
private fun MeteredNetworkDialog(viewModel: SpeedTestViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::dismissMeteredConfirmation,
        title = { Text("Use Metered Data?") },
        text = { Text("This speed test transfers a substantial amount of data. Your carrier or network provider may charge for it.") },
        confirmButton = { Button(onClick = viewModel::confirmMeteredAndStart) { Text("Start anyway") } },
        dismissButton = { TextButton(onClick = viewModel::dismissMeteredConfirmation) { Text("Cancel") } },
    )
}

private val SpeedTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
private fun formatSpeedTime(value: Long): String =
    Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).format(SpeedTimeFormatter)

private fun copySpeedResult(context: Context, result: SpeedTestResult) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Internet Speed Test", result.asPlainText()))
}

private fun shareSpeedResult(context: Context, result: SpeedTestResult) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Internet Speed Test")
                putExtra(Intent.EXTRA_TEXT, result.asPlainText())
            },
            "Share Internet Speed Test",
        ),
    )
}
