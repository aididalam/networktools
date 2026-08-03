package com.nexdevlab.networktools.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Cable
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Router
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolCategory(
    val number: String,
    val route: String,
    val title: String,
    val icon: ImageVector,
    val accent: Color,
    val available: Boolean = false,
)

val ToolCategories = listOf(
    ToolCategory("01", "ip-information", "IP Information", Icons.Outlined.Info, Color(0xFF1B8A78), true),
    ToolCategory("02", "whois", "WHOIS Lookup", Icons.Outlined.Public, Color(0xFF5378B7), true),
    ToolCategory("03", "ping", "Ping", Icons.Outlined.CellTower, Color(0xFFC0733A), true),
    ToolCategory("04", "traceroute", "Traceroute", Icons.Outlined.Route, Color(0xFF7B67B3), true),
    ToolCategory("05", "wifi-analyzer", "Wi-Fi Analyzer", Icons.Outlined.Radar, Color(0xFFC24D67), true),
    ToolCategory("06", "lan-scanner", "LAN Scanner", Icons.Outlined.Lan, Color(0xFF348096), true),
    ToolCategory("07", "port-scanner", "Port Scanner", Icons.Outlined.Search, Color(0xFF6F7C38), true),
    ToolCategory("08", "dns-lookup", "DNS Lookup", Icons.Outlined.Dns, Color(0xFF4069A3), true),
    ToolCategory("09", "ip-calculator", "IP Calculator", Icons.Outlined.Calculate, Color(0xFFA65D36), true),
    ToolCategory("10", "converter", "IP & Host Converter", Icons.AutoMirrored.Outlined.CompareArrows, Color(0xFF71599B)),
    ToolCategory("11", "ip-finder", "IP Finder", Icons.Outlined.Cable, Color(0xFF2C8273)),
    ToolCategory("12", "router-setup", "Router Setup", Icons.Outlined.Router, Color(0xFFAA6C18)),
    ToolCategory("13", "wake-on-lan", "Wake-on-LAN", Icons.Outlined.PowerSettingsNew, Color(0xFFB64D4D)),
    ToolCategory("14", "connections-log", "Connections Log", Icons.Outlined.History, Color(0xFF4F739E)),
    ToolCategory("15", "additional-tools", "Additional Tools", Icons.Outlined.Terminal, Color(0xFF64746D)),
    ToolCategory("16", "general", "General Features", Icons.Outlined.Hub, Color(0xFF805C87)),
)
