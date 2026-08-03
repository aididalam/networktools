package com.nexdevlab.networktools.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexdevlab.networktools.ui.theme.NetworkToolsTheme
import com.nexdevlab.networktools.ui.theme.ThemeChoice

private const val PreferencesName = "network_tools_preferences"
private const val ThemeKey = "theme_choice"
internal const val PrivacyPolicyUrl = ""

@Composable
fun NetworkToolsApp() {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    }
    var route by rememberSaveable { mutableStateOf("home") }
    var themeChoice by rememberSaveable {
        mutableStateOf(
            preferences.getString(ThemeKey, null)
                ?.let { stored -> ThemeChoice.entries.firstOrNull { it.name == stored } }
                ?: ThemeChoice.SYSTEM,
        )
    }

    NetworkToolsTheme(themeChoice) {
        val updateThemeChoice: (ThemeChoice) -> Unit = {
            themeChoice = it
            preferences.edit().putString(ThemeKey, it.name).apply()
        }
        BackHandler(enabled = route != "home") { route = "home" }
        key(route) {
            when (route) {
                "ip-information" -> IpInformationScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "whois" -> WhoisLookupScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "ping" -> PingScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "traceroute" -> TracerouteScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "wifi-analyzer" -> WifiAnalyzerScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "lan-scanner" -> LanScannerScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "port-scanner" -> PortScannerScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                "dns-lookup" -> DnsLookupScreen(
                    onBack = { route = "home" },
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                )
                else -> HomeScreen(
                    themeChoice = themeChoice,
                    onThemeChoice = updateThemeChoice,
                    onOpenTool = { route = it },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    onOpenTool: (String) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("NetworkTools", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Network diagnostics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    AppOverflowMenu(
                        themeChoice = themeChoice,
                        onThemeChoice = onThemeChoice,
                        helpText = "Open an available tool to view current network information. " +
                            "Run diagnostics only against networks and systems you own or are authorized to test.",
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(ToolCategories, key = { it.number }) { tool ->
                ToolCard(tool = tool, onClick = { onOpenTool(tool.route) })
            }
        }
    }
}

@Composable
private fun ToolCard(tool: ToolCategory, onClick: () -> Unit) {
    val modifier = if (tool.available) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(136.dp)
            .then(modifier),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(28.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            ) {
                Surface(
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    color = tool.accent.copy(alpha = 0.14f),
                    contentColor = tool.accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(tool.icon, contentDescription = null, modifier = Modifier.size(25.dp))
                    }
                }
                Text(
                    text = tool.title,
                    color = if (tool.available) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 18.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (tool.available) Icons.Outlined.ChevronRight else Icons.Outlined.Lock,
                    contentDescription = if (tool.available) null else "Not available yet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
internal fun AppOverflowMenu(
    themeChoice: ThemeChoice,
    onThemeChoice: (ThemeChoice) -> Unit,
    helpText: String,
) {
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    var themeDialogOpen by rememberSaveable { mutableStateOf(false) }
    var helpDialogOpen by rememberSaveable { mutableStateOf(false) }

    Box {
        IconButton(onClick = { menuOpen = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = "More options")
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = { Text("Theme") },
                leadingIcon = { Icon(Icons.Outlined.Palette, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    themeDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text("Help") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    helpDialogOpen = true
                },
            )
            DropdownMenuItem(
                text = { Text("Privacy policy") },
                leadingIcon = { Icon(Icons.Outlined.PrivacyTip, contentDescription = null) },
                enabled = PrivacyPolicyUrl.isNotBlank(),
                onClick = {
                    menuOpen = false
                    uriHandler.openUri(PrivacyPolicyUrl)
                },
            )
        }
    }

    if (themeDialogOpen) {
        ThemeChoiceDialog(
            selected = themeChoice,
            onSelected = onThemeChoice,
            onDismiss = { themeDialogOpen = false },
        )
    }
    if (helpDialogOpen) {
        AlertDialog(
            onDismissRequest = { helpDialogOpen = false },
            title = { Text("Help") },
            text = { Text(helpText) },
            confirmButton = {
                TextButton(onClick = { helpDialogOpen = false }) { Text("Close") }
            },
        )
    }
}

@Composable
private fun ThemeChoiceDialog(
    selected: ThemeChoice,
    onSelected: (ThemeChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeChoice.entries.forEachIndexed { index, choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { role = Role.RadioButton }
                            .clickable {
                                onSelected(choice)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selected == choice,
                            onClick = null,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(choice.label, style = MaterialTheme.typography.bodyLarge)
                    }
                    if (index != ThemeChoice.entries.lastIndex) HorizontalDivider()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
