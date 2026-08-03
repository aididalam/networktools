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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.nexdevlab.networktools.data.IpAddressFamily
import com.nexdevlab.networktools.data.IpCalculationInput
import com.nexdevlab.networktools.data.IpCalculationResult
import com.nexdevlab.networktools.data.IpCalculator
import com.nexdevlab.networktools.data.IpPrefixInputMode
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IpCalculatorScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var family by rememberSaveable { mutableStateOf(IpAddressFamily.IPV4) }
    var address by rememberSaveable { mutableStateOf("") }
    var inputMode by rememberSaveable { mutableStateOf(IpPrefixInputMode.PREFIX) }
    var prefix by rememberSaveable { mutableStateOf(IpAddressFamily.IPV4.defaultPrefix.toString()) }
    var subnetMask by rememberSaveable { mutableStateOf("255.255.255.0") }
    var membershipAddress by rememberSaveable { mutableStateOf("") }
    var calculated by rememberSaveable { mutableStateOf(false) }

    val calculation = remember(family, address, inputMode, prefix, subnetMask, membershipAddress, calculated) {
        if (!calculated) null else runCatching {
            IpCalculator.calculate(
                IpCalculationInput(family, address, inputMode, prefix, subnetMask, membershipAddress),
            )
        }
    }
    val result = calculation?.getOrNull()
    val error = calculation?.exceptionOrNull()?.message
    fun invalidate() { calculated = false }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("IP Calculator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = result != null,
                        onClick = {
                            result?.let { value ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("IP calculation", value.asPlainText()))
                                scope.launch { snackbar.showSnackbar("IP calculation copied") }
                            }
                        },
                    ) { Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy IP calculation") }
                    IconButton(
                        enabled = result != null,
                        onClick = { result?.let { shareIpCalculation(context, it) } },
                    ) { Icon(Icons.Outlined.Share, contentDescription = "Share IP calculation") }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Calculate an IPv4 or IPv6 network locally from an address and prefix. " +
                            "For IPv4, a contiguous subnet mask can be used instead of a prefix.",
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
                IpCalculatorInputPanel(
                    family = family,
                    onFamilyChanged = { selected ->
                        family = selected
                        prefix = selected.defaultPrefix.toString()
                        inputMode = IpPrefixInputMode.PREFIX
                        invalidate()
                    },
                    address = address,
                    onAddressChanged = { address = it.take(128); invalidate() },
                    inputMode = inputMode,
                    onInputModeChanged = { inputMode = it; invalidate() },
                    prefix = prefix,
                    onPrefixChanged = { prefix = it.filter(Char::isDigit).take(3); invalidate() },
                    onStepPrefix = { delta ->
                        val current = prefix.toIntOrNull() ?: family.defaultPrefix
                        prefix = (current + delta).coerceIn(0, family.bitCount).toString()
                        invalidate()
                    },
                    subnetMask = subnetMask,
                    onSubnetMaskChanged = { subnetMask = it.take(15); invalidate() },
                    membershipAddress = membershipAddress,
                    onMembershipChanged = { membershipAddress = it.take(128); invalidate() },
                    onCalculate = { calculated = true },
                )
            }
            error?.let { item { IpCalculatorError(it) } }
            if (result != null) {
                item { IpCalculatorSummary(result) }
                item { IpResultSectionTitle("Network") }
                item { IpResultRow("CIDR", result.cidr) }
                item { IpResultRow("Network address", result.networkAddress) }
                item { IpResultRow("Subnet mask", result.subnetMask) }
                result.broadcastAddress?.let { item { IpResultRow("Broadcast address", it) } }
                item { IpResultRow("Prefix length", "/${result.prefixLength}") }
                item { IpResultSectionTitle("Host range") }
                item { IpResultRow("First host", result.firstHost) }
                item { IpResultRow("Last host", result.lastHost) }
                item { IpResultRow("Range", result.hostRange) }
                item { IpResultSectionTitle("Capacity") }
                item { IpResultRow("Total addresses", result.totalAddresses.toString()) }
                item { IpResultRow("Usable hosts", result.usableHosts.toString()) }
                result.membershipAddress?.let { candidate ->
                    item { IpMembershipResult(candidate, result.cidr, result.membershipMatches == true) }
                }
            } else if (error == null) {
                item { IpCalculatorEmptyState() }
            }
        }
    }
}

