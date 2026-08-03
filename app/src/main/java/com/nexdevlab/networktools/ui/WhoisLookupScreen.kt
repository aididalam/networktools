package com.nexdevlab.networktools.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.nexdevlab.networktools.data.WhoisQueryType
import com.nexdevlab.networktools.data.WhoisRepository
import com.nexdevlab.networktools.data.WhoisResult
import com.nexdevlab.networktools.data.WhoisServerMode
import com.nexdevlab.networktools.data.WhoisViewModel
import com.nexdevlab.networktools.ui.theme.ThemeChoice
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhoisLookupScreen(
    onBack: () -> Unit,
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    viewModel: WhoisViewModel = viewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var queryType by rememberSaveable { mutableStateOf(WhoisQueryType.DOMAIN) }
    var serverMode by rememberSaveable { mutableStateOf(WhoisServerMode.AUTOMATIC) }
    var query by rememberSaveable { mutableStateOf("") }
    var manualServer by rememberSaveable { mutableStateOf("") }
    var queryError by rememberSaveable { mutableStateOf<String?>(null) }
    var serverError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submitLookup() {
        queryError = runCatching {
            when (queryType) {
                WhoisQueryType.DOMAIN -> WhoisRepository.normalizeDomain(query)
                WhoisQueryType.IP_ADDRESS -> WhoisRepository.normalizeIpAddress(query)
            }
        }.exceptionOrNull()?.message
        serverError = if (serverMode == WhoisServerMode.MANUAL) {
            runCatching { WhoisRepository.normalizeWhoisServer(manualServer) }
                .exceptionOrNull()?.message
        } else {
            null
        }
        if (queryError == null && serverError == null) {
            focusManager.clearFocus()
            viewModel.lookup(query, queryType, serverMode, manualServer)
        }
    }

    DisposableEffect(viewModel) {
        onDispose(viewModel::cancelLookup)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("WHOIS Lookup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        enabled = state.result != null,
                        onClick = {
                            state.result?.let {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("WHOIS result", it.asPlainText()))
                                scope.launch { snackbar.showSnackbar("WHOIS result copied") }
                            }
                        },
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy WHOIS result")
                    }
                    IconButton(
                        enabled = state.result != null,
                        onClick = { state.result?.let { shareWhoisResult(context, it) } },
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share WHOIS result")
                    }
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Look up one domain or IP address at a time. Automatic mode follows " +
                            "the IANA referral; manual mode connects only to the public WHOIS server you enter.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LookupForm(
                    queryType = queryType,
                    onQueryTypeChange = {
                        queryType = it
                        queryError = null
                    },
                    serverMode = serverMode,
                    onServerModeChange = {
                        serverMode = it
                        serverError = null
                    },
                    query = query,
                    onQueryChange = {
                        query = it.take(253)
                        queryError = null
                    },
                    queryError = queryError,
                    manualServer = manualServer,
                    onManualServerChange = {
                        manualServer = it.take(253)
                        serverError = null
                    },
                    serverError = serverError,
                    loading = state.loading,
                    onLookup = ::submitLookup,
                    onCancel = viewModel::cancelLookup,
                )
            }
            state.error?.let { error -> item { WhoisErrorPanel(error) } }
            state.result?.let { result -> item { WhoisResults(result) } }
        }
    }
}

@Composable
private fun LookupForm(
    queryType: WhoisQueryType,
    onQueryTypeChange: (WhoisQueryType) -> Unit,
    serverMode: WhoisServerMode,
    onServerModeChange: (WhoisServerMode) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    queryError: String?,
    manualServer: String,
    onManualServerChange: (String) -> Unit,
    serverError: String?,
    loading: Boolean,
    onLookup: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Query type", style = MaterialTheme.typography.labelLarge)
            SegmentedPicker(
                choices = WhoisQueryType.entries.toList(),
                selected = queryType,
                label = WhoisQueryType::label,
                onSelected = onQueryTypeChange,
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(if (queryType == WhoisQueryType.DOMAIN) "Domain name" else "IP address") },
                placeholder = {
                    Text(if (queryType == WhoisQueryType.DOMAIN) "example.com" else "8.8.8.8")
                },
                singleLine = true,
                isError = queryError != null,
                supportingText = queryError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (queryType == WhoisQueryType.DOMAIN) KeyboardType.Uri else KeyboardType.Ascii,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { if (!loading) onLookup() }),
            )
            Text("WHOIS server", style = MaterialTheme.typography.labelLarge)
            SegmentedPicker(
                choices = WhoisServerMode.entries.toList(),
                selected = serverMode,
                label = WhoisServerMode::label,
                onSelected = onServerModeChange,
            )
            if (serverMode == WhoisServerMode.MANUAL) {
                OutlinedTextField(
                    value = manualServer,
                    onValueChange = onManualServerChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Server hostname") },
                    placeholder = { Text("whois.example.net") },
                    supportingText = serverError?.let { message -> { Text(message) } },
                    isError = serverError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { if (!loading) onLookup() }),
                )
            }
            if (loading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    TextButton(onClick = onCancel) { Text("Cancel") }
                }
            } else {
                Button(
                    onClick = onLookup,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text("Lookup", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun <T> SegmentedPicker(
    choices: List<T>,
    selected: T,
    label: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { choice ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clickable { onSelected(choice) },
                shape = RoundedCornerShape(8.dp),
                color = if (selected == choice) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                contentColor = if (selected == choice) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(label(choice), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WhoisErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(message, modifier = Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun WhoisResults(result: WhoisResult) {
    var rawExpanded by rememberSaveable(result.query, result.rawResponse) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(result.query, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
                Text(
                    result.servers.joinToString(" -> "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        WhoisResultSection("Registration") {
            WhoisResultRow("Registrar", result.registrar)
            WhoisResultRow("Registered", result.registrationDate)
            WhoisResultRow("Expires", result.expirationDate)
            WhoisResultRow("Name servers", result.nameServers.joinToString("\n").ifBlank { null })
            WhoisResultRow("Registrant", result.registrant)
            WhoisResultRow("Organization", result.organization, last = true)
        }
        WhoisResultSection("Network ownership") {
            WhoisResultRow("IP block owner", result.ipBlockOwner)
            WhoisResultRow("Autonomous system", result.autonomousSystem, last = true)
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { rawExpanded = !rawExpanded }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Raw response", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Icon(
                        if (rawExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = if (rawExpanded) "Collapse raw response" else "Expand raw response",
                    )
                }
                if (rawExpanded) {
                    HorizontalDivider()
                    Text(
                        text = result.rawResponse.take(MaxDisplayedRawCharacters),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    if (result.rawResponse.length > MaxDisplayedRawCharacters) {
                        Text(
                            "Response shortened for display",
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhoisResultSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 2.dp, bottom = 7.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun WhoisResultRow(label: String, value: String?, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.4f),
            )
            Text(
                value ?: "Unavailable",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(0.6f),
            )
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(start = 14.dp))
    }
}

private fun shareWhoisResult(context: Context, result: WhoisResult) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "WHOIS result for ${result.query}")
        putExtra(Intent.EXTRA_TEXT, result.asPlainText())
    }
    context.startActivity(Intent.createChooser(intent, "Share WHOIS result"))
}

private const val MaxDisplayedRawCharacters = 50_000
