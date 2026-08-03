package com.nexdevlab.networktools.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import com.nexdevlab.networktools.data.RouterAddress
import com.nexdevlab.networktools.data.RouterAddressSource
import com.nexdevlab.networktools.data.RouterPageAction
import com.nexdevlab.networktools.data.RouterProtocol
import com.nexdevlab.networktools.data.RouterSetupUiState
import com.nexdevlab.networktools.data.RouterSetupViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

private data class PendingRouterPage(
    val action: RouterPageAction,
    val address: RouterAddress,
    val protocol: RouterProtocol,
) {
    val url: String get() = address.browserUrl(protocol)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouterSetupScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: RouterSetupViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var source by rememberSaveable { mutableStateOf(RouterAddressSource.DETECTED) }
    var manualAddress by rememberSaveable { mutableStateOf("") }
    var protocol by rememberSaveable { mutableStateOf(RouterProtocol.HTTPS) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingPage by remember { mutableStateOf<PendingRouterPage?>(null) }

    fun selectedAddress(): RouterAddress? {
        if (source == RouterAddressSource.DETECTED) {
            return state.detectedRouter?.address.also {
                validationError = if (it == null) state.detectionError ?: "No router gateway is available" else null
            }
        }
        return runCatching { RouterAddress.parse(manualAddress, RouterAddressSource.MANUAL) }
            .onFailure { validationError = it.message ?: "Router address is invalid" }
            .getOrNull()
            .also { if (it != null) validationError = null }
    }

    fun requestPage(action: RouterPageAction) {
        selectedAddress()?.let { pendingPage = PendingRouterPage(action, it, protocol) }
    }

    pendingPage?.let { pending ->
        RouterLaunchConfirmation(
            pending = pending,
            onDismiss = { pendingPage = null },
            onOpen = {
                pendingPage = null
                if (!openRouterPage(context, pending.url)) {
                    scope.launch { snackbar.showSnackbar("No browser could open the router page") }
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Router Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            selectedAddress()?.let { address ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Router address", address.value))
                                scope.launch { snackbar.showSnackbar("Router address copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy router address") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Detect a local router gateway or enter its private IP address. " +
                            "NetworkTools only opens the confirmed page in your browser and never handles router credentials.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("router_setup_list"),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RouterDestinationPanel(
                    state = state,
                    source = source,
                    onSourceChanged = { source = it; validationError = null },
                    manualAddress = manualAddress,
                    onManualAddressChanged = {
                        manualAddress = it.take(RouterAddress.MaximumLength)
                        validationError = null
                    },
                    protocol = protocol,
                    onProtocolChanged = { protocol = it },
                    validationError = validationError,
                    onRefresh = viewModel::refresh,
                    onOpen = { requestPage(RouterPageAction.ADMIN) },
                )
            }
            item { RouterPrivacyNotice() }
            item { SectionLabel("Device settings") }
            item {
                RouterActionRow(
                    left = RouterActionSpec("Wi-Fi settings", "Network connections", Icons.Outlined.Wifi) {
                        launchSettings(context, wifiSettingsIntent())
                    },
                    right = RouterActionSpec("Wi-Fi passwords", "Android protected", Icons.Outlined.Lock) {
                        launchSettings(context, Intent(Settings.ACTION_WIFI_SETTINGS))
                    },
                    onLaunchFailure = { scope.launch { snackbar.showSnackbar("This settings screen is unavailable") } },
                )
            }
            item {
                RouterActionCard(
                    spec = RouterActionSpec("Private DNS", "Android DNS settings", Icons.Outlined.Dns) {
                        launchPrivateDnsSettings(context)
                    },
                    onLaunchFailure = { scope.launch { snackbar.showSnackbar("Private DNS settings are unavailable") } },
                )
            }
            item { SectionLabel("Router pages") }
            item {
                RouterActionRow(
                    left = RouterActionSpec("DHCP", "Address assignment", Icons.Outlined.Lan) {
                        requestPage(RouterPageAction.DHCP); true
                    },
                    right = RouterActionSpec("Port forwarding", "Inbound rules", Icons.Outlined.Security) {
                        requestPage(RouterPageAction.PORT_FORWARDING); true
                    },
                    onLaunchFailure = {},
                )
            }
            item {
                RouterActionCard(
                    spec = RouterActionSpec("Connected devices", "Router device list", Icons.Outlined.Devices) {
                        requestPage(RouterPageAction.CONNECTED_DEVICES); true
                    },
                    onLaunchFailure = {},
                )
            }
        }
    }
}

@Composable
private fun RouterDestinationPanel(
    state: RouterSetupUiState,
    source: RouterAddressSource,
    onSourceChanged: (RouterAddressSource) -> Unit,
    manualAddress: String,
    onManualAddressChanged: (String) -> Unit,
    protocol: RouterProtocol,
    onProtocolChanged: (RouterProtocol) -> Unit,
    validationError: String?,
    onRefresh: () -> Unit,
    onOpen: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RouterSegments(RouterAddressSource.entries, source, { it.label }, onSourceChanged)
            if (source == RouterAddressSource.DETECTED) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Router gateway", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        when {
                            state.refreshing -> Text("Detecting...", fontFamily = FontFamily.Monospace)
                            state.detectedRouter != null -> {
                                Text(state.detectedRouter.address.value, style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace)
                                Text(
                                    listOfNotNull(state.detectedRouter.connectionLabel, state.detectedRouter.interfaceName)
                                        .joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            else -> Text(state.detectionError ?: "Gateway unavailable",
                                color = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !state.refreshing) {
                        if (state.refreshing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, contentDescription = "Refresh router gateway")
                    }
                }
            } else {
                OutlinedTextField(
                    value = manualAddress,
                    onValueChange = onManualAddressChanged,
                    label = { Text("Private router IP address") },
                    placeholder = { Text("192.168.1.1") },
                    supportingText = { Text("Numeric private or link-local address only") },
                    isError = validationError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text("Connection", style = MaterialTheme.typography.labelLarge)
            RouterSegments(RouterProtocol.entries, protocol, { it.label }, onProtocolChanged)
            if (protocol == RouterProtocol.HTTP) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.error)
                    Text("HTTP is unencrypted. Use it only when your router does not support HTTPS.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            validationError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall) }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Router, contentDescription = null)
                Text("Open router administration", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun <T> RouterSegments(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (value == selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label(value), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun RouterPrivacyNotice() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(
                "Pages open only after confirmation in your external browser. NetworkTools does not test passwords, " +
                    "load router pages itself, or collect credentials and configuration data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

private data class RouterActionSpec(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val launch: () -> Boolean,
)

@Composable
private fun RouterActionRow(
    left: RouterActionSpec,
    right: RouterActionSpec,
    onLaunchFailure: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RouterActionCard(left, onLaunchFailure, Modifier.weight(1f))
        RouterActionCard(right, onLaunchFailure, Modifier.weight(1f))
    }
}

@Composable
private fun RouterActionCard(
    spec: RouterActionSpec,
    onLaunchFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(104.dp).clickable { if (!spec.launch()) onLaunchFailure() },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Icon(Icons.Outlined.ChevronRight, contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column {
                Text(spec.title, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                Text(spec.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RouterLaunchConfirmation(
    pending: PendingRouterPage,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (pending.protocol == RouterProtocol.HTTP) Icons.Outlined.WarningAmber else Icons.Outlined.Router,
                contentDescription = null,
            )
        },
        title = { Text("Open ${pending.action.label}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(pending.action.description)
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(pending.url, Modifier.fillMaxWidth().padding(12.dp), fontFamily = FontFamily.Monospace)
                }
                if (pending.protocol == RouterProtocol.HTTP) {
                    Text("Warning: HTTP is unencrypted. Router credentials could be exposed on the network.",
                        color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                }
                Text("The external browser handles sign-in. NetworkTools does not read or store the page or credentials.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onOpen) { Text("Open in browser") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun wifiSettingsIntent(): Intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    Intent(Settings.Panel.ACTION_WIFI)
} else {
    Intent(Settings.ACTION_WIFI_SETTINGS)
}

private fun launchSettings(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

private fun launchPrivateDnsSettings(context: Context): Boolean =
    launchSettings(context, Intent("android.settings.PRIVATE_DNS_SETTINGS")) ||
        launchSettings(context, Intent(Settings.ACTION_WIRELESS_SETTINGS))

private fun openRouterPage(context: Context, url: String): Boolean = try {
    context.startActivity(
        Intent(Intent.ACTION_VIEW, url.toUri()).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
        },
    )
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}
