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
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.SwapHoriz
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
import com.nexdevlab.networktools.data.IpHostAddressMode
import com.nexdevlab.networktools.data.IpHostConversionAnswer
import com.nexdevlab.networktools.data.IpHostConversionConfig
import com.nexdevlab.networktools.data.IpHostConversionMode
import com.nexdevlab.networktools.data.IpHostConversionResult
import com.nexdevlab.networktools.data.IpHostConverterUiState
import com.nexdevlab.networktools.data.IpHostConverterViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpHostConverterScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: IpHostConverterViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(IpHostConversionMode.HOST_TO_IP) }
    var addressMode by rememberSaveable { mutableStateOf(IpHostAddressMode.BOTH) }
    var target by rememberSaveable { mutableStateOf("") }
    var resolverMode by rememberSaveable { mutableStateOf(DnsResolverMode.SYSTEM) }
    var customResolver by rememberSaveable { mutableStateOf(DnsLookupRepository.PublicResolvers.first().address) }
    var timeout by rememberSaveable { mutableIntStateOf(DnsQueryConfig.DefaultTimeoutMillis) }

    DisposableEffect(viewModel) { onDispose(viewModel::stop) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueAfterPermission(granted)
    }
    if (state.permissionRequired) {
        IpHostLocalPermissionDialog(
            onContinue = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else viewModel.continueAfterPermission(true)
            },
            onDismiss = viewModel::dismissPermission,
        )
    }

    fun invalidate() = viewModel.clearResult()
    fun runConversion() {
        viewModel.start(
            IpHostConversionConfig(
                mode = mode,
                target = target,
                addressMode = addressMode,
                resolverMode = resolverMode,
                customResolver = customResolver,
                timeoutMillis = timeout,
            ),
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "IP & Host Converter",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
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
                                clipboard.setPrimaryClip(ClipData.newPlainText("IP and host conversion", result.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Conversion result copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy conversion result") }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { shareIpHostResult(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share conversion result") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Resolve one hostname to IPv4 and IPv6 addresses, or perform reverse DNS for one numeric IP address. " +
                            "Queries go directly to the displayed system or custom DNS resolver.",
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
                IpHostInputPanel(
                    mode = mode,
                    onModeChanged = { mode = it; target = ""; invalidate() },
                    addressMode = addressMode,
                    onAddressModeChanged = { addressMode = it; invalidate() },
                    target = target,
                    onTargetChanged = { target = it.take(IpHostConversionConfig.MaximumTargetLength); invalidate() },
                    resolverMode = resolverMode,
                    onResolverModeChanged = { resolverMode = it; invalidate() },
                    customResolver = customResolver,
                    onCustomResolverChanged = { customResolver = it.take(45); invalidate() },
                    timeout = timeout,
                    onTimeoutChanged = { timeout = it; invalidate() },
                    busy = state.running,
                    onRun = ::runConversion,
                    onStop = viewModel::stop,
                )
            }
            item { IpHostPrivacyNotice(state.result?.privateDnsActive == true) }
            if (state.running) item { IpHostProgress(state) }
            state.error?.let { item { IpHostError(it) } }
            val result = state.result
            if (result != null) {
                item { IpHostResultSummary(result) }
                if (result.answers.isEmpty()) {
                    item { IpHostNoAnswers(result) }
                } else {
                    items(result.answers, key = { "${it.type}:${it.value}" }) { answer ->
                        IpHostAnswerRow(answer)
                    }
                }
            } else if (!state.running && state.error == null) {
                item { IpHostInitialState() }
            }
        }
    }
}

@Composable
private fun IpHostInputPanel(
    mode: IpHostConversionMode,
    onModeChanged: (IpHostConversionMode) -> Unit,
    addressMode: IpHostAddressMode,
    onAddressModeChanged: (IpHostAddressMode) -> Unit,
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
            IpHostSegmentedRow(IpHostConversionMode.entries, mode, { it.label }, busy, onModeChanged)
            OutlinedTextField(
                value = target,
                onValueChange = onTargetChanged,
                label = { Text(if (mode == IpHostConversionMode.HOST_TO_IP) "Hostname" else "IPv4 or IPv6 address") },
                placeholder = { Text(if (mode == IpHostConversionMode.HOST_TO_IP) "example.com" else "192.0.2.1") },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (mode == IpHostConversionMode.HOST_TO_IP) KeyboardType.Uri else KeyboardType.Ascii,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode == IpHostConversionMode.HOST_TO_IP) {
                Text("Address results", style = MaterialTheme.typography.labelLarge)
                IpHostSegmentedRow(IpHostAddressMode.entries, addressMode, { it.label }, busy, onAddressModeChanged)
            }
            Text("DNS resolver", style = MaterialTheme.typography.labelLarge)
            IpHostSegmentedRow(DnsResolverMode.entries, resolverMode, { "${it.label} DNS" }, busy, onResolverModeChanged)
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
            IpHostSegmentedRow(DnsQueryConfig.AllowedTimeouts.sorted(), timeout, { "${it / 1_000} s" }, busy, onTimeoutChanged)
            Button(onClick = if (busy) onStop else onRun, modifier = Modifier.fillMaxWidth()) {
                Icon(if (busy) Icons.Outlined.StopCircle else Icons.Outlined.PlayArrow, contentDescription = null)
                Text(if (busy) "Stop conversion" else "Convert", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun <T> IpHostSegmentedRow(
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
                color = if (selected == value) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun IpHostPrivacyNotice(privateDnsActive: Boolean) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Text(
                if (privateDnsActive) "Android Private DNS is active. This direct query uses the displayed resolver and may not use the protected Private DNS path."
                else "One bounded DNS conversion is sent to the displayed resolver. No API service, account, or history is used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IpHostProgress(state: IpHostConverterUiState) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Column {
                Text("Converting", fontWeight = FontWeight.Medium)
                Text(state.activeConversion?.normalizedTarget.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun IpHostResultSummary(result: IpHostConversionResult) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (result.successful) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (result.successful) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null)
                Text(result.normalizedTarget, Modifier.padding(start = 8.dp).weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(result.statusText)
            }
            Text("${result.mode.label} • ${result.resolver.label} (${result.resolver.address})",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IpHostAnswerRow(answer: IpHostConversionAnswer) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(6.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(answer.type, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(answer.value, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                Text("TTL ${answer.ttlSeconds}s", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun IpHostNoAnswers(result: IpHostConversionResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("No conversion result", fontWeight = FontWeight.Medium)
            Text(result.queryResults.joinToString(" • ") { "${it.recordType.label}: ${it.status.label}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IpHostError(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun IpHostInitialState() {
    Surface(Modifier.fillMaxWidth().height(118.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
            Text("No conversion result", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun IpHostLocalPermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
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

private fun shareIpHostResult(context: Context, result: IpHostConversionResult) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "IP and host conversion")
                putExtra(Intent.EXTRA_TEXT, result.asPlainText())
            },
            "Share conversion result",
        ),
    )
}
