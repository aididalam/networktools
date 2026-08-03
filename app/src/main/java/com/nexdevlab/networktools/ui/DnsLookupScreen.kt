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
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.DnsComparisonResult
import com.nexdevlab.networktools.data.DnsLookupRepository
import com.nexdevlab.networktools.data.DnsLookupResult
import com.nexdevlab.networktools.data.DnsLookupUiState
import com.nexdevlab.networktools.data.DnsLookupView
import com.nexdevlab.networktools.data.DnsQueryConfig
import com.nexdevlab.networktools.data.DnsRecordEntry
import com.nexdevlab.networktools.data.DnsRecordType
import com.nexdevlab.networktools.data.DnsResolverMode
import com.nexdevlab.networktools.data.DnsResponseStatus
import com.nexdevlab.networktools.data.DnsSection
import com.nexdevlab.networktools.data.DnsLookupViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DnsLookupScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: DnsLookupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var selectedView by rememberSaveable { mutableStateOf(DnsLookupView.LOOKUP) }
    var name by rememberSaveable { mutableStateOf("") }
    var recordType by rememberSaveable { mutableStateOf(DnsRecordType.A) }
    var resolverMode by rememberSaveable { mutableStateOf(DnsResolverMode.SYSTEM) }
    var customResolver by rememberSaveable { mutableStateOf(DnsLookupRepository.PublicResolvers.first().address) }
    var timeout by rememberSaveable { mutableStateOf(DnsQueryConfig.DefaultTimeoutMillis) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueAfterPermission(granted)
    }
    if (state.permissionRequired) {
        DnsLocalPermissionDialog(
            onContinue = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else viewModel.continueAfterPermission(true)
            },
            onDismiss = viewModel::dismissPermission,
        )
    }

    val shareText = when (selectedView) {
        DnsLookupView.LOOKUP -> state.result?.asPlainText()
        DnsLookupView.COMPARE -> state.comparison?.asPlainText()
    }
    fun runQuery() {
        viewModel.start(
            DnsQueryConfig(
                name = name,
                recordType = recordType,
                resolverMode = resolverMode,
                customResolver = customResolver,
                timeoutMillis = timeout,
            ),
            compare = selectedView == DnsLookupView.COMPARE,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("DNS Lookup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = shareText != null,
                        onClick = {
                            shareText?.let { text ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("DNS lookup", text))
                                scope.launch { snackbar.showSnackbar("DNS result copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy DNS result") }
                    IconButton(
                        enabled = shareText != null,
                        onClick = { shareText?.let { shareDnsResult(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share DNS result") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Query one DNS name through the active network resolver or a numeric resolver you select. " +
                            "Compare runs the same bounded question against the system resolver and three public resolvers.",
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
            item { DnsViewPicker(selectedView) { if (!state.running) selectedView = it } }
            item {
                DnsQueryPanel(
                    selectedView = selectedView,
                    name = name,
                    onNameChanged = { name = it.take(253) },
                    recordType = recordType,
                    onRecordTypeChanged = { recordType = it },
                    resolverMode = resolverMode,
                    onResolverModeChanged = { resolverMode = it },
                    customResolver = customResolver,
                    onCustomResolverChanged = { customResolver = it.take(45) },
                    timeout = timeout,
                    onTimeoutChanged = { timeout = it },
                    busy = state.running,
                    onRun = ::runQuery,
                    onStop = viewModel::stop,
                )
            }
            item { DnsPrivacyNotice(state.activeQuery?.privateDnsActive == true) }
            if (state.running) item { DnsProgressPanel(selectedView, state) }
            state.error?.let { item { DnsErrorPanel(it) } }
            when (selectedView) {
                DnsLookupView.LOOKUP -> {
                    val result = state.result
                    if (result != null) {
                        item { DnsResultSummary(result) }
                        if (result.records.isEmpty()) {
                            item { DnsEmptyResult(result.detail ?: "No DNS records were returned.") }
                        } else {
                            DnsSection.entries.forEach { section ->
                                val records = result.records.filter { it.section == section }
                                if (records.isNotEmpty()) {
                                    item { Text(section.label, style = MaterialTheme.typography.titleSmall) }
                                    items(records, key = { "${it.section}-${it.name}-${it.type}-${it.value}-${it.ttlSeconds}" }) {
                                        DnsRecordRow(it)
                                    }
                                }
                            }
                        }
                    } else if (!state.running && state.error == null) {
                        item { DnsInitialState(selectedView) }
                    }
                }
                DnsLookupView.COMPARE -> {
                    val comparison = state.comparison
                    if (comparison != null) {
                        item { DnsComparisonSummary(comparison) }
                        items(comparison.results, key = { it.resolver.address }) { DnsResolverResult(it) }
                    } else if (state.comparisonProgress.isNotEmpty()) {
                        items(state.comparisonProgress, key = { it.resolver.address }) { DnsResolverResult(it) }
                    } else if (!state.running && state.error == null) item { DnsInitialState(selectedView) }
                }
            }
        }
    }
}

@Composable
private fun DnsViewPicker(selected: DnsLookupView, onSelected: (DnsLookupView) -> Unit) {
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DnsLookupView.entries.forEach { view ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(view) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == view) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (selected == view) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) { Box(contentAlignment = Alignment.Center) { Text(view.label, fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
private fun DnsQueryPanel(
    selectedView: DnsLookupView,
    name: String,
    onNameChanged: (String) -> Unit,
    recordType: DnsRecordType,
    onRecordTypeChanged: (DnsRecordType) -> Unit,
    resolverMode: DnsResolverMode,
    onResolverModeChanged: (DnsResolverMode) -> Unit,
    customResolver: String,
    onCustomResolverChanged: (String) -> Unit,
    timeout: Int,
    onTimeoutChanged: (Int) -> Unit,
    busy: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                label = { Text(if (recordType == DnsRecordType.PTR) "Domain name or IP address" else "Domain or DNS name") },
                placeholder = { Text(if (recordType == DnsRecordType.SRV) "_service._tcp.example.com" else "example.com") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            DnsRecordTypeMenu(recordType, onRecordTypeChanged, busy)
            if (selectedView == DnsLookupView.LOOKUP) {
                Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DnsResolverMode.entries.forEach { mode ->
                        Surface(
                            modifier = Modifier.weight(1f).fillMaxSize()
                                .clickable(enabled = !busy) { onResolverModeChanged(mode) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (resolverMode == mode) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) { Box(contentAlignment = Alignment.Center) { Text("${mode.label} DNS") } }
                    }
                }
                if (resolverMode == DnsResolverMode.CUSTOM) {
                    OutlinedTextField(
                        value = customResolver,
                        onValueChange = onCustomResolverChanged,
                        label = { Text("DNS-server IP address") },
                        singleLine = true,
                        enabled = !busy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DnsLookupRepository.PublicResolvers.forEach { resolver ->
                            TextButton(
                                onClick = { onCustomResolverChanged(resolver.address) },
                                enabled = !busy,
                                modifier = Modifier.weight(1f),
                            ) { Text(resolver.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            } else {
                Text(
                    "System DNS + Cloudflare + Google + Quad9",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Query timeout", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().height(42.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                DnsQueryConfig.AllowedTimeouts.sorted().forEach { value ->
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !busy) { onTimeoutChanged(value) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (timeout == value) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) { Box(contentAlignment = Alignment.Center) { Text("${value / 1_000} s") } }
                }
            }
            Button(onClick = if (busy) onStop else onRun, modifier = Modifier.fillMaxWidth()) {
                Icon(if (busy) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow, contentDescription = null)
                Text(if (busy) "Stop query" else if (selectedView == DnsLookupView.COMPARE) "Compare resolvers" else "Run DNS lookup",
                    Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun DnsRecordTypeMenu(
    selected: DnsRecordType,
    onSelected: (DnsRecordType) -> Unit,
    disabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text("${selected.label} record", fontWeight = FontWeight.Medium)
                Text(selected.description, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Choose record type")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DnsRecordType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text("${type.label}  ${type.description}") },
                    onClick = { onSelected(type); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun DnsPrivacyNotice(privateDnsActive: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                if (privateDnsActive) "Android Private DNS is active. This direct record query uses the displayed DNS resolver and may not use the protected Private DNS path."
                else "Direct DNS query to the selected resolver. No API service or account is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DnsProgressPanel(view: DnsLookupView, state: DnsLookupUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Column {
                Text(if (view == DnsLookupView.COMPARE) "Comparing resolvers" else "Querying DNS", fontWeight = FontWeight.Medium)
                val active = state.activeQuery
                Text(
                    if (view == DnsLookupView.COMPARE) "${state.comparisonProgress.size} of ${active?.resolvers?.size ?: 0} complete"
                    else "${active?.normalizedName.orEmpty()} ${active?.config?.recordType?.label.orEmpty()}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DnsResultSummary(result: DnsLookupResult) {
    val success = result.status == DnsResponseStatus.SUCCESS
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (success) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text("${result.queryName} ${result.recordType.label}", Modifier.padding(start = 8.dp).weight(1f),
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.status.label, color = if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            }
            Text("${result.resolver.label} • ${result.resolver.address}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${result.responseCode} • ${if (result.usedTcp) "TCP" else "UDP"} • ${formatDnsLatency(result.latencyMillis)} ms" +
                (result.minimumTtl?.let { " • minimum TTL ${it}s" }.orEmpty()),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DnsRecordRow(record: DnsRecordEntry) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(record.type, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
                }
                Text("TTL ${record.ttlSeconds}s", Modifier.weight(1f).padding(start = 10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(record.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(record.value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun DnsComparisonSummary(comparison: DnsComparisonResult) {
    val complete = comparison.results.size
    val success = comparison.successfulResults.size
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (comparison.consistent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (comparison.consistent) "Answers match" else "Answers differ or are incomplete",
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text("${comparison.queryName} ${comparison.recordType.label}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$success successful • $complete resolvers checked", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DnsResolverResult(result: DnsLookupResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(result.resolver.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(result.status.label, color = if (result.status == DnsResponseStatus.SUCCESS) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error)
            }
            Text("${result.resolver.address} • ${formatDnsLatency(result.latencyMillis)} ms • " +
                "${result.answerRecords.size} answers", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            result.answerRecords.take(3).forEach { record ->
                Text("${record.type}  ${record.value}  TTL ${record.ttlSeconds}s", style = MaterialTheme.typography.bodySmall)
            }
            if (result.answerRecords.size > 3) {
                Text("+${result.answerRecords.size - 3} more", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            result.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun DnsErrorPanel(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun DnsEmptyResult(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DnsInitialState(view: DnsLookupView) {
    Surface(Modifier.fillMaxWidth().height(118.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Dns, contentDescription = null, modifier = Modifier.size(34.dp), tint = MaterialTheme.colorScheme.primary)
            Text(if (view == DnsLookupView.COMPARE) "No resolver comparison" else "No DNS lookup result",
                Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun DnsLocalPermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
        title = { Text("Allow local DNS access") },
        text = { Text("Android requires Nearby devices permission to contact this local-network DNS resolver. " +
            "NetworkTools sends only the displayed, user-initiated DNS question while this screen is active.") },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private fun formatDnsLatency(value: Double): String = "%.1f".format(Locale.US, value)

private fun shareDnsResult(context: Context, text: String) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "DNS lookup")
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Share DNS result",
        ),
    )
}
