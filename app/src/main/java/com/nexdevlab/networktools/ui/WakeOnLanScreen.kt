package com.nexdevlab.networktools.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexdevlab.networktools.data.WakeLocalNetwork
import com.nexdevlab.networktools.data.WakeOnLanDevice
import com.nexdevlab.networktools.data.WakeOnLanDeviceDraft
import com.nexdevlab.networktools.data.WakeOnLanMode
import com.nexdevlab.networktools.data.WakeOnLanResult
import com.nexdevlab.networktools.data.WakeOnLanUiState
import com.nexdevlab.networktools.data.WakeOnLanViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WakeOnLanScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: WakeOnLanViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var macAddress by rememberSaveable { mutableStateOf("") }
    var targetAddress by rememberSaveable { mutableStateOf("") }
    var broadcastAddress by rememberSaveable { mutableStateOf("") }
    var udpPort by rememberSaveable { mutableStateOf("9") }
    var mode by rememberSaveable { mutableStateOf(WakeOnLanMode.LOCAL) }
    var formError by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingSend by remember { mutableStateOf<WakeOnLanDevice?>(null) }
    var pendingDelete by remember { mutableStateOf<WakeOnLanDevice?>(null) }

    val existing = editingId?.let { id -> state.devices.firstOrNull { it.id == id } }

    fun openEditor(device: WakeOnLanDevice? = null) {
        editingId = device?.id
        name = device?.name.orEmpty()
        macAddress = device?.macAddress.orEmpty()
        targetAddress = device?.targetAddress.orEmpty()
        broadcastAddress = device?.broadcastAddress ?: state.localNetwork?.broadcastAddress.orEmpty()
        udpPort = device?.udpPort?.toString() ?: "9"
        mode = device?.mode ?: WakeOnLanMode.LOCAL
        formError = null
        viewModel.clearMessage()
        editing = true
    }

    fun draft() = WakeOnLanDeviceDraft(name, macAddress, targetAddress, broadcastAddress, udpPort, mode)

    fun validateDraft(): WakeOnLanDevice? = try {
        draft().validate(existing?.id, existing?.createdAtMillis ?: System.currentTimeMillis())
            .also { formError = null }
    } catch (failure: Exception) {
        formError = failure.message ?: "Device details are invalid"
        null
    }

    BackHandler(enabled = editing) { editing = false }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.continueAfterPermission(granted)
    }
    if (state.permissionRequired) {
        WakePermissionDialog(
            onContinue = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
                    permissionLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                } else viewModel.continueAfterPermission(true)
            },
            onDismiss = viewModel::dismissPermission,
        )
    }
    pendingSend?.let { device ->
        WakeSendConfirmation(
            device = device,
            onDismiss = { pendingSend = null },
            onSend = {
                pendingSend = null
                editing = false
                viewModel.send(device, authorized = true)
            },
        )
    }
    pendingDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${device.name}?") },
            text = { Text("The saved Wake-on-LAN profile will be removed from this device.") },
            confirmButton = {
                Button(onClick = { viewModel.delete(device); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (editing) if (existing == null) "Add device" else "Edit device" else "Wake-on-LAN") },
                navigationIcon = {
                    IconButton(onClick = { if (editing) editing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!editing) {
                        IconButton(onClick = { openEditor() }) {
                            Icon(Icons.Outlined.Add, contentDescription = "Add Wake-on-LAN device")
                        }
                        IconButton(
                            enabled = state.result != null,
                            onClick = {
                                state.result?.let { result ->
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Wake-on-LAN", result.asPlainText()))
                                    scope.launch { snackbar.showSnackbar("Wake-on-LAN result copied") }
                                }
                            },
                        ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy Wake-on-LAN result") }
                        IconButton(
                            enabled = state.result != null,
                            onClick = { state.result?.let { shareWakeResult(context, it) } },
                        ) { Icon(Icons.Outlined.Share, contentDescription = "Share Wake-on-LAN result") }
                        AppOverflowMenu(
                            themeChoice = themeChoice,
                            onThemeChoice = onThemeChoice,
                            helpText = "Save devices you are authorized to manage. Each confirmed action sends one " +
                                "standard magic packet; packet transmission does not confirm that a device woke.",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (editing) {
            WakeDeviceEditor(
                modifier = Modifier.fillMaxSize().padding(padding),
                mode = mode,
                onModeChanged = {
                    mode = it
                    if (it == WakeOnLanMode.LOCAL && broadcastAddress.isBlank()) {
                        broadcastAddress = state.localNetwork?.broadcastAddress.orEmpty()
                    }
                    formError = null
                },
                name = name,
                onNameChanged = { name = it.take(WakeOnLanDeviceDraft.MaximumNameLength); formError = null },
                macAddress = macAddress,
                onMacChanged = { macAddress = it.take(17); formError = null },
                targetAddress = targetAddress,
                onTargetChanged = { targetAddress = it.take(WakeOnLanDeviceDraft.MaximumTargetLength); formError = null },
                broadcastAddress = broadcastAddress,
                onBroadcastChanged = { broadcastAddress = it.take(15); formError = null },
                udpPort = udpPort,
                onPortChanged = { udpPort = it.filter(Char::isDigit).take(5); formError = null },
                localNetwork = state.localNetwork,
                localNetworkError = state.localNetworkError,
                formError = formError,
                onSave = {
                    val saved = viewModel.save(draft(), existing)
                    if (saved != null) editing = false else formError = viewModel.state.value.error
                },
                onSend = { validateDraft()?.let { pendingSend = it } },
            )
        } else {
            WakeDeviceList(
                modifier = Modifier.fillMaxSize().padding(padding),
                state = state,
                onAdd = { openEditor() },
                onEdit = ::openEditor,
                onDelete = { pendingDelete = it },
                onWake = { pendingSend = it },
                onRefreshNetwork = viewModel::refreshLocalNetwork,
            )
        }
    }
}

@Composable
private fun WakeDeviceEditor(
    modifier: Modifier,
    mode: WakeOnLanMode,
    onModeChanged: (WakeOnLanMode) -> Unit,
    name: String,
    onNameChanged: (String) -> Unit,
    macAddress: String,
    onMacChanged: (String) -> Unit,
    targetAddress: String,
    onTargetChanged: (String) -> Unit,
    broadcastAddress: String,
    onBroadcastChanged: (String) -> Unit,
    udpPort: String,
    onPortChanged: (String) -> Unit,
    localNetwork: WakeLocalNetwork?,
    localNetworkError: String?,
    formError: String?,
    onSave: () -> Unit,
    onSend: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    WakeSegments(WakeOnLanMode.entries, mode, { it.label }, onModeChanged)
                    OutlinedTextField(
                        value = name, onValueChange = onNameChanged, label = { Text("Friendly device name") },
                        placeholder = { Text("Office PC") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = macAddress, onValueChange = onMacChanged, label = { Text("MAC address") },
                        placeholder = { Text("00:11:22:33:44:55") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = targetAddress, onValueChange = onTargetChanged,
                        label = { Text(if (mode == WakeOnLanMode.LOCAL) "Device IPv4 address" else "Public host or IP address") },
                        placeholder = { Text(if (mode == WakeOnLanMode.LOCAL) "192.168.1.50" else "wol.example.com") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (mode == WakeOnLanMode.LOCAL) {
                        OutlinedTextField(
                            value = broadcastAddress, onValueChange = onBroadcastChanged,
                            label = { Text("Broadcast address") }, placeholder = { Text("192.168.1.255") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            supportingText = { Text(localNetwork?.let { "Active ${it.localAddress}/${it.prefixLength}" }
                                ?: localNetworkError ?: "Active local network unavailable") },
                            trailingIcon = localNetwork?.let { network -> {
                                TextButton(onClick = { onBroadcastChanged(network.broadcastAddress) }) { Text("Use") }
                            } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = udpPort, onValueChange = onPortChanged, label = { Text("UDP port") },
                        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("9", "7").forEach { preset ->
                            OutlinedButton(onClick = { onPortChanged(preset) }, modifier = Modifier.weight(1f)) {
                                Text("Port $preset")
                            }
                        }
                    }
                }
            }
        }
        item {
            WakeNotice(
                if (mode == WakeOnLanMode.LOCAL) {
                    "Local sending is allowed only when the saved device and broadcast match the active Wi-Fi or Ethernet subnet."
                } else {
                    "Remote Wake-on-LAN requires router UDP forwarding or your own compatible relay. One packet is sent to the displayed public destination."
                },
            )
        }
        formError?.let { item { WakeError(it) } }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save device") }
                Button(onClick = onSend, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                    Text("Review & send", Modifier.padding(start = 6.dp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun WakeDeviceList(
    modifier: Modifier,
    state: WakeOnLanUiState,
    onAdd: () -> Unit,
    onEdit: (WakeOnLanDevice) -> Unit,
    onDelete: (WakeOnLanDevice) -> Unit,
    onWake: (WakeOnLanDevice) -> Unit,
    onRefreshNetwork: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { WakeNetworkSummary(state.localNetwork, state.localNetworkError, onRefreshNetwork) }
        state.result?.let { item { WakeSuccess(it) } }
        state.error?.let { item { WakeError(it) } }
        if (state.sending) item { WakeSending(state.activeDevice) }
        if (state.devices.isEmpty()) {
            item { WakeEmptyState(onAdd) }
        } else {
            item { Text("Saved devices", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
            items(state.devices, key = WakeOnLanDevice::id) { device ->
                WakeDeviceCard(device, state.sending, onEdit, onDelete, onWake)
            }
        }
        item {
            WakeNotice("Profiles stay in app-private storage and are excluded from backup and device transfer. " +
                "No scheduled or background packets are sent.")
        }
    }
}

@Composable
private fun WakeNetworkSummary(network: WakeLocalNetwork?, error: String?, onRefresh: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("Local network", style = MaterialTheme.typography.labelMedium)
                Text(network?.let { "${it.localAddress}/${it.prefixLength} · ${it.broadcastAddress}" }
                    ?: error ?: "Unavailable", style = MaterialTheme.typography.bodySmall,
                    fontFamily = if (network != null) FontFamily.Monospace else null,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

@Composable
private fun WakeDeviceCard(
    device: WakeOnLanDevice,
    busy: Boolean,
    onEdit: (WakeOnLanDevice) -> Unit,
    onDelete: (WakeOnLanDevice) -> Unit,
    onWake: (WakeOnLanDevice) -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(
                        if (device.mode == WakeOnLanMode.LOCAL) Icons.Outlined.Router else Icons.Outlined.PowerSettingsNew,
                        contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp),
                    )
                }
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("${device.mode.label} · UDP ${device.udpPort}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onEdit(device) }, enabled = !busy) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Edit ${device.name}")
                }
                IconButton(onClick = { onDelete(device) }, enabled = !busy) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete ${device.name}")
                }
            }
            Text(device.macAddress, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
            Text("${if (device.mode == WakeOnLanMode.LOCAL) "Broadcast" else "Destination"}: ${device.destination}",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onWake(device) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null)
                Text("Wake ${device.name}", Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun WakeEmptyState(onAdd: () -> Unit) {
    Surface(Modifier.fillMaxWidth().height(190.dp), shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No saved devices", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            Button(onClick = onAdd, modifier = Modifier.padding(top = 12.dp)) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Text("Add device", Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun WakeSendConfirmation(device: WakeOnLanDevice, onDismiss: () -> Unit, onSend: () -> Unit) {
    var authorized by rememberSaveable(device.id) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
        title = { Text("Send magic packet?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("MAC  ${device.macAddress}", fontFamily = FontFamily.Monospace)
                        Text("To   ${device.destination}:${device.udpPort}", fontFamily = FontFamily.Monospace)
                        Text("Mode ${device.mode.label}", fontFamily = FontFamily.Monospace)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable { authorized = !authorized }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = authorized, onCheckedChange = { authorized = it })
                    Text("I am authorized to wake this device", Modifier.padding(start = 6.dp))
                }
                Text("One UDP packet will be sent. Delivery does not confirm that the device woke.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onSend, enabled = authorized) { Text("Send packet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun WakePermissionDialog(onContinue: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Allow local-network access") },
        text = { Text("Android requires Nearby devices permission to send the confirmed magic packet " +
            "to this local broadcast address. No background packets are sent.") },
        confirmButton = { Button(onClick = onContinue) { Text("Continue") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun WakeNotice(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WakeSuccess(result: WakeOnLanResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null)
            Column {
                Text("Magic packet sent", fontWeight = FontWeight.Bold)
                Text("${result.destination}:${result.udpPort} · ${result.packetSize} bytes",
                    style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                Text("Packet transmission does not confirm that the device woke.",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WakeSending(device: WakeOnLanDevice?) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            Text("Sending to ${device?.name ?: "device"}", Modifier.padding(start = 10.dp))
        }
    }
}

@Composable
private fun WakeError(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun <T> WakeSegments(values: List<T>, selected: T, label: (T) -> String, onSelected: (T) -> Unit) {
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

private fun shareWakeResult(context: Context, result: WakeOnLanResult) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Wake-on-LAN")
                putExtra(Intent.EXTRA_TEXT, result.asPlainText())
            },
            "Share Wake-on-LAN result",
        ),
    )
}
