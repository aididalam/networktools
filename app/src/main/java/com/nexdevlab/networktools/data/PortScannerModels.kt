package com.nexdevlab.networktools.data

import java.net.InetAddress

enum class PortSelectionMode(val label: String) {
    SINGLE("Single"),
    RANGE("Range"),
    CUSTOM("Custom"),
}

enum class PortScanStatus(val label: String) {
    OPEN("Open"),
    CLOSED("Closed"),
    TIMED_OUT("Timed out"),
    UNREACHABLE("Unreachable"),
    ERROR("Error"),
}

data class PortScanConfig(
    val target: String,
    val selectionMode: PortSelectionMode,
    val singlePort: String = DefaultSinglePort.toString(),
    val rangeStart: String = DefaultRangeStart.toString(),
    val rangeEnd: String = DefaultRangeEnd.toString(),
    val customPorts: String = CommonPorts.joinToString(","),
    val timeoutMillis: Int = DefaultTimeoutMillis,
    val authorized: Boolean,
) {
    fun validate(): List<Int> {
        PingRepository.normalizeTarget(target)
        require(timeoutMillis in TimeoutRange) { "Timeout must be ${TimeoutRange.first}-${TimeoutRange.last} ms" }
        require(authorized) { "Confirm that you are authorized to scan this host" }
        return ports()
    }

    fun ports(): List<Int> = when (selectionMode) {
        PortSelectionMode.SINGLE -> listOf(parsePort(singlePort, "Enter a port from 1-65535"))
        PortSelectionMode.RANGE -> {
            val start = parsePort(rangeStart, "Enter a valid starting port")
            val end = parsePort(rangeEnd, "Enter a valid ending port")
            require(start <= end) { "Starting port must not exceed ending port" }
            require(end - start + 1 <= MaximumPorts) { "A scan can contain at most $MaximumPorts ports" }
            (start..end).toList()
        }
        PortSelectionMode.CUSTOM -> parseCustomPorts(customPorts)
    }

    companion object {
        val TimeoutRange = 250..2000
        val CommonPorts = listOf(
            20, 21, 22, 23, 25, 53, 67, 68, 80, 110, 123, 137, 138, 139, 143,
            161, 389, 443, 445, 465, 514, 587, 631, 993, 995, 1433, 1883, 2049,
            3306, 3389, 5000, 5353, 5432, 5900, 8000, 8080, 8443, 8883, 9100,
        )
        const val MaximumPorts = 512
        const val DefaultSinglePort = 443
        const val DefaultRangeStart = 1
        const val DefaultRangeEnd = 100
        const val DefaultTimeoutMillis = 500
        private const val MaximumCustomInputLength = 2_048

        private fun parsePort(value: String, message: String): Int =
            value.trim().toIntOrNull()?.takeIf { it in 1..65535 }
                ?: throw IllegalArgumentException(message)

        internal fun parseCustomPorts(value: String): List<Int> {
            val trimmed = value.trim()
            require(trimmed.isNotEmpty()) { "Enter at least one port" }
            require(trimmed.length <= MaximumCustomInputLength) { "Custom port list is too long" }
            require(trimmed.all { it.isDigit() || it == ',' || it.isWhitespace() }) {
                "Separate ports with commas"
            }
            val tokens = trimmed.split(',')
            require(tokens.none { it.isBlank() }) { "Enter a valid comma-separated port list" }
            require(tokens.size <= MaximumPorts) { "A scan can contain at most $MaximumPorts ports" }
            val ports = tokens.map { parsePort(it, "Every port must be from 1-65535") }
            require(ports.distinct().size == ports.size) { "Remove duplicate ports" }
            return ports.sorted()
        }
    }
}

data class PreparedPortScan(
    val config: PortScanConfig,
    val normalizedTarget: String,
    val resolvedAddress: String,
    val address: InetAddress,
    val ports: List<Int>,
    val localNetworkTarget: Boolean,
)

data class PortScanEntry(
    val port: Int,
    val status: PortScanStatus,
    val service: String,
    val latencyMillis: Double? = null,
)

data class PortScanResult(
    val target: String,
    val resolvedAddress: String,
    val entries: List<PortScanEntry>,
    val timeoutMillis: Int,
    val stopped: Boolean,
    val completedAtMillis: Long,
) {
    val openCount: Int get() = entries.count { it.status == PortScanStatus.OPEN }
    val closedCount: Int get() = entries.count { it.status == PortScanStatus.CLOSED }
    val timedOutCount: Int get() = entries.count { it.status == PortScanStatus.TIMED_OUT }

    fun asPlainText(): String = buildList {
        add("Port scan result")
        add("Target: $target ($resolvedAddress)")
        add("Status: ${if (stopped) "Stopped" else "Completed"}")
        add("Scanned: ${entries.size}")
        add("Open: $openCount")
        add("Closed: $closedCount")
        add("Timed out: $timedOutCount")
        entries.forEach { entry ->
            add("${entry.port}/tcp ${entry.status.label} ${entry.service}" +
                entry.latencyMillis?.let { " ${formatLatency(it)} ms" }.orEmpty())
        }
    }.joinToString("\n")
}

data class PortScanHistoryEntry(
    val id: Long,
    val target: String,
    val resolvedAddress: String,
    val scannedCount: Int,
    val openPorts: List<PortScanEntry>,
    val closedCount: Int,
    val timedOutCount: Int,
    val stopped: Boolean,
    val completedAtMillis: Long,
)

enum class PortScannerView(val label: String) {
    SCANNER("Scanner"),
    HISTORY("History"),
}

enum class PortResultFilter(val label: String) {
    OPEN("Open"),
    ALL("All"),
}

data class PortScannerUiState(
    val preparing: Boolean = false,
    val running: Boolean = false,
    val permissionRequired: Boolean = false,
    val activeConfig: PortScanConfig? = null,
    val normalizedTarget: String? = null,
    val resolvedAddress: String? = null,
    val entries: List<PortScanEntry> = emptyList(),
    val totalPorts: Int = 0,
    val result: PortScanResult? = null,
    val history: List<PortScanHistoryEntry> = emptyList(),
    val error: String? = null,
)
