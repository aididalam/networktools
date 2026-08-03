package com.nexdevlab.networktools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.TraceAttemptStatus
import com.nexdevlab.networktools.data.TracerouteConfig
import com.nexdevlab.networktools.data.TracerouteHistoryEntry
import com.nexdevlab.networktools.data.TracerouteHop
import com.nexdevlab.networktools.data.TraceroutePhase
import com.nexdevlab.networktools.data.TracerouteResult
import com.nexdevlab.networktools.data.TracerouteViewModel
import com.nexdevlab.networktools.data.formatTraceTime
import com.nexdevlab.networktools.data.PingRepository
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: TracerouteViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var target by rememberSaveable { mutableStateOf("") }
    var maximumHops by rememberSaveable { mutableStateOf(TracerouteConfig.DefaultMaxHops.toString()) }
    var attempts by rememberSaveable { mutableStateOf(TracerouteConfig.DefaultAttempts.toString()) }
    var timeout by rememberSaveable { mutableStateOf(TracerouteConfig.DefaultTimeoutSeconds.toString()) }
    var errors by rememberSaveable { mutableStateOf(TracerouteFormErrors()) }

    fun startTrace() {
        val newErrors = validateTracerouteForm(target, maximumHops, attempts, timeout)
        errors = newErrors
        if (newErrors.hasAny) return
        focusManager.clearFocus()
        viewModel.start(
            TracerouteConfig(
                target = target,
                maxHops = maximumHops.toInt(),
                attemptsPerHop = attempts.toInt(),
                timeoutSeconds = timeout.toInt(),
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
                title = { Text("Traceroute") },
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("Traceroute result", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Traceroute result copied") }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Traceroute result")
                    }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { shareTracerouteResult(context, it) } },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share Traceroute result")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Trace one hostname or IP address through successive network hops. " +
                            "Asterisks mark attempts that did not answer; geographic positions are approximate and only public hops are mapped.",
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
                TracerouteForm(
                    target = target,
                    onTargetChange = { target = it.take(253); errors = errors.copy(target = null) },
                    maximumHops = maximumHops,
                    onMaximumHopsChange = { maximumHops = it.numeric(2); errors = errors.copy(maximumHops = null) },
                    attempts = attempts,
                    onAttemptsChange = { attempts = it.numeric(1); errors = errors.copy(attempts = null) },
                    timeout = timeout,
                    onTimeoutChange = { timeout = it.numeric(1); errors = errors.copy(timeout = null) },
                    errors = errors,
                    running = state.running,
                    onStart = ::startTrace,
                    onStop = viewModel::stop,
                )
            }
            state.error?.let { error -> item { TracerouteErrorPanel(error) } }
            if (state.running || state.hops.isNotEmpty()) {
                item {
                    TracerouteStatus(
                        target = state.target ?: target,
                        address = state.resolvedAddress,
                        running = state.running,
                        phase = state.phase,
                        currentHop = state.currentHop,
                        maximumHops = state.maximumHops,
                        result = state.result,
                    )
                }
                if (state.hops.any { it.location != null }) {
                    item { TracerouteMap(state.hops) }
                }
                item { Text("Route", style = MaterialTheme.typography.titleMedium) }
                items(state.hops, key = TracerouteHop::number) { hop ->
                    TracerouteHopRow(hop)
                }
            }
            item { TracerouteHistoryHeader(state.history.isNotEmpty(), viewModel::clearHistory) }
            if (state.history.isEmpty()) {
                item { TracerouteHistoryEmpty() }
            } else {
                items(state.history, key = TracerouteHistoryEntry::id) { entry ->
                    TracerouteHistoryRow(entry, onDelete = { viewModel.removeHistory(entry.id) })
                }
            }
        }
    }
}