@Composable
private fun IpCalculatorInputPanel(
    family: IpAddressFamily,
    onFamilyChanged: (IpAddressFamily) -> Unit,
    address: String,
    onAddressChanged: (String) -> Unit,
    inputMode: IpPrefixInputMode,
    onInputModeChanged: (IpPrefixInputMode) -> Unit,
    prefix: String,
    onPrefixChanged: (String) -> Unit,
    onStepPrefix: (Int) -> Unit,
    subnetMask: String,
    onSubnetMaskChanged: (String) -> Unit,
    membershipAddress: String,
    onMembershipChanged: (String) -> Unit,
    onCalculate: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SegmentedPicker(IpAddressFamily.entries, family, { it.label }, onFamilyChanged)
            OutlinedTextField(
                value = address,
                onValueChange = onAddressChanged,
                label = { Text("${family.label} address") },
                placeholder = { Text(if (family == IpAddressFamily.IPV4) "192.168.1.10" else "2001:db8::1") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (family == IpAddressFamily.IPV4) {
                SegmentedPicker(IpPrefixInputMode.entries, inputMode, { it.label }, onInputModeChanged)
            }
            if (family == IpAddressFamily.IPV4 && inputMode == IpPrefixInputMode.SUBNET_MASK) {
                OutlinedTextField(
                    value = subnetMask,
                    onValueChange = onSubnetMaskChanged,
                    label = { Text("Subnet mask") },
                    placeholder = { Text("255.255.255.0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { onStepPrefix(-1) }) {
                        Icon(Icons.Outlined.Remove, contentDescription = "Decrease prefix length")
                    }
                    OutlinedTextField(
                        value = prefix,
                        onValueChange = onPrefixChanged,
                        label = { Text("Prefix length") },
                        prefix = { Text("/") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onStepPrefix(1) }) {
                        Icon(Icons.Outlined.Add, contentDescription = "Increase prefix length")
                    }
                }
            }
            OutlinedTextField(
                value = membershipAddress,
                onValueChange = onMembershipChanged,
                label = { Text("Address to check (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Calculate, contentDescription = null)
                Text("Calculate subnet", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun <T> SegmentedPicker(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        values.forEach { value ->
            Surface(
                modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(value) },
                shape = RoundedCornerShape(8.dp),
                color = if (value == selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant,
            ) { Box(contentAlignment = Alignment.Center) { Text(label(value), fontWeight = FontWeight.Medium) } }
        }
    }
}

@Composable
private fun IpCalculatorSummary(result: IpCalculationResult) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.cidr, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${result.family.label} • ${result.totalAddresses} addresses",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IpResultSectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
}

@Composable
private fun IpResultRow(label: String, value: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun IpMembershipResult(candidate: String, cidr: String, matches: Boolean) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (matches) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (matches) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = null)
            Column {
                Text(if (matches) "Address is inside subnet" else "Address is outside subnet", fontWeight = FontWeight.Medium)
                Text("$candidate • $cidr", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun IpCalculatorError(message: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null)
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun IpCalculatorEmptyState() {
    Surface(Modifier.fillMaxWidth().height(118.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Calculate, contentDescription = null, modifier = Modifier.size(34.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text("No subnet calculation", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun shareIpCalculation(context: Context, result: IpCalculationResult) {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "IP calculation")
                putExtra(Intent.EXTRA_TEXT, result.asPlainText())
            },
            "Share IP calculation",
        ),
    )
}
