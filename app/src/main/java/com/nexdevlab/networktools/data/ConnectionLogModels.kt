package com.nexdevlab.networktools.data

enum class ConnectionKind(val label: String) {
    WIFI("Wi-Fi"),
    MOBILE("Mobile"),
    ETHERNET("Ethernet"),
    VPN("VPN"),
    OTHER("Other network"),
    OFFLINE("Offline"),
}

enum class ConnectionEventType(val label: String) {
    CONNECTED("Connected"),
    DISCONNECTED("Disconnected"),
    SWITCHED("Network switched"),
    UPDATED("Connection changed"),
}

data class ConnectionStateSnapshot(
    val kind: ConnectionKind,
    val metered: Boolean = false,
    val validated: Boolean = false,
) {
    val status: String get() = buildList {
        add(kind.label)
        if (kind != ConnectionKind.OFFLINE) {
            add(if (validated) "Internet available" else "Internet not validated")
            add(if (metered) "Metered" else "Unmetered")
        }
    }.joinToString(" · ")
}

data class ConnectionLogEntry(
    val id: Long,
    val event: ConnectionEventType,
    val previous: ConnectionStateSnapshot?,
    val current: ConnectionStateSnapshot,
    val timestampMillis: Long,
) {
    val title: String get() = when (event) {
        ConnectionEventType.CONNECTED -> "Connected to ${current.kind.label}"
        ConnectionEventType.DISCONNECTED -> "${previous?.kind?.label ?: "Network"} disconnected"
        ConnectionEventType.SWITCHED -> "${previous?.kind?.label} to ${current.kind.label}"
        ConnectionEventType.UPDATED -> "${current.kind.label} state changed"
    }

    fun asPlainText(): String = buildString {
        append(event.label).append(": ").append(title)
        append("\nCurrent: ").append(current.status)
        previous?.let { append("\nPrevious: ").append(it.status) }
    }

    companion object {
        fun transition(
            previous: ConnectionStateSnapshot?,
            current: ConnectionStateSnapshot,
            timestampMillis: Long,
            id: Long = timestampMillis,
        ): ConnectionLogEntry? {
            if (previous == current) return null
            val event = when {
                previous == null && current.kind == ConnectionKind.OFFLINE -> ConnectionEventType.DISCONNECTED
                previous == null || previous.kind == ConnectionKind.OFFLINE -> ConnectionEventType.CONNECTED
                current.kind == ConnectionKind.OFFLINE -> ConnectionEventType.DISCONNECTED
                previous.kind != current.kind -> ConnectionEventType.SWITCHED
                else -> ConnectionEventType.UPDATED
            }
            return ConnectionLogEntry(id, event, previous, current, timestampMillis)
        }
    }
}

enum class ConnectionLogFilter(val label: String) {
    ALL("All"),
    WIFI("Wi-Fi"),
    MOBILE("Mobile"),
}

data class ConnectionLogUiState(
    val monitoring: Boolean = false,
    val current: ConnectionStateSnapshot? = null,
    val entries: List<ConnectionLogEntry> = emptyList(),
    val error: String? = null,
) {
    fun filtered(filter: ConnectionLogFilter): List<ConnectionLogEntry> = when (filter) {
        ConnectionLogFilter.ALL -> entries
        ConnectionLogFilter.WIFI -> entries.filter {
            it.current.kind == ConnectionKind.WIFI || it.previous?.kind == ConnectionKind.WIFI
        }
        ConnectionLogFilter.MOBILE -> entries.filter {
            it.current.kind == ConnectionKind.MOBILE || it.previous?.kind == ConnectionKind.MOBILE
        }
    }
}

fun List<ConnectionLogEntry>.asConnectionLogText(): String = buildList {
    add("Connections Log")
    this@asConnectionLogText.forEach { entry ->
        add("${entry.timestampMillis}: ${entry.event.label} - ${entry.title}")
        add("  ${entry.current.status}")
    }
}.joinToString("\n")