@Composable
private fun TracerouteForm(
    target: String,
    onTargetChange: (String) -> Unit,
    maximumHops: String,
    onMaximumHopsChange: (String) -> Unit,
    attempts: String,
    onAttemptsChange: (String) -> Unit,
    timeout: String,
    onTimeoutChange: (String) -> Unit,
    errors: TracerouteFormErrors,
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
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !running,
                label = { Text("Hostname or IP address") },
                placeholder = { Text("example.com or 8.8.8.8") },
                supportingText = errors.target?.let { message -> { Text(message) } },
                isError = errors.target != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (!running) onStart() }),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TraceNumberField(
                    maximumHops, onMaximumHopsChange, "Maximum hops", "1-30", errors.maximumHops,
                    !running, Modifier.weight(1f),
                )
                TraceNumberField(
                    attempts, onAttemptsChange, "Attempts / hop", "1-3", errors.attempts,
                    !running, Modifier.weight(1f),
                )
            }
            TraceNumberField(
                timeout, onTimeoutChange, "Timeout per attempt (seconds)", "1-3", errors.timeout,
                !running, Modifier.fillMaxWidth(),
            )
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
                        "Trace only systems and networks you own or are authorized to test.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (running) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Stop, contentDescription = null)
                    Text("Stop Traceroute", modifier = Modifier.padding(start = 8.dp))
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Text("Start Traceroute", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun TraceNumberField(
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
private fun TracerouteStatus(
    target: String,
    address: String?,
    running: Boolean,
    phase: TraceroutePhase,
    currentHop: Int,
    maximumHops: Int,
    result: TracerouteResult?,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(target, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
            address?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (running) phase.label else when {
                    result?.stopped == true -> "Stopped"
                    result?.reachedDestination == true -> "Destination reached"
                    else -> "Partial route"
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (running) {
                val progress = if (maximumHops == 0) 0f else (currentHop.toFloat() / maximumHops).coerceIn(0f, 1f)
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (phase == TraceroutePhase.TRACING) "Hop ${maxOf(currentHop, 1)} of $maximumHops" else phase.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TracerouteMap(hops: List<TracerouteHop>) {
    val located = hops.mapNotNull { hop -> hop.location?.let { hop.number to it } }
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surface
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Map, contentDescription = null, modifier = Modifier.size(21.dp))
            Text("Approximate route map", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
        }
        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = surface) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f)
                    .semantics {
                        contentDescription = "Visual route map showing ${located.size} geolocated public hops"
                    },
            ) {
                val inset = 18.dp.toPx()
                val usableWidth = size.width - inset * 2
                val usableHeight = size.height - inset * 2
                listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
                    drawLine(grid, Offset(inset + usableWidth * fraction, inset),
                        Offset(inset + usableWidth * fraction, inset + usableHeight), 1.dp.toPx())
                }
                listOf(1f / 3f, 2f / 3f).forEach { fraction ->
                    drawLine(grid, Offset(inset, inset + usableHeight * fraction),
                        Offset(inset + usableWidth, inset + usableHeight * fraction), 1.dp.toPx())
                }
                val points = located.map { (_, location) ->
                    Offset(
                        x = inset + ((location.longitude + 180.0) / 360.0).toFloat() * usableWidth,
                        y = inset + ((90.0 - location.latitude) / 180.0).toFloat() * usableHeight,
                    )
                }
                points.zipWithNext().forEach { (start, end) ->
                    drawLine(primary, start, end, 3.dp.toPx(), cap = StrokeCap.Round)
                }
                points.forEachIndexed { index, point ->
                    drawCircle(primary.copy(alpha = 0.18f), 10.dp.toPx(), point)
                    drawCircle(primary, if (index == points.lastIndex) 5.dp.toPx() else 4.dp.toPx(), point)
                }
            }
        }
        Text(
            "Public hops only. Positions are approximate registry-derived locations.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TracerouteHopRow(hop: TracerouteHop) {
    val statusColor = when {
        hop.reachedDestination -> MaterialTheme.colorScheme.primaryContainer
        hop.unreachable -> MaterialTheme.colorScheme.errorContainer
        hop.timedOut -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = statusColor) {
        Row(
            Modifier.fillMaxWidth().padding(13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(hop.number.toString(), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                when {
                    hop.timedOut -> {
                        Text("No response", fontWeight = FontWeight.Medium)
                        Text(
                            hop.attempts.joinToString("  ") { "*" },
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> {
                        Text(
                            hop.hostname ?: hop.primaryAddress.orEmpty(),
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                        )
                        if (hop.hostname != null) {
                            Text(
                                hop.addresses.joinToString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            hop.attempts.joinToString("  ") { attempt ->
                                attempt.responseTimeMillis?.let { "${formatTraceTime(it)} ms" } ?: "*"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                        )
                        hop.location?.let { location ->
                            Text(
                                listOfNotNull(
                                    location.label,
                                    String.format(Locale.US, "%.4f, %.4f", location.latitude, location.longitude),
                                ).joinToString("  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (hop.reachedDestination) {
                            Text("Destination", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        } else if (hop.unreachable || hop.attempts.any { it.status == TraceAttemptStatus.UNREACHABLE }) {
                            Text("Destination unreachable", style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TracerouteHistoryHeader(hasEntries: Boolean, onClear: () -> Unit) {
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
private fun TracerouteHistoryEmpty() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Text(
            "Completed and stopped Traceroute runs will appear here.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TracerouteHistoryRow(entry: TracerouteHistoryEntry, onDelete: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(entry.target, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, maxLines = 1)
                Text(
                    "${entry.hopCount} hops  " + when {
                        entry.stopped -> "Stopped"
                        entry.reachedDestination -> "Destination reached"
                        else -> "Partial route"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(entry.completedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Traceroute history entry")
            }
        }
    }
}

@Composable
private fun TracerouteErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

private data class TracerouteFormErrors(
    val target: String? = null,
    val maximumHops: String? = null,
    val attempts: String? = null,
    val timeout: String? = null,
) : java.io.Serializable {
    val hasAny: Boolean get() = listOf(target, maximumHops, attempts, timeout).any { it != null }
}

private fun validateTracerouteForm(
    target: String,
    maximumHops: String,
    attempts: String,
    timeout: String,
): TracerouteFormErrors = TracerouteFormErrors(
    target = runCatching { PingRepository.normalizeTarget(target) }.exceptionOrNull()?.message,
    maximumHops = traceNumberError(maximumHops, TracerouteConfig.MaxHopsRange, "Maximum hops"),
    attempts = traceNumberError(attempts, TracerouteConfig.AttemptsRange, "Attempts per hop"),
    timeout = traceNumberError(timeout, TracerouteConfig.TimeoutRange, "Timeout"),
)

private fun traceNumberError(value: String, range: IntRange, label: String): String? {
    val number = value.toIntOrNull() ?: return "$label is required"
    return if (number in range) null else "$label must be ${range.first}-${range.last}"
}

private fun String.numeric(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private fun shareTracerouteResult(context: Context, result: TracerouteResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Traceroute result for ${result.target}")
        putExtra(Intent.EXTRA_TEXT, result.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share Traceroute result"))
}
