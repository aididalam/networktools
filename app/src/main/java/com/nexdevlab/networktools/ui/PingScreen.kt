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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexdevlab.networktools.data.PingConfig
import com.nexdevlab.networktools.data.PingHistoryEntry
import com.nexdevlab.networktools.data.PingMethod
import com.nexdevlab.networktools.data.PingProbe
import com.nexdevlab.networktools.data.PingProbeStatus
import com.nexdevlab.networktools.data.PingRepository
import com.nexdevlab.networktools.data.PingSummary
import com.nexdevlab.networktools.data.PingViewModel
import com.nexdevlab.networktools.data.formatLatency
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PingScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: PingViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var method by rememberSaveable { mutableStateOf(PingMethod.ICMP) }
    var target by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf(PingConfig.DefaultPort.toString()) }
    var count by rememberSaveable { mutableStateOf(PingConfig.DefaultPacketCount.toString()) }
    var packetSize by rememberSaveable { mutableStateOf(PingConfig.DefaultPacketSize.toString()) }
    var timeout by rememberSaveable { mutableStateOf(PingConfig.DefaultTimeoutMillis.toString()) }
    var interval by rememberSaveable { mutableStateOf(PingConfig.DefaultIntervalMillis.toString()) }
    var errors by rememberSaveable { mutableStateOf(PingFormErrors()) }

    fun startPing() {
        val newErrors = validatePingForm(method, target, port, count, packetSize, timeout, interval)
        errors = newErrors
        if (newErrors.hasAny) return
        focusManager.clearFocus()
        viewModel.start(
            PingConfig(
                target = target,
                method = method,
                port = port.toInt(),
                packetCount = count.toInt(),
                packetSize = packetSize.toInt(),
                timeoutMillis = timeout.toInt(),
                intervalMillis = interval.toInt(),
            ),
        )
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Ping") },
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("Ping result", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Ping result copied") }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Ping result")
                    }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { sharePingResult(context, it) } },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share Ping result")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Send bounded ICMP packets or TCP connection probes to one hostname or IP address. " +
                            "TCP reports whether the selected port responds; TTL is available only for ICMP replies.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                PingForm(
                    method = method,
                    onMethodChange = { method = it; errors = PingFormErrors() },
                    target = target,
                    onTargetChange = { target = it.take(253); errors = errors.copy(target = null) },
                    port = port,
                    onPortChange = { port = it.numeric(5); errors = errors.copy(port = null) },
                    count = count,
                    onCountChange = { count = it.numeric(2); errors = errors.copy(count = null) },
                    packetSize = packetSize,
                    onPacketSizeChange = { packetSize = it.numeric(4); errors = errors.copy(packetSize = null) },
                    timeout = timeout,
                    onTimeoutChange = { timeout = it.numeric(4); errors = errors.copy(timeout = null) },
                    interval = interval,
                    onIntervalChange = { interval = it.numeric(4); errors = errors.copy(interval = null) },
                    errors = errors,
                    running = state.running,
                    onStart = ::startPing,
                    onStop = viewModel::stop,
                )
            }
            state.error?.let { error -> item { PingErrorPanel(error) } }
            if (state.running || state.probes.isNotEmpty()) {
                item {
                    PingLiveResults(
                        target = state.activeTarget ?: target,
                        probes = state.probes,
                        result = state.result,
                        running = state.running,
                    )
                }
            }
            item {
                PingHistoryHeader(state.history.isNotEmpty(), viewModel::clearHistory)
            }
            if (state.history.isEmpty()) {
                item { PingHistoryEmpty() }
            } else {
                items(state.history, key = PingHistoryEntry::id) { entry ->
                    PingHistoryRow(entry = entry, onDelete = { viewModel.removeHistory(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun PingForm(
    method: PingMethod,
    onMethodChange: (PingMethod) -> Unit,
    target: String,
    onTargetChange: (String) -> Unit,
    port: String,
    onPortChange: (String) -> Unit,
    count: String,
    onCountChange: (String) -> Unit,
    packetSize: String,
    onPacketSizeChange: (String) -> Unit,
    timeout: String,
    onTimeoutChange: (String) -> Unit,
    interval: String,
    onIntervalChange: (String) -> Unit,
    errors: PingFormErrors,
    running: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PingMethodPicker(method, onMethodChange, enabled = !running)
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                label = { Text("Hostname or IP address") },
                placeholder = { Text("example.com or 8.8.8.8") },
                isError = errors.target != null,
                supportingText = errors.target?.let { message -> { Text(message) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!running) onStart() }),
            )
            if (method == PingMethod.TCP) {
                PingNumberField(
                    port, onPortChange, "TCP port", "1-65535", errors.port, !running, Modifier.fillMaxWidth(),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PingNumberField(
                    count, onCountChange, "Packet count", "1-10", errors.count, !running, Modifier.weight(1f),
                )
                PingNumberField(
                    if (method == PingMethod.ICMP) packetSize else "N/A",
                    onPacketSizeChange,
                    "Size (bytes)",
                    if (method == PingMethod.ICMP) "16-1400" else "ICMP only",
                    errors.packetSize,
                    !running && method == PingMethod.ICMP,
                    Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PingNumberField(
                    timeout, onTimeoutChange, "Timeout (ms)", "500-5000", errors.timeout, !running, Modifier.weight(1f),
                )
                PingNumberField(
                    interval, onIntervalChange, "Interval (ms)", "250-2000", errors.interval, !running, Modifier.weight(1f),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(
                        "Run diagnostics only on systems you own or are authorized to test.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (running) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Stop, contentDescription = null)
                    Text("Stop Ping", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text("Start Ping", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun PingMethodPicker(method: PingMethod, onSelected: (PingMethod) -> Unit, enabled: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PingMethod.entries.forEach { choice ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = enabled) { onSelected(choice) },
                shape = RoundedCornerShape(8.dp),
                color = if (method == choice) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                contentColor = if (method == choice) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(choice.label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun PingNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    range: String,
    error: String?,
    enabled: Boolean,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        supportingText = { Text(error ?: range) },
        isError = error != null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
    )
}

@Composable
private fun PingLiveResults(target: String, probes: List<PingProbe>, result: PingSummary?, running: Boolean) {
    val sent = probes.size
    val received = probes.count(PingProbe::received)
    val latencies = probes.mapNotNull(PingProbe::latencyMillis)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(target, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(
                    when {
                        running -> "Ping in progress"
                        result?.stopped == true -> "Stopped"
                        else -> "Completed"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                probes.forEachIndexed { index, probe ->
                    PingProbeRow(probe)
                    if (index < probes.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PingStat("Sent", sent.toString(), Modifier.weight(1f))
                PingStat("Received", received.toString(), Modifier.weight(1f))
                PingStat("Lost", (sent - received).toString(), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PingStat("Loss", if (sent == 0) "0%" else String.format(Locale.US, "%.1f%%", (sent - received) * 100.0 / sent), Modifier.weight(1f))
                PingStat("Minimum", latencies.minOrNull()?.let { "${formatLatency(it)} ms" } ?: "--", Modifier.weight(1f))
                PingStat("Maximum", latencies.maxOrNull()?.let { "${formatLatency(it)} ms" } ?: "--", Modifier.weight(1f))
            }
            PingStat(
                "Average latency",
                latencies.takeIf { it.isNotEmpty() }?.average()?.let { "${formatLatency(it)} ms" } ?: "Unavailable",
                Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PingProbeRow(probe: PingProbe) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#${probe.sequence}", fontFamily = FontFamily.Monospace, modifier = Modifier.weight(0.18f))
        Column(Modifier.weight(0.5f)) {
            Text(probe.status.label, fontWeight = FontWeight.Medium)
            Text(probe.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.weight(0.32f), horizontalAlignment = Alignment.End) {
            Text(probe.latencyMillis?.let { "${formatLatency(it)} ms" } ?: "--", fontFamily = FontFamily.Monospace)
            Text(
                probe.ttl?.let { "TTL $it" } ?: if (probe.status == PingProbeStatus.REPLY) "TTL unavailable" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PingStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
    }
}

@Composable
private fun PingHistoryHeader(hasEntries: Boolean, onClear: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.History, contentDescription = null, modifier = Modifier.size(21.dp))
        Text("History", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp).weight(1f))
        TextButton(onClick = onClear, enabled = hasEntries) {
            Icon(Icons.Outlined.ClearAll, contentDescription = null)
            Text("Clear", modifier = Modifier.padding(start = 5.dp))
        }
    }
}

@Composable
private fun PingHistoryEmpty() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Text(
            "Completed and stopped Ping runs will appear here.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PingHistoryRow(entry: PingHistoryEntry, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.target, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(
                    "${entry.method.label}${entry.port?.let { ":$it" }.orEmpty()}  ${entry.receivedCount}/${entry.sentCount} received  " +
                        String.format(Locale.US, "%.1f%% loss", entry.lossPercentage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.completedAtMillis)) +
                        if (entry.stopped) "  Stopped" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Ping history entry")
            }
        }
    }
}

@Composable
private fun PingErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private data class PingFormErrors(
    val target: String? = null,
    val port: String? = null,
    val count: String? = null,
    val packetSize: String? = null,
    val timeout: String? = null,
    val interval: String? = null,
) : java.io.Serializable {
    val hasAny: Boolean get() = listOf(target, port, count, packetSize, timeout, interval).any { it != null }
}

private fun validatePingForm(
    method: PingMethod,
    target: String,
    port: String,
    count: String,
    packetSize: String,
    timeout: String,
    interval: String,
): PingFormErrors = PingFormErrors(
    target = runCatching { PingRepository.normalizeTarget(target) }.exceptionOrNull()?.message,
    port = if (method == PingMethod.TCP) numberError(port, PingConfig.PortRange, "Port") else null,
    count = numberError(count, PingConfig.PacketCountRange, "Packet count"),
    packetSize = if (method == PingMethod.ICMP) numberError(packetSize, PingConfig.PacketSizeRange, "Packet size") else null,
    timeout = numberError(timeout, PingConfig.TimeoutRange, "Timeout"),
    interval = numberError(interval, PingConfig.IntervalRange, "Interval"),
)

private fun numberError(value: String, range: IntRange, label: String): String? {
    val number = value.toIntOrNull() ?: return "$label is required"
    return if (number in range) null else "$label must be ${range.first}-${range.last}"
}

private fun String.numeric(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private fun sharePingResult(context: Context, result: PingSummary) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Ping result for ${result.target}")
        putExtra(Intent.EXTRA_TEXT, result.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share Ping result"))
}
