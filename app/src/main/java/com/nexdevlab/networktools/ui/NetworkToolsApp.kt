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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nexdevlab.networktools.ui.theme.NetworkToolsTheme
import com.nexdevlab.networktools.ui.theme.ThemeChoice

private const val PreferencesName = "network_tools_preferences"
private const val ThemeKey = "theme_choice"

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
        BackHandler(enabled = route != "home") { route = "home" }
        key(route) {
            when (route) {
                "ip-information" -> IpInformationScreen(onBack = { route = "home" })
                else -> HomeScreen(
                    themeChoice = themeChoice,
                    onThemeChoice = {
                        themeChoice = it
                        preferences.edit().putString(ThemeKey, it.name).apply()
                    },
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
    var themeDialogOpen by rememberSaveable { mutableStateOf(false) }
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
                    IconButton(onClick = { themeDialogOpen = true }) {
                        Icon(Icons.Outlined.DarkMode, contentDescription = "Choose theme")
                    }
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                top = 12.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                IpOverviewBand(onClick = { onOpenTool("ip-information") })
            }
            item {
                Text(
                    text = "Tools",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 18.dp, bottom = 4.dp),
                )
            }
            items(ToolCategories, key = { it.number }) { tool ->
                ToolRow(tool = tool, onClick = { onOpenTool(tool.route) })
            }
        }
    }

    if (themeDialogOpen) {
        ThemeChoiceDialog(
            selected = themeChoice,
            onSelected = onThemeChoice,
            onDismiss = { themeDialogOpen = false },
        )
    }
}

@Composable
private fun IpOverviewBand(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.NetworkCheck, contentDescription = null)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text("IP Information", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Connection and address details",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun ToolRow(tool: ToolCategory, onClick: () -> Unit) {
    val modifier = if (tool.available) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier.alpha(0.62f)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .then(modifier),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = tool.accent.copy(alpha = 0.14f),
                contentColor = tool.accent,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(tool.icon, contentDescription = null, modifier = Modifier.size(22.dp))
                }
            }
            Text(
                text = tool.number,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp).width(28.dp),
            )
            Text(
                text = tool.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (tool.available) Icons.Outlined.ChevronRight else Icons.Outlined.Lock,
                contentDescription = if (tool.available) null else "Not available yet",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
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
