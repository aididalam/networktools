package com.nexdevlab.networktools.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.IpInformationViewModel
import com.nexdevlab.networktools.data.NetworkSnapshot
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpInformationScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: IpInformationViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var permissionVersion by remember { mutableIntStateOf(0) }
    val hasWifiPermission = remember(permissionVersion) { hasWifiDetailsPermission(context) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        permissionVersion++
        val granted = grants.values.all { it } && hasWifiDetailsPermission(context)
        viewModel.refresh(granted)
        if (!granted) scope.launch { snackbar.showSnackbar("Wi-Fi identifiers remain hidden") }
    }

    LaunchedEffect(Unit) {
        if (state.snapshot == null && !state.loading) viewModel.refresh(hasWifiPermission)
    }
    DisposableEffect(viewModel) {
        onDispose(viewModel::cancelRefresh)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("IP Information") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.refresh(hasWifiPermission) },
                        enabled = !state.loading,
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh network information")
                    }
                    IconButton(
                        onClick = {
                            state.snapshot?.let {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Network information", it.asPlainText()))
                                scope.launch { snackbar.showSnackbar("Network information copied") }
                            }
                        },
                        enabled = state.snapshot != null,
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy network information")
                    }
                    IconButton(
                        onClick = { state.snapshot?.let { shareSnapshot(context, it) } },
                        enabled = state.snapshot != null,
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share network information")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "This screen reads the active connection while it is visible. " +
                            "Wi-Fi network names and BSSIDs remain hidden until you grant access.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.snapshot == null && state.loading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
                state.snapshot == null -> EmptyNetworkState(
                    message = state.error ?: "No active network information",
                    onRetry = { viewModel.refresh(hasWifiPermission) },
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> NetworkDetails(
                    snapshot = requireNotNull(state.snapshot),
                    loading = state.loading,
                    hasWifiPermission = hasWifiPermission,
                    onRequestWifiPermission = {
                        permissionLauncher.launch(wifiPermissions())
                    },
                )
            }
        }
    }
}

@Composable
private fun NetworkDetails(
    snapshot: NetworkSnapshot,
    loading: Boolean,
    hasWifiPermission: Boolean,
    onRequestWifiPermission: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        item { ConnectionHeader(snapshot) }
        snapshot.remoteError?.let { error ->
            item {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        item {
            InformationSection("Internet") {
                InformationRow("Public IP", snapshot.publicIp)
                InformationRow("Organization", snapshot.organization)
                InformationRow("ASN", snapshot.asn, last = true)
            }
        }
        item {
            InformationSection("Local network") {
                InformationRow("Local IP", snapshot.localAddresses.joinToString().ifBlank { null })
                InformationRow("Gateway", snapshot.gateway)
                InformationRow("Subnet mask", snapshot.subnetMask)
                InformationRow("Broadcast", snapshot.broadcastAddress)
                InformationRow("DNS servers", snapshot.dnsServers.joinToString().ifBlank { null }, last = true)
            }
        }
        if (!hasWifiPermission && snapshot.connectionType == "Wi-Fi") {
            item { WifiPermissionPanel(onRequestWifiPermission) }
        }
        item {
            InformationSection("Wi-Fi") {
                InformationRow("SSID", snapshot.wifiSsid ?: protectedValue(hasWifiPermission))
                InformationRow("BSSID", snapshot.wifiBssid ?: protectedValue(hasWifiPermission))
                InformationRow("Signal strength", snapshot.wifiSignal)
                InformationRow("Link speed", snapshot.wifiLinkSpeed, last = true)
            }
        }
        item {
            Text(
                text = "Updated ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(snapshot.refreshedAtMillis))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun ConnectionHeader(snapshot: NetworkSnapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Wifi, contentDescription = null)
                }
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(snapshot.connectionType, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = snapshot.publicIp ?: "Public address unavailable",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun InformationSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 2.dp, bottom = 7.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun InformationRow(label: String, value: String?, last: Boolean = false) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.42f),
            )
            Text(
                text = value ?: "Unavailable",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                fontFamily = if (label in setOf("Public IP", "Local IP", "Gateway", "Subnet mask", "Broadcast", "DNS servers", "BSSID", "ASN")) {
                    FontFamily.Monospace
                } else {
                    FontFamily.Default
                },
                modifier = Modifier.weight(0.58f),
            )
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(start = 14.dp))
    }
}

@Composable
private fun WifiPermissionPanel(onRequest: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null)
                Text("Wi-Fi identifiers are protected", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Location and nearby-device access are used only to read this connection's SSID and BSSID. The values stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = onRequest,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 12.dp),
            ) { Text("Allow") }
        }
    }
}

@Composable
private fun EmptyNetworkState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text("Retry") }
    }
}

private fun protectedValue(permissionGranted: Boolean): String? =
    if (permissionGranted) null else "Permission required"

private fun hasWifiDetailsPermission(context: Context): Boolean {
    val fineLocation = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED
    val nearby = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        ) == PackageManager.PERMISSION_GRANTED
    return fineLocation && nearby
}

private fun wifiPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    }
}.toTypedArray()

private fun shareSnapshot(context: Context, snapshot: NetworkSnapshot) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Network information")
        putExtra(Intent.EXTRA_TEXT, snapshot.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share network information"))
}
