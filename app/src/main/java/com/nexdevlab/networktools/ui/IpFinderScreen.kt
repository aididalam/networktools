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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.DnsLookupRepository
import com.nexdevlab.networktools.data.DnsQueryConfig
import com.nexdevlab.networktools.data.DnsResolverMode
import com.nexdevlab.networktools.data.IpFinderConfig
import com.nexdevlab.networktools.data.IpFinderMode
import com.nexdevlab.networktools.data.IpFinderResult
import com.nexdevlab.networktools.data.IpFinderUiState
import com.nexdevlab.networktools.data.IpFinderViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpFinderScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: IpFinderViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(IpFinderMode.PUBLIC_IP) }
    var target by rememberSaveable { mutableStateOf("") }
    var resolverMode by rememberSaveable { mutableStateOf(DnsResolverMode.SYSTEM) }
    var customResolver by rememberSaveable { mutableStateOf(DnsLookupRepository.PublicResolvers.first().address) }
    var timeout by rememberSaveable { mutableIntStateOf(DnsQueryConfig.DefaultTimeoutMillis) }

    DisposableEffect(viewModel) { onDispose(viewModel::stop) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueAfterPermission(granted)
    }
    if (state.permissionRequired) {
        IpFinderPermissionDialog(
            onContinue = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else viewModel.continueAfterPermission(true)
            },
            onDismiss = viewModel::dismissPermission,
        )
    }

    fun invalidate() = viewModel.clearResult()
    fun start() = viewModel.start(
        IpFinderConfig(
            mode = mode,
            target = target,
            resolverMode = resolverMode,
            customResolver = customResolver,
            timeoutMillis = timeout,
        ),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("IP Finder") },
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("IP Finder", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("IP Finder result copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy IP Finder result") }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { shareIpFinderResult(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share IP Finder result") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Find the current public IP or resolve one domain or public IP. " +
                            "Organization and origin ASN come from keyless authoritative registration and DNS sources.",
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
                IpFinderInputPanel(
                    mode = mode,
                    onModeChanged = { mode = it; target = ""; invalidate() },
                    target = target,
                    onTargetChanged = { target = it.take(IpFinderConfig.MaximumTargetLength); invalidate() },
                    resolverMode = resolverMode,
                    onResolverModeChanged = { resolverMode = it; invalidate() },
                    customResolver = customResolver,
                    onCustomResolverChanged = { customResolver = it.take(45); invalidate() },
                    timeout = timeout,
                    onTimeoutChanged = { timeout = it; invalidate() },
                    busy = state.running,
                    onRun = ::start,
                    onStop = viewModel::stop,
                )
            }
            item { IpFinderPrivacyNotice(mode) }
            if (state.running) item { IpFinderProgress(state) }
            state.error?.let { item { IpFinderError(it) } }
            val result = state.result
            if (result != null) {
                item { IpFinderSummary(result) }
                item { IpFinderSectionTitle("Resolved addresses") }
                items(result.resolvedAddresses, key = { it }) { address ->
                    IpFinderValueRow(
                        label = if (address == result.selectedAddress) "Selected public IP" else "Resolved public IP",
                        value = address,
                        highlighted = address == result.selectedAddress,
                    )
                }
                item { IpFinderSectionTitle("Registration") }
                item { IpFinderValueRow("Organization", result.organization ?: "Not published") }
                result.registration?.networkName?.takeIf { it != result.organization }?.let { value ->
                    item { IpFinderValueRow("Network name", value) }
                }
                item { IpFinderValueRow("Origin ASN", result.asn ?: "Not published") }
                result.origin?.routedPrefix?.let { item { IpFinderValueRow("Routed prefix", it) } }
                result.origin?.registry?.let { item { IpFinderValueRow("Origin registry", it) } }
                if (result.notices.isNotEmpty()) item { IpFinderNotices(result.notices) }
            } else if (!state.running && state.error == null) {
                item { IpFinderInitialState() }
            }
        }
    }
}

@Composable
private fun IpFinderInputPanel(
    mode: IpFinderMode,
    onModeChanged: (IpFinderMode) -> Unit,
    target: String,
    onTargetChanged: (String) -> Unit,
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
            IpFinderSegments(IpFinderMode.entries, mode, { it.label }, busy, onModeChanged)
            if (mode == IpFinderMode.TARGET) {
                OutlinedTextField(
                    value = target,
                    onValueChange = onTargetChanged,
                    label = { Text("Domain or public IP address") },
                    placeholder = { Text("example.com or 1.1.1.1") },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("DNS for domain and ASN", style = MaterialTheme.typography.labelLarge)
            IpFinderSegments(DnsResolverMode.entries, resolverMode, { "${it.label} DNS" }, busy, onResolverModeChanged)
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
            Text("Query timeout", style = MaterialTheme.typography.labelLarge)
            IpFinderSegments(DnsQueryConfig.AllowedTimeouts.sorted(), timeout, { "${it / 1_000} s" }, busy, onTimeoutChanged)
            Button(onClick = if (busy) onStop else onRun, modifier = Modifier.fillMaxWidth()) {
                Icon(if (busy) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow, contentDescription = null)
                Text(if (busy) "Stop lookup" else "Find IP", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun <T> IpFinderSegments(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    disabled: Boolean,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable(enabled = !disabled) { onSelected(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (value == selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun IpFinderPrivacyNotice(mode: IpFinderMode) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                if (mode == IpFinderMode.PUBLIC_IP) {
                    "Public IP uses keyless HTTPS IP echo, then IANA-selected RDAP and DNS-to-ASN. No history, location profile, account, or API key."
                } else {
                    "The displayed target uses direct DNS, IANA-selected RDAP, and DNS-to-ASN. No history, location profile, account, or API key."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IpFinderProgress(state: IpFinderUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Column {
                Text("Finding IP information", fontWeight = FontWeight.Medium)
                Text(state.activeLookup?.normalizedTarget ?: "Current connection", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IpFinderSummary(result: IpFinderResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null)
                Text(result.query, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${result.resolvedAddresses.size} ${if (result.resolvedAddresses.size == 1) "address" else "addresses"}")
            }
            Text("${result.resolver.label} (${result.resolver.address})", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IpFinderSectionTitle(value: String) {
    Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun IpFinderValueRow(label: String, value: String, highlighted: Boolean = false) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge,
                fontFamily = if (label.contains("IP") || label in setOf("Origin ASN", "Routed prefix")) FontFamily.Monospace else null)
        }
    }
}

@Composable
private fun IpFinderNotices(notices: List<String>) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            notices.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun IpFinderError(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun IpFinderInitialState() {
    Surface(Modifier.fillMaxWidth().height(118.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No IP Finder result", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IpFinderPermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Dns, contentDescription = null) },
        title = { Text("Allow local DNS access") },
        text = { Text("Android requires Nearby devices permission to contact this local-network DNS resolver. " +
            "NetworkTools sends only the displayed, user-initiated DNS questions while this screen is active.") },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

private fun shareIpFinderResult(context: Context, result: IpFinderResult) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "IP Finder")
                putExtra(Intent.EXTRA_TEXT, result.asPlainText())
            },
            "Share IP Finder result",
        ),
    )
}
